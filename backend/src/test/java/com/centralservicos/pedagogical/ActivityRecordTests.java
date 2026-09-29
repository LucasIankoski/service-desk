package com.centralservicos.pedagogical;

import com.centralservicos.identity.*;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static com.centralservicos.pedagogical.ActivityRecordViews.*;
import static com.centralservicos.pedagogical.PedagogicalViews.ClassInput;

@SpringBootTest
@ActiveProfiles("test")
class ActivityRecordTests {
    @Autowired ActivityRecordService records;
    @Autowired PedagogicalService classes;
    @Autowired IdentityService identity;
    @Autowired PhotoCleanupService cleanup;
    @Autowired PhotoCleanupRepository queue;
    @Autowired com.centralservicos.attachments.AttachmentStorage storage;
    @Autowired WebApplicationContext context;

    @Test void albumLifecycleFiltersConflictAndDurableDeletion() throws Exception {
        var admin = roleUser(Role.ADMIN); var author = roleUser(Role.REQUESTER); var colleague = roleUser(Role.REQUESTER);
        var c = classes.saveClass(null, new ClassInput("Fotos", false, List.of(author.id(), colleague.id()), null), admin);
        var r = records.create(c.id(), input("Brincar", null, List.of()), List.of(png(), png()), author);
        assertThat(r.photos()).hasSize(2); assertThat(r.canEdit()).isTrue();
        assertThat(records.get(r.id(), colleague).canEdit()).isFalse();
        assertThat(records.list(c.id(), 2026, 9, 0, colleague).items()).extracting(Summary::id).contains(r.id());
        assertThat(records.list(c.id(), 2026, 10, 0, colleague).items()).isEmpty();
        assertThat(records.list(c.id(), null, null, 1, colleague).items()).isEmpty();
        assertThatThrownBy(() -> records.list(c.id(), null, 9, 0, colleague)).isInstanceOf(DomainException.class);
        var photo = r.photos().getFirst();
        var original = records.image(r.id(), photo.id(), false, colleague);
        assertThat(ImageIO.read(original.resource().getInputStream()).getWidth()).isEqualTo(800);
        var thumbnail = records.image(r.id(), photo.id(), true, colleague);
        assertThat(ImageIO.read(thumbnail.resource().getInputStream()).getWidth()).isEqualTo(640);
        var edited = records.update(r.id(), input("Pintura", r.version(), List.of(photo.id())), List.of(), author);
        assertThat(edited.photos()).hasSize(1); assertThat(edited.version()).isGreaterThan(r.version());
        assertThatThrownBy(() -> records.update(r.id(), input("Antigo", r.version(), List.of(photo.id())), List.of(), author)).hasMessageContaining("mudou");
        assertThatThrownBy(() -> records.delete(r.id(), r.version(), admin)).hasMessageContaining("mudou");
        assertThatThrownBy(() -> records.update(r.id(), input("Sem foto", edited.version(), List.of()), List.of(), author)).hasMessageContaining("1 e 50");
        records.delete(r.id(), edited.version(), admin);
        assertThatThrownBy(() -> records.get(r.id(), author)).isInstanceOf(DomainException.class);
        // Database timestamp precision varies; make asynchronous cleanup deterministically due.
        var due = queue.findAll(); due.forEach(job -> job.eligibleAt = Instant.now().minusSeconds(2)); queue.saveAll(due);
        cleanup.clean();
        assertThat(original.resource().exists()).isFalse();
        assertThat(thumbnail.resource().exists()).isFalse();
    }

