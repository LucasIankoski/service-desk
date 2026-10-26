package com.centralservicos.pedagogical;

import com.centralservicos.identity.*;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:general_records;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Transactional
class GeneralRecordTests {
    @Autowired ActivityRecordService records;
    @Autowired PedagogicalService classes;
    @Autowired IdentityService identity;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    @Test void independentAlbumsAreSharedAndOnlyAuthorOrAdministrationCanChangeThem() throws Exception {
        var author = actor(Role.REQUESTER); var colleague = actor(Role.REQUESTER); var manager = actor(Role.MANAGER); var admin = actor(Role.ADMIN);
        var r = records.createGeneral(input("Festa da escola", null, List.of()), List.of(ActivityRecordTests.png()), author);
        assertThat(r.maxPhotos()).isEqualTo(100); assertThat(r.canEdit()).isTrue();
        assertThat(r.photos().getFirst().url()).startsWith("/api/v1/records/");
        assertThat(records.getGeneral(r.id(), colleague).canEdit()).isFalse();
        assertThat(records.getGeneral(r.id(), manager).canEdit()).isTrue();
        assertThat(records.getGeneral(r.id(), admin).canEdit()).isTrue();
        var page = records.listGeneral(2026, 10, 0, colleague);
        assertThat(page.canCreate()).isTrue(); assertThat(page.maxPhotos()).isEqualTo(100);
        assertThat(page.items()).extracting(GeneralRecordViews.Summary::id).contains(r.id());
        assertThat(records.listGeneral(2026, 9, 0, author).items()).isEmpty();
        assertThatThrownBy(() -> records.listGeneral(null, 10, 0, author)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.updateGeneral(r.id(), input("Negado", r.version(), ids(r)), List.of(), colleague)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.deleteGeneral(r.id(), r.version(), colleague)).isInstanceOf(DomainException.class);
        for (boolean thumbnail : List.of(false, true)) {
            try (var stream = records.generalImage(r.id(), r.photos().getFirst().id(), thumbnail, colleague).resource().getInputStream()) {
                assertThat(stream.read()).isNotEqualTo(-1);
            }
        }
        var changed = records.updateGeneral(r.id(), input("Evento editado", r.version(), ids(r)), List.of(), author);
        assertThat(changed.version()).isGreaterThan(r.version());
        assertThatThrownBy(() -> records.updateGeneral(r.id(), input("Antigo", r.version(), ids(r)), List.of(), author)).hasMessageContaining("mudou");
        assertThatThrownBy(() -> records.deleteGeneral(r.id(), r.version(), manager)).hasMessageContaining("mudou");
        records.deleteGeneral(r.id(), changed.version(), manager);
        assertThatThrownBy(() -> records.getGeneral(r.id(), author)).isInstanceOf(DomainException.class);
    }

