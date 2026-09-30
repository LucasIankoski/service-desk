package com.centralservicos.pedagogical;

import com.centralservicos.identity.*;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDate;
import java.util.*;
import static com.centralservicos.pedagogical.ProjectViews.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class ProjectTests {
    @Autowired ProjectService projects;
    @Autowired PedagogicalService classes;
    @Autowired IdentityService identity;
    @Autowired WebApplicationContext context;

    @Test void lifecycleFormattingDatesConflictsAndHistoricalTeachers() {
        var admin = actor(Role.ADMIN); var a = actor(Role.REQUESTER); var b = actor(Role.REQUESTER);
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("Projetos", false, List.of(a.id(), b.id()), null), admin);
        var p = projects.create(c.id(), new CreateInput("Descobertas", LocalDate.of(2026,11,1), LocalDate.of(2027,2,28)), a);
        assertThat(projects.create(c.id(), new CreateInput(p.title(), p.startDate(), p.endDate()), b).id()).isNotEqualTo(p.id());
        assertThat(projects.list(c.id(), 2026, 12, "DRAFT", a.id(), b)).extracting(Summary::id).contains(p.id());
        assertThat(projects.list(c.id(), 2027, 1, null, null, a)).extracting(Summary::id).contains(p.id());
        assertThat(projects.list(c.id(), 2027, 3, null, null, a)).isEmpty();
        assertThatThrownBy(() -> projects.transition(p.id(), p.version(), true, a)).isInstanceOf(DomainException.class);
        var input = complete(p);
        var saved = projects.edit(p.id(), input, b);
        assertThat(projects.get(p.id(), a).generalObjective()).isEqualTo(formatted());
        assertThat(saved.activities()).extracting(Activity::title).containsExactly("Primeira", "Segunda", "Última");
        assertThatThrownBy(() -> projects.edit(p.id(), input, a)).isInstanceOf(DomainException.class).hasMessageContaining("mudou");
        var finalized = projects.transition(p.id(), saved.version(), true, a);
        assertThat(finalized.status()).isEqualTo("FINALIZED");
        assertThatThrownBy(() -> projects.edit(p.id(), complete(finalized), b)).isInstanceOf(DomainException.class).hasMessageContaining("Reabra");
        var reopened = projects.transition(p.id(), finalized.version(), false, b);
        assertThat(reopened.status()).isEqualTo("DRAFT");
        classes.saveClass(c.id(), new PedagogicalViews.ClassInput(c.name(), false, List.of(b.id()), c.version()), admin);
        assertThatThrownBy(() -> projects.get(p.id(), a)).isInstanceOf(DomainException.class);
        assertThat(projects.get(p.id(), b).teachers()).extracting(PedagogicalViews.Teacher::id).contains(a.id());
        var again = projects.edit(p.id(), complete(reopened), b);
        assertThat(again.teachers()).containsAll(p.teachers());
    }
    @Test void authorizationArchiveAndInactiveAccounts() {
        var manager = actor(Role.MANAGER); var teacher = actor(Role.REQUESTER);
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("A", false, List.of(teacher.id()), null), manager);
        var p = projects.create(c.id(), new CreateInput("Tema", LocalDate.of(2026,10,1), LocalDate.of(2026,10,9)), teacher);
        for (var denied : List.of(actor(Role.REQUESTER), actor(Role.AGENT))) {
            assertThatThrownBy(() -> projects.get(p.id(), denied)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> projects.list(c.id(), null, null, null, null, denied)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> projects.create(c.id(), new CreateInput(p.title(),p.startDate(),p.endDate()), denied)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> projects.edit(p.id(), complete(p), denied)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> projects.transition(p.id(), p.version(), true, denied)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> projects.transition(p.id(), p.version(), false, denied)).isInstanceOf(DomainException.class);
        }
        classes.saveClass(c.id(), new PedagogicalViews.ClassInput(c.name(), true, List.of(teacher.id()), c.version()), manager);
        assertThat(projects.get(p.id(), teacher).archived()).isTrue();
        assertThatThrownBy(() -> projects.edit(p.id(), complete(p), teacher)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> projects.transition(p.id(), p.version(), true, manager)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> projects.create(c.id(), new CreateInput(p.title(), p.startDate(), p.endDate()), manager)).isInstanceOf(DomainException.class);
        identity.update(teacher.id(), teacher.displayName(), Set.of(Role.REQUESTER), false, manager.id());
        assertThatThrownBy(() -> projects.get(p.id(), teacher)).isInstanceOf(DomainException.class);
    }
    @Test void partialDraftValidationAndUnsupportedRichText() {
        var admin = actor(Role.ADMIN);
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("A", false, List.of(), null), admin);
        var start = LocalDate.of(2026,10,1); var end = start.plusDays(8);
        assertThatThrownBy(() -> projects.create(c.id(), new CreateInput("Tema", end, start), admin)).isInstanceOf(DomainException.class);
        var p = projects.create(c.id(), new CreateInput("Tema", start, end), admin);
        var partial = new EditInput(p.version(),p.title(),start,end,"",List.of(),RichText.empty(),List.of(""),List.of(new Activity(start,"",RichText.empty())),RichText.empty());
        var saved = projects.edit(p.id(),partial,admin);
        assertThat(projects.get(p.id(),admin).ageRange()).isEmpty();
        assertThatThrownBy(() -> projects.transition(p.id(),saved.version(),true,admin)).isInstanceOf(DomainException.class);
        var outside = new EditInput(saved.version(),p.title(),start,end,"",List.of(),RichText.empty(),List.of(),List.of(new Activity(end.plusDays(1),"Atividade",formatted())),RichText.empty());
        assertThatThrownBy(() -> projects.edit(p.id(),outside,admin)).isInstanceOf(DomainException.class).hasMessageContaining("período");
        var invalid = new EditInput(saved.version(),p.title(),start,end,"",List.of(),new RichText(List.of(new Block("HTML",List.of()))),List.of(),List.of(),RichText.empty());
        assertThatThrownBy(() -> projects.edit(p.id(),invalid,admin)).isInstanceOf(DomainException.class);
        var tooLong = new EditInput(saved.version(),p.title(),start,end,"",List.of(),new RichText(List.of(new Block("PARAGRAPH",List.of(new Run("a".repeat(25000),false),new Run("b".repeat(25000),true))))),List.of(),List.of(),RichText.empty());
        assertThatThrownBy(() -> projects.edit(p.id(),tooLong,admin)).isInstanceOf(DomainException.class).hasMessageContaining("40.000");
        assertThatThrownBy(() -> projects.list(c.id(),null,1,null,null,admin)).isInstanceOf(DomainException.class);
    }
    @Test void httpContractCsrfVersionAndAuthorization() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var admin = actor(Role.ADMIN); var teacher = actor(Role.REQUESTER);
        identity.changePassword(admin.id(), "permanent-password-123"); identity.changePassword(teacher.id(), "permanent-password-123");
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("HTTP",false,List.of(teacher.id()),null),admin);
        var p = projects.create(c.id(),new CreateInput("Tema",LocalDate.of(2026,10,1),LocalDate.of(2026,10,9)),admin);
        String path = "/api/v1/pedagogical/projects/" + p.id();
        mvc.perform(get(path).with(user(teacher))).andExpect(status().isOk()).andExpect(jsonPath("$.generalObjective.blocks").isArray());
        mvc.perform(post(path+"/finalize").with(user(teacher)).contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path+"/finalize").with(user(teacher)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path+"/finalize").with(user(teacher)).with(csrf()).contentType("application/json").content("{\"version\":99}"))
                .andExpect(status().isConflict());
        String invalid = "{\"version\":0,\"title\":\"Tema\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-09\",\"ageRange\":\"\",\"teacherIds\":[],\"generalObjective\":{\"blocks\":[{\"type\":\"HTML\",\"runs\":[]}]},\"specificObjectives\":[],\"activities\":[],\"conclusion\":{\"blocks\":[]}}";
        mvc.perform(put(path).with(user(teacher)).with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        classes.saveClass(c.id(),new PedagogicalViews.ClassInput("HTTP",false,List.of(),c.version()),admin);
        mvc.perform(get(path).with(user(teacher))).andExpect(status().isForbidden());
    }
    @Test void simpleDevelopmentNeedsNoDateOrTitleAndSkipsEmptyObjectiveLines() {
        var admin = actor(Role.ADMIN); var teacher = actor(Role.REQUESTER);
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("Simples",false,List.of(teacher.id()),null),admin);
        var p = projects.create(c.id(),new CreateInput("Projeto simples",LocalDate.of(2026,10,1),LocalDate.of(2026,10,9)),teacher);
        var input = new EditInput(p.version(),p.title(),p.startDate(),p.endDate(),"2 anos",List.of(teacher.id()),formatted(),
                List.of("", "  Explorar  ", "", "Criar", ""),List.of(new Activity(null,"",formatted())),formatted());
        var saved = projects.edit(p.id(),input,teacher);
        var reloaded = projects.get(p.id(),teacher);
        assertThat(reloaded.activities()).hasSize(1);
        assertThat(reloaded.activities().getFirst().date()).isNull();
        assertThat(reloaded.activities().getFirst().title()).isEmpty();
        assertThat(reloaded.activities().getFirst().description()).isEqualTo(formatted());
        assertThat(reloaded.specificObjectives()).containsExactly("Explorar","Criar");
        assertThat(projects.transition(p.id(),saved.version(),true,teacher).status()).isEqualTo("FINALIZED");
    }
    private RichText formatted() {
        return new RichText(List.of(new Block("PARAGRAPH",List.of(new Run("Imaginar ",false),new Run("e brincar",true))), new Block("BULLET",List.of(new Run("Explorar materiais",false)))));
    }

    @Autowired com.centralservicos.audit.AuditService deletionAudit;
    @Test void administrativeDeletionChecksRoleVersionArchiveAndAudits() throws Exception {
        var administrator = actor(Role.ADMIN);
        var manager = actor(Role.MANAGER); var teacher = actor(Role.REQUESTER);
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("Exclusão", false, List.of(teacher.id()), null), manager);
        var p = projects.create(c.id(), new CreateInput("Tema", LocalDate.of(2026,10,1), LocalDate.of(2026,10,9)), manager);
        for (var denied : List.of(teacher, actor(Role.AGENT))) {
            assertThatThrownBy(() -> projects.delete(p.id(), p.version(), denied)).isInstanceOf(DomainException.class);
        }
        var staleAdmin = actor(Role.ADMIN);
        identity.update(staleAdmin.id(), staleAdmin.displayName(), Set.of(Role.REQUESTER), true, manager.id());
        assertThatThrownBy(() -> projects.delete(p.id(), p.version(), staleAdmin)).isInstanceOf(DomainException.class);
        var inactive = actor(Role.ADMIN);
        identity.update(inactive.id(), inactive.displayName(), Set.of(Role.ADMIN), false, manager.id());
        assertThatThrownBy(() -> projects.delete(p.id(), p.version(), inactive)).isInstanceOf(DomainException.class);
        var updated = projects.edit(p.id(), complete(p), teacher);
        assertThatThrownBy(() -> projects.delete(p.id(), p.version(), manager)).isInstanceOf(DomainException.class).hasMessageContaining("mudou");
        var finalized = projects.transition(p.id(), updated.version(), true, teacher);
        var archived = classes.saveClass(c.id(), new PedagogicalViews.ClassInput(c.name(), true, List.of(teacher.id()), c.version()), manager);
        assertThatThrownBy(() -> projects.delete(p.id(), finalized.version(), manager)).isInstanceOf(DomainException.class).hasMessageContaining("Reative");
        classes.saveClass(c.id(), new PedagogicalViews.ClassInput(c.name(), false, List.of(teacher.id()), archived.version()), manager);
        projects.delete(p.id(), finalized.version(), manager);
        assertThatThrownBy(() -> projects.get(p.id(), manager)).isInstanceOf(DomainException.class).hasMessageContaining("não encontrado");
        assertThat(projects.list(c.id(), null, null, null, null, manager)).isEmpty();
        assertThat(deletionAudit.list(org.springframework.data.domain.Pageable.unpaged()).getContent())
            .filteredOn(e -> p.id().toString().equals(e.entityId()) && e.action().equals("PED_PROJECT_DELETED"))
            .singleElement().satisfies(e -> {
                assertThat(e.actorId()).isEqualTo(manager.id());
                assertThat(e.entityType()).isEqualTo("PedagogicalProject");
                assertThat(e.details()).isNull();
            });
        var draft = projects.create(c.id(), new CreateInput("Tema", LocalDate.of(2026,10,1), LocalDate.of(2026,10,9)), manager);
        projects.delete(draft.id(), draft.version(), administrator);
        assertThat(projects.list(c.id(), null, null, null, null, manager)).isEmpty();
    }
    @Test void deleteHttpRequiresAdministrativeRoleCsrfAndCurrentVersion() throws Exception {
        var manager = actor(Role.MANAGER); var teacher = actor(Role.REQUESTER);
        identity.changePassword(manager.id(), "permanent-password-123");
        identity.changePassword(teacher.id(), "permanent-password-123");
        var c = classes.saveClass(null, new PedagogicalViews.ClassInput("Excluir HTTP", false, List.of(teacher.id()), null), manager);
        var p = projects.create(c.id(), new CreateInput("Tema", LocalDate.of(2026,10,1), LocalDate.of(2026,10,9)), manager);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var url = "/api/v1/pedagogical/projects/" + p.id();
        var auth = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager);
        mvc.perform(delete(url).param("version", Long.toString(p.version())).with(auth)).andExpect(status().isForbidden());
        mvc.perform(delete(url).with(auth).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete(url).param("version", Long.toString(p.version())).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacher)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(url).param("version", "-1").with(auth).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete(url).param("version", Long.toString(p.version())).with(auth).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(url).param("version", Long.toString(p.version())).with(auth).with(csrf())).andExpect(status().isNotFound());
    }
    private EditInput complete(View p) {
        return new EditInput(p.version(),p.title(),p.startDate(),p.endDate(),"0 a 6 anos",p.teachers().stream().map(PedagogicalViews.Teacher::id).toList(),
                formatted(),List.of("Criatividade","Interação"), List.of(new Activity(p.endDate(),"Última",formatted()),new Activity(p.startDate(),"Primeira",formatted()),new Activity(p.startDate(),"Segunda",formatted())),formatted());
    }
    private AuthenticatedUser actor(Role role) {
        var u = identity.create(UUID.randomUUID()+"@example.test","Professora "+UUID.randomUUID(),Set.of(role),null).user();
        return new AuthenticatedUser(u.id(),u.email(),u.displayName(),"unused",u.roles(),true,false);
    }
}