    @Test void currentAllocationAndRolesProtectOriginalsThumbnailsAndMutations() throws Exception {
        var admin = roleUser(Role.ADMIN); var manager = roleUser(Role.MANAGER); var author = roleUser(Role.REQUESTER);
        var colleague = roleUser(Role.REQUESTER); var other = roleUser(Role.REQUESTER); var agent = roleUser(Role.AGENT);
        var c = classes.saveClass(null, new ClassInput("A", false, List.of(author.id(), colleague.id()), null), manager);
        var r = records.create(c.id(), input("Atividade", null, List.of()), List.of(png()), author);
        for (var denied : List.of(other, agent)) {
            assertThatThrownBy(() -> records.get(r.id(), denied)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> records.list(c.id(), null, null, 0, denied)).isInstanceOf(DomainException.class);
            for (boolean thumb : List.of(false, true)) assertThatThrownBy(() -> records.image(r.id(), r.photos().getFirst().id(), thumb, denied)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> records.create(c.id(), input("Negado", null, List.of()), List.of(png()), denied)).isInstanceOf(DomainException.class);
        }
        assertThatThrownBy(() -> records.delete(r.id(), r.version(), colleague)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.update(r.id(), input("Negado", r.version(), ids(r)), List.of(), colleague)).isInstanceOf(DomainException.class);
        assertThat(records.get(r.id(), manager).canEdit()).isTrue();
        var archived = classes.saveClass(c.id(), new ClassInput("A", true, List.of(author.id(), colleague.id()), c.version()), admin);
        assertThat(records.get(r.id(), author).canEdit()).isFalse();
        assertThat(records.image(r.id(), r.photos().getFirst().id(), true, author)).isNotNull();
        assertThatThrownBy(() -> records.update(r.id(), input("Arquivada", r.version(), ids(r)), List.of(), admin)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.delete(r.id(), r.version(), manager)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.create(c.id(), input("Arquivada", null, List.of()), List.of(png()), author)).isInstanceOf(DomainException.class);
        classes.saveClass(c.id(), new ClassInput("A", false, List.of(colleague.id()), archived.version()), admin);
        assertThatThrownBy(() -> records.get(r.id(), author)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.image(r.id(), r.photos().getFirst().id(), false, author)).isInstanceOf(DomainException.class);
        assertThat(records.get(r.id(), colleague).authorName()).isEqualTo(author.displayName());
        identity.update(colleague.id(), colleague.displayName(), Set.of(Role.REQUESTER), false, admin.id());
        assertThatThrownBy(() -> records.image(r.id(), r.photos().getFirst().id(), true, colleague)).isInstanceOf(DomainException.class);
        identity.update(manager.id(), manager.displayName(), Set.of(Role.AGENT), true, admin.id());
        assertThatThrownBy(() -> records.get(r.id(), manager)).isInstanceOf(DomainException.class);
    }