    @Test void hundredPhotoLimitCountsRetainedAndNewPhotosWhileClassRecordsRemainAtFifty() throws Exception {
        var author = actor(Role.REQUESTER); var admin = actor(Role.ADMIN); var png = ActivityRecordTests.png();
        var hundred = Collections.<org.springframework.web.multipart.MultipartFile>nCopies(100, png);
        assertThatThrownBy(() -> records.createGeneral(input("Sem foto", null, List.of()), List.of(), author)).hasMessageContaining("1 e 100");
        assertThatThrownBy(() -> records.createGeneral(input("Muitas", null, List.of()), Collections.nCopies(101, png), author)).hasMessageContaining("1 e 100");
        var r = records.createGeneral(input("Cem fotos", null, List.of()), hundred, author);
        assertThat(r.photos()).hasSize(100);
        assertThatThrownBy(() -> records.updateGeneral(r.id(), input(r.title(), r.version(), ids(r)), List.of(png), author)).hasMessageContaining("1 e 100");
        var changed = records.updateGeneral(r.id(), input(r.title(), r.version(), ids(r).subList(1, 100)), List.of(png), author);
        assertThat(changed.photos()).hasSize(100);
        assertThat(ids(changed).subList(0, 99)).containsExactlyElementsOf(ids(r).subList(1, 100));
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var metadata = new MockMultipartFile("metadata", "", "application/json", json.writeValueAsBytes(input(changed.title(), changed.version(), ids(changed))));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/records/" + r.id()).file(metadata).with(user(author)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos.length()").value(100));
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("Turma", false, List.of(author.id()), null), admin);
        assertThatThrownBy(() -> records.create(c.id(), input("Turma", null, List.of()), Collections.nCopies(51, png), author)).hasMessageContaining("1 e 50");
        var classAlbum = records.create(c.id(), input("Turma", null, List.of()), Collections.nCopies(50, png), author);
        assertThat(classAlbum.photos()).hasSize(50);
        assertThatThrownBy(() -> records.update(classAlbum.id(), input("Turma", classAlbum.version(), classAlbum.photos().stream().map(ActivityRecordViews.Photo::id).toList()), List.of(png), author)).hasMessageContaining("1 e 50");
    }

    @Test void httpRoutesRejectIdsFromOtherContextIncludingPhotosAndMutations() throws Exception {
        var admin = actor(Role.ADMIN); var author = actor(Role.REQUESTER);
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("Turma", false, List.of(author.id()), null), admin);
        var classAlbum = records.create(c.id(), input("Turma", null, List.of()), List.of(ActivityRecordTests.png()), author);
        var general = records.createGeneral(input("Geral", null, List.of()), List.of(ActivityRecordTests.png()), author);
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var metadata = new MockMultipartFile("metadata", "", "application/json", json.writeValueAsBytes(input("Alterado", 0L, List.of())));
        for (var pair : List.of(Map.entry("/api/v1/records/", classAlbum.id()), Map.entry("/api/v1/pedagogical/records/", general.id()))) {
            var url = pair.getKey() + pair.getValue();
            var photoId = pair.getValue().equals(general.id()) ? general.photos().getFirst().id() : classAlbum.photos().getFirst().id();
            mvc.perform(get(url).with(user(admin))).andExpect(status().isNotFound());
            mvc.perform(get(url + "/photos/" + photoId).with(user(admin))).andExpect(status().isNotFound());
            mvc.perform(get(url + "/photos/" + photoId + "/thumbnail").with(user(admin))).andExpect(status().isNotFound());
            mvc.perform(multipart(HttpMethod.PUT, url).file(metadata).with(user(admin)).with(csrf())).andExpect(status().isNotFound());
            mvc.perform(delete(url).param("version", "0").with(user(admin)).with(csrf())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/records/" + general.id()).with(user(author))).andExpect(status().isOk())
                .andExpect(jsonPath("$.classId").doesNotExist()).andExpect(jsonPath("$.className").doesNotExist())
                .andExpect(jsonPath("$.archived").doesNotExist()).andExpect(jsonPath("$.maxPhotos").value(100));
        mvc.perform(get(general.photos().getFirst().url()).with(user(author))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/v1/records").with(user(author))).andExpect(status().isForbidden());
        var colleague = actor(Role.REQUESTER);
        mvc.perform(delete("/api/v1/records/" + general.id()).param("version", "0").with(user(colleague)).with(csrf())).andExpect(status().isForbidden());
        assertThat(records.list(c.id(), null, null, 0, author).items()).extracting(ActivityRecordViews.Summary::id).contains(classAlbum.id()).doesNotContain(general.id());
        assertThat(records.listGeneral(null, null, 0, author).items()).extracting(GeneralRecordViews.Summary::id).contains(general.id()).doesNotContain(classAlbum.id());
    }

    @Test void currentRolesAccountStateAndUploadLimitsAreEnforced() throws Exception {
        var admin = actor(Role.ADMIN); var author = actor(Role.REQUESTER); var agent = actor(Role.AGENT);
        var r = records.createGeneral(input("Evento", null, List.of()), List.of(ActivityRecordTests.png()), author);
        assertThatThrownBy(() -> records.listGeneral(null, null, 0, agent)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.getGeneral(r.id(), agent)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.createGeneral(input("Evento", null, List.of()), List.of(ActivityRecordTests.png()), agent)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> records.generalImage(r.id(), r.photos().getFirst().id(), false, agent)).isInstanceOf(DomainException.class);
        var oversized = new MockMultipartFile("files", "foto.png", "image/png", new byte[]{1}) {
            @Override public long getSize() { return 101L * 1024 * 1024; }
        };
        assertThatThrownBy(() -> records.createGeneral(input("Grande", null, List.of()), List.of(oversized), author))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        assertThatThrownBy(() -> records.createGeneral(input("Inválido", null, List.of()), List.of(new MockMultipartFile("files", "foto.png", "image/png", new byte[]{1})), author)).isInstanceOf(DomainException.class);
        identity.update(author.id(), author.displayName(), Set.of(Role.AGENT), true, admin.id());
        assertThatThrownBy(() -> records.getGeneral(r.id(), author)).isInstanceOf(DomainException.class);
        identity.update(author.id(), author.displayName(), Set.of(Role.REQUESTER), false, admin.id());
        assertThatThrownBy(() -> records.deleteGeneral(r.id(), r.version(), author)).isInstanceOf(DomainException.class);
    }

    private ActivityRecordViews.Input input(String title, Long version, List<UUID> ids) { return new ActivityRecordViews.Input(title, LocalDate.of(2026, 10, 25), version, ids); }
    private List<UUID> ids(GeneralRecordViews.View r) { return r.photos().stream().map(ActivityRecordViews.Photo::id).toList(); }
    private AuthenticatedUser actor(Role role) {
        var u = identity.create(UUID.randomUUID() + "@example.test", "Pessoa " + UUID.randomUUID(), Set.of(role), null).user();
        identity.changePassword(u.id(), "permanent-password-123");
        return new AuthenticatedUser(u.id(), u.email(), u.displayName(), "unused", u.roles(), true, false);
    }
}
