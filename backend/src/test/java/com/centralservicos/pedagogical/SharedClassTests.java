package com.centralservicos.pedagogical;

import com.centralservicos.identity.*;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;

import static com.centralservicos.pedagogical.ClassContentVisibility.*;
import static com.centralservicos.pedagogical.PedagogicalViews.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SharedClassTests {
    @Autowired PedagogicalService classes;
    @Autowired ProjectService projects;
    @Autowired ActivityRecordService records;
    @Autowired IdentityService identity;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    @Test void visibilityDefaultsToRestrictedAndListingIncludesSharedWithoutDuplicates() {
        var admin = actor(Role.ADMIN); var teacher = actor(Role.REQUESTER); var reader = actor(Role.REQUESTER);
        var restricted = classes.saveClass(null, new ClassInput("Restrita", false, List.of(teacher.id()), null), admin);
        assertThat(restricted.contentVisibility()).isEqualTo(ALLOCATED_ONLY);
        var shared = classes.saveClass(null, new ClassInput("AEE", false, List.of(teacher.id(), reader.id()), null, PEDAGOGICAL_READ_ONLY), admin);
        var empty = classes.saveClass(null, new ClassInput("Sem alocação", false, List.of(), null, PEDAGOGICAL_READ_ONLY), admin);
        assertThat(classes.classes(reader)).extracting(ClassView::id).contains(shared.id(), empty.id()).doesNotContain(restricted.id());
        assertThat(classes.classes(reader).stream().filter(c -> c.id().equals(shared.id()))).hasSize(1);
        assertThat(classes.getClass(shared.id(), reader).canEdit()).isTrue();
        var view = classes.getClass(empty.id(), reader);
        assertThat(view.canCreate()).isFalse(); assertThat(view.canEdit()).isFalse(); assertThat(view.canManage()).isFalse();
        assertThat(classes.getClass(shared.id(), admin).canManage()).isTrue();
        assertThat(classes.getClass(shared.id(), actor(Role.MANAGER)).canEdit()).isTrue();
        var unchanged = classes.saveClass(shared.id(), new ClassInput("Renomeada", false, List.of(teacher.id()), shared.version()), admin);
        assertThat(unchanged.contentVisibility()).isEqualTo(PEDAGOGICAL_READ_ONLY);
        assertThat(classes.getClass(shared.id(), reader).canEdit()).isFalse();
        assertThatThrownBy(() -> classes.saveClass(shared.id(), new ClassInput("Negado", false, List.of(), unchanged.version(), ALLOCATED_ONLY), reader))
                .isInstanceOf(DomainException.class);
    }

    @Test void httpReadersCanConsultAllContentButCannotMutateOrManage() throws Exception {
        var admin = actor(Role.ADMIN); var teacher = actor(Role.REQUESTER); var reader = actor(Role.REQUESTER);
        var c = classes.saveClass(null, new ClassInput("AEE", false, List.of(teacher.id()), null, PEDAGOGICAL_READ_ONLY), admin);
        var plan = classes.create(c.id(), LocalDate.of(2026, 9, 28), teacher);
        var projectInput = new ProjectViews.CreateInput("Projeto AEE", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        var project = projects.create(c.id(), projectInput, teacher);
        var recordInput = new ActivityRecordViews.Input("Registro AEE", LocalDate.of(2026, 9, 28), null, List.of());
        var record = records.create(c.id(), recordInput, List.of(ActivityRecordTests.png()), teacher);
        var mvc = mvc(); var auth = user(reader); var base = "/api/v1/pedagogical";
        for (var url : List.of("/classes", "/classes/" + c.id(), "/classes/" + c.id() + "/plans", "/plans/" + plan.id(),
                "/classes/" + c.id() + "/projects", "/projects/" + project.id(), "/classes/" + c.id() + "/records", "/records/" + record.id(),
                record.photos().getFirst().url().substring(base.length()), record.photos().getFirst().thumbnailUrl().substring(base.length()))) {
            mvc.perform(get(base + url).with(auth)).andExpect(status().isOk());
        }
        mvc.perform(get(base + "/classes/" + c.id()).with(auth)).andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canCreate").value(false)).andExpect(jsonPath("$.canManage").value(false));
        mvc.perform(get(base + "/records/" + record.id()).with(auth)).andExpect(jsonPath("$.canEdit").value(false));
        mvc.perform(post(base + "/classes/" + c.id() + "/plans").with(auth).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new CreateInput(LocalDate.of(2026, 10, 5))))).andExpect(status().isForbidden());
        var days = plan.days().stream().map(d -> new DayInput(true, "Recesso", null, null, null, null)).toList();
        mvc.perform(put(base + "/plans/" + plan.id()).with(auth).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new EditInput(plan.version(), "Negado", List.of(teacher.id()), days)))).andExpect(status().isForbidden());
        mvc.perform(post(base + "/classes/" + c.id() + "/projects").with(auth).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(projectInput))).andExpect(status().isForbidden());
        var rich = ProjectViews.RichText.empty();
        mvc.perform(put(base + "/projects/" + project.id()).with(auth).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new ProjectViews.EditInput(project.version(), "Negado", project.startDate(), project.endDate(), "", List.of(), rich, List.of(), List.of(), rich))))
                .andExpect(status().isForbidden());
        for (var document : List.of("plans/" + plan.id(), "projects/" + project.id())) {
            for (var action : List.of("finalize", "reopen")) {
                mvc.perform(post(base + "/" + document + "/" + action).with(auth).with(csrf()).contentType("application/json")
                        .content("{\"version\":0}")).andExpect(status().isForbidden());
            }
            mvc.perform(delete(base + "/" + document).param("version", "0").with(auth).with(csrf())).andExpect(status().isForbidden());
        }
        var metadata = new MockMultipartFile("metadata", "", "application/json", json.writeValueAsBytes(recordInput));
        mvc.perform(multipart(base + "/classes/" + c.id() + "/records").file(metadata).file(ActivityRecordTests.png()).with(auth).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, base + "/records/" + record.id()).file(metadata).with(auth).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(base + "/records/" + record.id()).param("version", "0").with(auth).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put(base + "/classes/" + c.id()).with(auth).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new ClassInput("Negado", false, List.of(reader.id()), c.version(), ALLOCATED_ONLY))))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/classes").with(auth).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new ClassInput("Negado", false, List.of(reader.id()), null, PEDAGOGICAL_READ_ONLY))))
                .andExpect(status().isForbidden());
        mvc.perform(get(base + "/teachers").with(auth)).andExpect(status().isForbidden());
    }

    @Test void allocationArchiveVisibilityAndCurrentAccountStateAreRechecked() throws Exception {
        var admin = actor(Role.ADMIN); var author = actor(Role.REQUESTER); var reader = actor(Role.REQUESTER);
        var c = classes.saveClass(null, new ClassInput("AEE", false, List.of(author.id()), null, PEDAGOGICAL_READ_ONLY), admin);
        var r = records.create(c.id(), new ActivityRecordViews.Input("Fotos", LocalDate.of(2026, 9, 28), null, List.of()), List.of(ActivityRecordTests.png()), author);
        var unallocated = classes.saveClass(c.id(), new ClassInput(c.name(), false, List.of(), c.version(), PEDAGOGICAL_READ_ONLY), admin);
        assertThat(records.get(r.id(), author).canEdit()).isFalse();
        assertThatThrownBy(() -> records.delete(r.id(), r.version(), author)).isInstanceOf(DomainException.class);
        var archived = classes.saveClass(c.id(), new ClassInput(c.name(), true, List.of(), unallocated.version(), PEDAGOGICAL_READ_ONLY), admin);
        assertThat(classes.getClass(c.id(), admin).canManage()).isTrue();
        assertThat(classes.getClass(c.id(), admin).canCreate()).isFalse();
        assertThat(records.get(r.id(), reader).archived()).isTrue();
        assertThatThrownBy(() -> records.delete(r.id(), r.version(), admin)).hasMessageContaining("Reative");
        var restricted = classes.saveClass(c.id(), new ClassInput(c.name(), false, List.of(), archived.version(), ALLOCATED_ONLY), admin);
        assertThat(classes.classes(reader)).extracting(ClassView::id).doesNotContain(c.id());
        for (boolean thumb : List.of(false, true)) assertThatThrownBy(() -> records.image(r.id(), r.photos().getFirst().id(), thumb, reader)).isInstanceOf(DomainException.class);
        classes.saveClass(c.id(), new ClassInput(c.name(), false, List.of(), restricted.version(), PEDAGOGICAL_READ_ONLY), admin);
        var agent = actor(Role.AGENT);
        assertThatThrownBy(() -> classes.getClass(c.id(), agent)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> classes.classes(agent)).isInstanceOf(DomainException.class);
        identity.update(reader.id(), reader.displayName(), Set.of(Role.AGENT), true, admin.id());
        assertThatThrownBy(() -> records.get(r.id(), reader)).isInstanceOf(DomainException.class);
        identity.update(author.id(), author.displayName(), Set.of(Role.REQUESTER), false, admin.id());
        assertThatThrownBy(() -> records.get(r.id(), author)).isInstanceOf(DomainException.class);
    }

    @Test void httpAcceptsVisibilityAndRejectsUnknownValuesAndStaleVersions() throws Exception {
        var admin = actor(Role.ADMIN); var mvc = mvc();
        var c = classes.saveClass(null, new ClassInput("AEE", false, List.of(), null), admin);
        var url = "/api/v1/pedagogical/classes/" + c.id();
        mvc.perform(put(url).with(user(admin)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new ClassInput(c.name(), false, List.of(), c.version(), PEDAGOGICAL_READ_ONLY))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentVisibility").value("PEDAGOGICAL_READ_ONLY"));
        mvc.perform(put(url).with(user(admin)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new ClassInput(c.name(), false, List.of(), c.version(), ALLOCATED_ONLY))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/pedagogical/classes").with(user(admin)).with(csrf()).contentType("application/json")
                .content("{\"name\":\"AEE\",\"teacherIds\":[],\"contentVisibility\":\"PUBLIC\"}"))
                .andExpect(status().isBadRequest());
    }

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private AuthenticatedUser actor(Role role) {
        var u = identity.create(UUID.randomUUID() + "@example.test", "Professora " + UUID.randomUUID(), Set.of(role), null).user();
        identity.changePassword(u.id(), "permanent-password-123");
        return new AuthenticatedUser(u.id(), u.email(), u.displayName(), "unused", u.roles(), true, false);
    }
}