    @Test void rejectsInvalidBatchWithoutPublishingOrChangingPhotos() throws Exception {
        var author = roleUser(Role.MANAGER);
        var c = classes.saveClass(null, new ClassInput("Validação", false, List.of(), null), author);
        var bad = new MockMultipartFile("files", "foto.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> records.create(c.id(), input("Teste", null, List.of()), List.of(png(), bad), author)).isInstanceOf(DomainException.class);
        assertThat(records.list(c.id(), null, null, 0, author).items()).isEmpty();
        assertThatThrownBy(() -> records.create(c.id(), input(" ", null, List.of()), List.of(png()), author)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.create(c.id(), input("Teste", null, List.of()), Collections.nCopies(51, png()), author)).isInstanceOf(DomainException.class);
        var huge = new MockMultipartFile("files", "foto.png", "image/png", new byte[1]) { @Override public long getSize() { return 101L * 1024 * 1024; } };
        assertThatThrownBy(() -> records.create(c.id(), input("Teste", null, List.of()), List.of(huge), author)).hasMessageContaining("100 MiB");
        var r = records.create(c.id(), input("Original", null, List.of()), List.of(png()), author);
        assertThatThrownBy(() -> records.update(r.id(), input("Mudou", r.version(), List.of()), List.of(bad), author)).isInstanceOf(DomainException.class);
        assertThat(records.get(r.id(), author).title()).isEqualTo("Original");
        assertThat(records.get(r.id(), author).photos()).hasSize(1);
        assertThatThrownBy(() -> records.update(r.id(), input("Inválido", r.version(), List.of(UUID.randomUUID())), List.of(), author)).isInstanceOf(DomainException.class);
    }

    @Test void httpMultipartCsrfVersionAndPrivatePhotos() throws Exception {
        var actor = roleUser(Role.MANAGER); identity.changePassword(actor.id(), "permanent-password-123");
        var c = classes.saveClass(null, new ClassInput("HTTP", false, List.of(), null), actor);
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var metadata = new MockMultipartFile("metadata", "metadata.json", "application/json", "{\"title\":\"Fotos\",\"activityDate\":\"2026-09-27\",\"retainedPhotoIds\":[]}".getBytes());
        mvc.perform(multipart("/api/v1/pedagogical/classes/" + c.id() + "/records").file(metadata).file(png()).with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/pedagogical/classes/" + c.id() + "/records").file(metadata).file(png()).with(user(actor)).with(csrf())).andExpect(status().isCreated()).andExpect(jsonPath("$.photos.length()").value(1));
        var r = records.list(c.id(), null, null, 0, actor).items().getFirst();
        mvc.perform(get(r.cover().url()).with(user(actor))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentType("image/png"));
        mvc.perform(get(r.cover().thumbnailUrl()).with(user(actor))).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        mvc.perform(get(r.cover().url())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/pedagogical/records/" + r.id()).with(user(actor)).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/pedagogical/records/" + r.id() + "?version=10").with(user(actor)).with(csrf())).andExpect(status().isConflict());
        identity.update(actor.id(), actor.displayName(), Set.of(Role.AGENT), true, actor.id());
        mvc.perform(get(r.cover().url()).with(user(actor))).andExpect(status().isForbidden());
    }

    @Test void cleanupRetainsReferencedPhotosAndDeletesAbandonedWrites() throws Exception {
        var actor = roleUser(Role.ADMIN);
        var c = classes.saveClass(null, new ClassInput("Limpeza", false, List.of(), null), actor);
        var r = records.create(c.id(), input("Conservar", null, List.of()), List.of(png()), actor);
        var original = records.image(r.id(), r.photos().getFirst().id(), false, actor);
        var abandonedKey = "pedagogical/test-abandoned-" + UUID.randomUUID() + ".png";
        cleanup.stage(List.of(abandonedKey));
        storage.store(abandonedKey, png().getBytes());
        var abandoned = storage.load(abandonedKey, "image/png", "abandoned.png");
        var entries = queue.findAll(); entries.forEach(job -> job.eligibleAt = Instant.now().minusSeconds(1)); queue.saveAll(entries);
        cleanup.clean();
        assertThat(original.resource().exists()).isTrue();
        assertThat(abandoned.resource().exists()).isFalse();
    }

    @Test void fiftyPhotosCanBeRetainedAndReplacedWithoutChangingTheirOrder() throws Exception {
        var actor = roleUser(Role.MANAGER);
        var c = classes.saveClass(null, new ClassInput("50 fotos", false, List.of(), null), actor);
        var r = records.create(c.id(), input("Completo", null, List.of()), Collections.nCopies(50, png()), actor);
        assertThat(r.photos()).hasSize(50);
        assertThatThrownBy(() -> records.update(r.id(), input("Excesso", r.version(), ids(r)), List.of(png()), actor))
            .hasMessageContaining("1 e 50");
        var kept = ids(r).subList(1, 50);
        var updated = records.update(r.id(), input("Substituída", r.version(), kept), List.of(png()), actor);
        assertThat(updated.photos()).hasSize(50);
        assertThat(ids(updated).subList(0, 49)).containsExactlyElementsOf(kept);
        assertThat(records.get(r.id(), actor).photos()).isEqualTo(updated.photos());
        records.delete(updated.id(), updated.version(), actor);
    }

    static MockMultipartFile png() throws IOException {
        var image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
        return new MockMultipartFile("files", "foto.png", "image/png", out.toByteArray());
    }
    private Input input(String title, Long version, List<UUID> ids) { return new Input(title, LocalDate.of(2026, 9, 27), version, ids); }
    private List<UUID> ids(View r) { return r.photos().stream().map(Photo::id).toList(); }
    private AuthenticatedUser roleUser(Role role) {
        var u = identity.create(UUID.randomUUID() + "@example.test", "Professora " + UUID.randomUUID(), Set.of(role), null).user();
        return new AuthenticatedUser(u.id(), u.email(), u.displayName(), "unused", u.roles(), true, false);
    }
}
