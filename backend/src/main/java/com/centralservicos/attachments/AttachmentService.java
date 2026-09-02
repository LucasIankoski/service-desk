package com.centralservicos.attachments;

import com.centralservicos.shared.DomainException;
import com.centralservicos.shared.CommentVisibility;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

@Service
public class AttachmentService {

    private static final int MAX_ATTACHMENTS_PER_OPERATION = 5;
    private static final Set<String> IMAGE_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, Set<String>> ALLOWED_MEDIA_BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("csv", Set.of("text/csv", "text/plain", "application/csv")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/zip"))
    );

    private final AttachmentRepository repository;
    private final AttachmentStorage storage;
    private final MalwareScanner malwareScanner;
    private final Tika tika = new Tika();
    private final Clock clock = Clock.systemUTC();

    AttachmentService(AttachmentRepository repository, AttachmentStorage storage, MalwareScanner malwareScanner) {
        this.repository = repository;
        this.storage = storage;
        this.malwareScanner = malwareScanner;
    }

    @Transactional
    public List<AttachmentView> saveTicketFiles(UUID ticketId, UUID commentId, UUID uploaderId,
                                                CommentVisibility visibility, List<MultipartFile> files,
                                                int perFileLimitMb) {
        var safeFiles = normalize(files);
        if (safeFiles.size() > MAX_ATTACHMENTS_PER_OPERATION) {
            throw DomainException.unprocessable("Envie no máximo 5 anexos por operação.");
        }
        var views = new ArrayList<AttachmentView>();
        for (MultipartFile file : safeFiles) {
            var inspected = inspect(file, perFileLimitMb, ALLOWED_MEDIA_BY_EXTENSION.keySet());
            var extension = extension(file.getOriginalFilename());
            var key = key("tickets/" + ticketId, extension);
            store(key, inspected.content());
            var attachment = repository.save(new Attachment(ticketId, commentId, uploaderId,
                    safeOriginalName(file.getOriginalFilename()), key, inspected.mediaType(),
                    inspected.content().length, inspected.sha256(), visibility));
            views.add(attachment.toView());
        }
        return views;
    }

    public StoredFile storeBrandingImage(MultipartFile file, int perFileLimitMb) {
        var inspected = inspect(file, perFileLimitMb, Set.of("jpg", "jpeg", "png", "webp"));
        if (!IMAGE_MEDIA_TYPES.contains(inspected.mediaType())) {
            throw DomainException.unprocessable("Use uma imagem JPG, PNG ou WebP.");
        }
        var key = key("branding", extension(file.getOriginalFilename()));
        store(key, inspected.content());
        return new StoredFile(key, inspected.mediaType(), inspected.content().length, inspected.sha256());
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> listTicketFiles(UUID ticketId) {
        return repository.findAllByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(Attachment::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentFile requiredFile(UUID id) {
        var attachment = repository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Anexo não encontrado."));
        return new AttachmentFile(attachment.id(), attachment.ticketId(), attachment.visibilityName(),
                attachment.originalName(), attachment.mediaType(), attachment.storedName());
    }

    public StoredResource loadStored(String storedName, String mediaType, String filename) {
        try {
            return storage.load(storedName, mediaType, filename);
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível carregar o arquivo.");
        }
    }

    private List<MultipartFile> normalize(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    private InspectedFile inspect(MultipartFile file, int perFileLimitMb, Set<String> allowedExtensions) {
        var originalName = safeOriginalName(file.getOriginalFilename());
        var extension = extension(originalName);
        if (!allowedExtensions.contains(extension)) {
            throw DomainException.unprocessable("Tipo de arquivo não permitido.");
        }
        var maxBytes = Math.multiplyExact(Math.max(1, perFileLimitMb), 1024L * 1024L);
        if (file.getSize() <= 0 || file.getSize() > maxBytes) {
            throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Arquivo vazio ou acima do limite permitido.");
        }
        try {
            var content = file.getBytes();
            var detectedType = tika.detect(content, originalName).toLowerCase(Locale.ROOT);
            var allowedMediaTypes = ALLOWED_MEDIA_BY_EXTENSION.getOrDefault(extension, Set.of());
            if (!allowedMediaTypes.contains(detectedType)) {
                throw DomainException.unprocessable("A assinatura real do arquivo não corresponde ao tipo permitido.");
            }
            validateOfficeContainer(extension, content);
            malwareScanner.assertClean(content, originalName);
            return new InspectedFile(content, detectedType, sha256(content));
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado.");
        }
    }

    private void store(String key, byte[] content) {
        try {
            storage.store(key, content);
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível persistir o arquivo.");
        }
    }

    private String key(String prefix, String extension) {
        var day = DateTimeFormatter.BASIC_ISO_DATE.withZone(clock.getZone()).format(clock.instant());
        return prefix + "/" + day + "/" + UUID.randomUUID() + "." + extension;
    }

    private String safeOriginalName(String originalName) {
        var normalized = originalName == null ? "arquivo" : originalName.replace("\\", "/");
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank() || normalized.length() > 255) {
            throw DomainException.unprocessable("Nome do arquivo inválido.");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw DomainException.unprocessable("Nome do arquivo inválido.");
        }
        return normalized;
    }

    private void validateOfficeContainer(String extension, byte[] content) {
        String requiredDocument;
        if ("docx".equals(extension)) {
            requiredDocument = "word/document.xml";
        } else if ("xlsx".equals(extension)) {
            requiredDocument = "xl/workbook.xml";
        } else {
            return;
        }

        boolean contentTypes = false;
        boolean document = false;
        int entries = 0;
        try (var archive = new ZipInputStream(new ByteArrayInputStream(content))) {
            var entry = archive.getNextEntry();
            while (entry != null) {
                if (++entries > 10_000) {
                    throw DomainException.unprocessable("Documento Office inválido.");
                }
                var name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../")) {
                    throw DomainException.unprocessable("Documento Office inválido.");
                }
                contentTypes |= "[Content_Types].xml".equals(name);
                document |= requiredDocument.equals(name);
                if (contentTypes && document) {
                    return;
                }
                entry = archive.getNextEntry();
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException exception) {
            throw DomainException.unprocessable("Documento Office inválido.");
        }
        throw DomainException.unprocessable("O arquivo não contém um documento Office válido.");
    }

    private String extension(String filename) {
        var safe = safeOriginalName(filename).toLowerCase(Locale.ROOT);
        var dot = safe.lastIndexOf('.');
        if (dot < 0 || dot == safe.length() - 1) {
            throw DomainException.unprocessable("Arquivo sem extensão permitida.");
        }
        return safe.substring(dot + 1);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    public record AttachmentFile(UUID id, UUID ticketId, CommentVisibility visibility, String originalName,
                                 String mediaType, String storedName) {
    }

    private record InspectedFile(byte[] content, String mediaType, String sha256) {
    }
}
