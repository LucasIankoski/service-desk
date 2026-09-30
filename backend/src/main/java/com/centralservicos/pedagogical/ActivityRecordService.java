package com.centralservicos.pedagogical;

import com.centralservicos.attachments.*;
import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.audit.AuditService;
import com.centralservicos.settings.SettingsService;
import com.centralservicos.shared.DomainException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.*;
import java.util.*;
import static com.centralservicos.pedagogical.ActivityRecordViews.*;

@Service
class ActivityRecordService {
    private final ActivityRecordRepository records;
    private final PedagogicalService access;
    private final ActivityImageProcessor images;
    private final AttachmentStorage storage;
    private final PhotoCleanupService cleanup;
    private final SettingsService settings;
    private final AuditService audit;
    ActivityRecordService(ActivityRecordRepository records, PedagogicalService access, ActivityImageProcessor images,
                          AttachmentStorage storage, PhotoCleanupService cleanup, SettingsService settings, AuditService audit) {
        this.records = records; this.access = access; this.images = images; this.storage = storage;
        this.cleanup = cleanup; this.settings = settings; this.audit = audit;
    }
    @Transactional(readOnly = true)
    public ActivityRecordViews.Page list(UUID classId, Integer year, Integer month, int page, AuthenticatedUser actor) {
        access.classroom(classId, actor, false);
        if (page < 0 || page > 100000 || year != null && (year < 1900 || year > 9998)
                || month != null && (year == null || month < 1 || month > 12))
            throw DomainException.unprocessable("Período ou página inválidos.");
        var start = LocalDate.of(year == null ? 1900 : year, month == null ? 1 : month, 1);
        var end = year == null ? LocalDate.of(9999, 1, 1) : month == null ? start.plusYears(1) : start.plusMonths(1);
        var result = records.findByClassIdAndActivityDateGreaterThanEqualAndActivityDateLessThan(classId, start, end,
                PageRequest.of(page, 24, Sort.by(Sort.Direction.DESC, "activityDate", "createdAt", "id")));
        return new ActivityRecordViews.Page(result.stream().map(r -> new Summary(r.id, r.classId.toString(), r.title,
                r.activityDate, r.authorName, r.photos.size(), photo(r, r.photos.getFirst()), r.rowVersion)).toList(),
                page, result.getTotalPages(), result.getTotalElements(), settings.ticketPolicy().attachmentLimitMb());
    }
    @Transactional(readOnly = true)
    public View get(UUID id, AuthenticatedUser actor) {
        var record = required(id);
        return view(record, access.classroom(record.classId, actor, false), actor);
    }
    @Transactional
    public View create(UUID classId, Input input, List<MultipartFile> files, AuthenticatedUser actor) {
        var classroom = access.classroom(classId, actor, true);
        writable(classroom);
        var record = new ActivityRecord(); record.classId = classId;
        record.authorId = actor.id(); record.authorName = actor.displayName();
        if (!input.retainedPhotoIds().isEmpty()) throw DomainException.unprocessable("Fotos existentes não pertencem a um novo registro.");
        change(record, input, files);
        records.saveAndFlush(record);
        audit.record(actor.id(), "PED_RECORD_CREATED", "ActivityRecord", record.id, null);
        return view(record, classroom, actor);
    }
    @Transactional
    public View update(UUID id, Input input, List<MultipartFile> files, AuthenticatedUser actor) {
        var record = required(id); var classroom = access.classroom(record.classId, actor, true);
        authorizeEdit(record, classroom, input.version(), actor);
        change(record, input, files); records.flush();
        audit.record(actor.id(), "PED_RECORD_UPDATED", "ActivityRecord", id, null);
        return view(record, classroom, actor);
    }
    @Transactional
    public void delete(UUID id, long version, AuthenticatedUser actor) {
        var record = required(id); var classroom = access.classroom(record.classId, actor, true);
        authorizeEdit(record, classroom, version, actor);
        record.photos.forEach(cleanup::removed);
        records.delete(record); records.flush();
        audit.record(actor.id(), "PED_RECORD_DELETED", "ActivityRecord", id, null);
    }
    @Transactional(readOnly = true)
    public StoredResource image(UUID id, UUID photoId, boolean thumbnail, AuthenticatedUser actor) {
        var record = required(id); access.classroom(record.classId, actor, false);
        var photo = record.photos.stream().filter(p -> p.id.equals(photoId)).findFirst()
                .orElseThrow(() -> DomainException.notFound("Foto não encontrada."));
        try {
            return storage.load(thumbnail ? photo.thumbnailKey : photo.originalKey,
                    thumbnail ? "image/jpeg" : photo.mediaType, "foto-" + photo.id + (thumbnail ? ".jpg" : extension(photo.mediaType)));
        } catch (IOException exception) { throw DomainException.notFound("Não foi possível carregar a foto."); }
    }
    private void change(ActivityRecord record, Input input, List<MultipartFile> files) {
        if (input.title() == null || input.title().isBlank() || input.title().length() > 200 || input.activityDate() == null
                || input.activityDate().getYear() < 1900 || input.activityDate().getYear() > 9998)
            throw DomainException.unprocessable("Informe título e data da proposta válidos.");
        var keep = new HashSet<>(input.retainedPhotoIds());
        if (keep.size() != input.retainedPhotoIds().size() || !record.photos.stream().map(p -> p.id).toList().containsAll(keep))
            throw DomainException.unprocessable("Seleção de fotos inválida.");
        var uploads = files == null ? List.<MultipartFile>of() : files;
        if (keep.size() + uploads.size() < 1 || keep.size() + uploads.size() > 50)
            throw DomainException.unprocessable("Cada registro deve ter entre 1 e 50 fotos.");
        if (uploads.stream().mapToLong(MultipartFile::getSize).sum() > 100L * 1024 * 1024)
            throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE, "Envie até 100 MiB de fotos por vez.");
        int limit = settings.ticketPolicy().attachmentLimitMb();
        var prepared = uploads.stream().map(f -> images.prepare(f, limit)).toList();
        var added = new ArrayList<ActivityPhoto>();
        // Decode and validate the entire batch before writing any files or modifying the album.
        for (var image : prepared) {
            var photoId = UUID.randomUUID(); var prefix = "pedagogical/" + record.id + "/" + photoId;
            var original = prefix + extension(image.mediaType()); var thumbnail = prefix + "-thumb.jpg";
            cleanup.stage(List.of(original, thumbnail));
            try { storage.store(original, image.original()); storage.store(thumbnail, image.thumbnail()); }
            catch (IOException exception) { throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível salvar as fotos. Tente novamente."); }
            added.add(new ActivityPhoto(photoId, original, thumbnail, image.mediaType(), image.original().length, image.width(), image.height()));
        }
        record.photos.stream().filter(p -> !keep.contains(p.id)).forEach(cleanup::removed);
        if (record.photos.stream().anyMatch(p -> !keep.contains(p.id))) {
            var retained = record.photos.stream().filter(p -> keep.contains(p.id)).toList();
            // Release unique photo IDs before Hibernate rewrites the ordered collection positions.
            record.photos.clear(); records.flush();
            record.photos.addAll(retained);
        }
        record.photos.addAll(added);
        record.title = input.title().trim(); record.activityDate = input.activityDate(); record.updatedAt = Instant.now();
    }
    private static String extension(String mediaType) {
        return switch (mediaType) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".jpg"; };
    }
    private ActivityRecord required(UUID id) {
        return records.findById(id).orElseThrow(() -> DomainException.notFound("Registro não encontrado."));
    }
    private boolean editor(ActivityRecord record, AuthenticatedUser actor) {
        return record.authorId.equals(actor.id()) || access.administrative(actor);
    }
    private void writable(SchoolClass classroom) {
        if (classroom.archived) throw DomainException.unprocessable("Reative a turma antes de alterar seus registros.");
    }
    private void authorizeEdit(ActivityRecord record, SchoolClass classroom, Long version, AuthenticatedUser actor) {
        writable(classroom);
        if (!editor(record, actor)) throw DomainException.forbidden("Somente a autora e a administração podem alterar este registro.");
        if (version == null) throw DomainException.unprocessable("Informe a versão do registro.");
        if (!record.rowVersion.equals(version)) throw DomainException.conflict("Este registro mudou. Suas alterações foram preservadas; consulte a versão atual.");
    }
    private Photo photo(ActivityRecord record, ActivityPhoto photo) {
        var url = "/api/v1/pedagogical/records/" + record.id + "/photos/" + photo.id;
        return new Photo(photo.id, url, url + "/thumbnail", photo.width, photo.height);
    }
    private View view(ActivityRecord record, SchoolClass classroom, AuthenticatedUser actor) {
        return new View(record.id, record.classId, classroom.name, classroom.archived, record.title, record.activityDate,
                record.authorId, record.authorName, record.photos.stream().map(p -> photo(record, p)).toList(), record.rowVersion,
                !classroom.archived && editor(record, actor), record.createdAt, record.updatedAt, settings.ticketPolicy().attachmentLimitMb());
    }
}
