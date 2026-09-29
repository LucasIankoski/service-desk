package com.centralservicos.pedagogical;
import com.centralservicos.identity.*;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.time.LocalDate;
import java.util.*;
import static com.centralservicos.pedagogical.PedagogicalViews.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PedagogicalTests {
    @Autowired PedagogicalService service;
    @Autowired IdentityService identity;
    @Test void sharedPlanCompletesReopensAndPreservesHistoricalTeachers() {
        var admin = user(Role.ADMIN); var a = user(Role.REQUESTER); var b = user(Role.REQUESTER);
        var c = service.saveClass(null, new ClassInput("BII", false, List.of(a.id(), b.id()), null), admin);
        var p = service.create(c.id(), LocalDate.of(2026,9,28), a);
        assertThat(p.days()).hasSize(5);
        assertThat(p.teachers()).hasSize(2);
        assertThat(service.list(c.id(), 2026, 9, null, null, b)).extracting(Summary::id).contains(p.id());
        assertThat(service.list(c.id(), 2026, 10, null, null, b)).extracting(Summary::id).contains(p.id());
        assertThatThrownBy(() -> service.create(c.id(), p.weekStart(), b)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.transition(p.id(), p.version(), true, a)).isInstanceOf(DomainException.class);
        var complete = service.edit(p.id(), complete(p), b);
        assertThatThrownBy(() -> service.edit(p.id(), complete(p), a)).isInstanceOf(DomainException.class).hasMessageContaining("mudou");
        var finalPlan = service.transition(p.id(), complete.version(), true, a);
        assertThat(finalPlan.status()).isEqualTo("FINALIZED");
        assertThatThrownBy(() -> service.edit(p.id(), complete(finalPlan), a)).isInstanceOf(DomainException.class);
        var reopened = service.transition(p.id(), finalPlan.version(), false, b);
        assertThat(reopened.status()).isEqualTo("DRAFT");
        service.saveClass(c.id(), new ClassInput("BII", false, List.of(b.id()), c.version()), admin);
        assertThatThrownBy(() -> service.get(p.id(), a)).isInstanceOf(DomainException.class);
        assertThat(service.classes(a)).isEmpty();
        assertThat(service.get(p.id(), b).teachers()).extracting(Teacher::id).contains(a.id(), b.id());
    }
    @Test void accessArchiveActiveUsersAndYearBoundary() {
        var manager = user(Role.MANAGER); var teacher = user(Role.REQUESTER); var other = user(Role.REQUESTER);
        var c = service.saveClass(null, new ClassInput("A", false, List.of(teacher.id()), null), manager);
        var p = service.create(c.id(), LocalDate.of(2026,12,28), manager);
        assertThat(service.list(c.id(),2027,1,null,null,teacher)).extracting(Summary::id).contains(p.id());
        for (var actor : List.of(other, user(Role.AGENT))) {
            assertThatThrownBy(() -> service.getClass(c.id(),actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> service.get(p.id(),actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> service.list(c.id(),null,null,null,null,actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> service.edit(p.id(),complete(p),actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> service.transition(p.id(),p.version(),true,actor)).isInstanceOf(DomainException.class);
        }
        assertThatThrownBy(() -> service.teachers(teacher)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.create(c.id(),LocalDate.of(2026,12,29),teacher)).isInstanceOf(DomainException.class);
        var archived = service.saveClass(c.id(),new ClassInput("A",true,List.of(teacher.id()),c.version()),manager);
        assertThat(service.get(p.id(),teacher).archived()).isTrue();
        assertThatThrownBy(() -> service.edit(p.id(),complete(p),teacher)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.create(c.id(),LocalDate.of(2027,1,4),manager)).isInstanceOf(DomainException.class);
        service.saveClass(c.id(),new ClassInput("A",false,List.of(teacher.id()),archived.version()),manager);
        identity.update(teacher.id(),teacher.displayName(),Set.of(Role.REQUESTER),false,manager.id());
        assertThatThrownBy(() -> service.get(p.id(),teacher)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.saveClass(null,new ClassInput("B",false,List.of(teacher.id()),null),manager)).isInstanceOf(DomainException.class);
    }
    @Autowired org.springframework.web.context.WebApplicationContext context;

    @Test void httpEnforcesAllocationVersionValidationAndCsrf() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var manager = user(Role.MANAGER);
        var teacher = user(Role.REQUESTER);
        identity.changePassword(manager.id(), "permanent-password-123");
        identity.changePassword(teacher.id(), "permanent-password-123");
        var c = service.saveClass(null, new ClassInput("HTTP", false, List.of(teacher.id()), null), manager);
        var p = service.create(c.id(), LocalDate.of(2028,1,3), manager);
        var authenticated = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacher);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/pedagogical/plans/"+p.id()).with(authenticated))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/pedagogical/plans/"+p.id()+"/finalize")
                .with(authenticated).contentType("application/json").content("{\"version\":0}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/pedagogical/plans/"+p.id()+"/finalize")
                .with(authenticated).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/pedagogical/plans/"+p.id())
                .with(authenticated).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"version\":0,\"teacherIds\":[],\"days\":[]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        service.saveClass(c.id(), new ClassInput("HTTP", false, List.of(), c.version()), manager);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/pedagogical/plans/"+p.id()).with(authenticated))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }


    @Autowired com.centralservicos.audit.AuditService deletionAudit;
    @Test void administrativeDeletionChecksRoleVersionArchiveAndAudits() throws Exception {
        var administrator = user(Role.ADMIN);
        var manager = user(Role.MANAGER); var teacher = user(Role.REQUESTER);
        var c = service.saveClass(null, new PedagogicalViews.ClassInput("Exclusão", false, List.of(teacher.id()), null), manager);
        var p = service.create(c.id(), LocalDate.of(2026,9,28), manager);
        for (var denied : List.of(teacher, user(Role.AGENT))) {
            assertThatThrownBy(() -> service.delete(p.id(), p.version(), denied)).isInstanceOf(DomainException.class);
        }
        var staleAdmin = user(Role.ADMIN);
        identity.update(staleAdmin.id(), staleAdmin.displayName(), Set.of(Role.REQUESTER), true, manager.id());
        assertThatThrownBy(() -> service.delete(p.id(), p.version(), staleAdmin)).isInstanceOf(DomainException.class);
        var inactive = user(Role.ADMIN);
        identity.update(inactive.id(), inactive.displayName(), Set.of(Role.ADMIN), false, manager.id());
        assertThatThrownBy(() -> service.delete(p.id(), p.version(), inactive)).isInstanceOf(DomainException.class);
        var updated = service.edit(p.id(), complete(p), teacher);
        assertThatThrownBy(() -> service.delete(p.id(), p.version(), manager)).isInstanceOf(DomainException.class).hasMessageContaining("mudou");
        var finalized = service.transition(p.id(), updated.version(), true, teacher);
        var archived = service.saveClass(c.id(), new PedagogicalViews.ClassInput(c.name(), true, List.of(teacher.id()), c.version()), manager);
        assertThatThrownBy(() -> service.delete(p.id(), finalized.version(), manager)).isInstanceOf(DomainException.class).hasMessageContaining("Reative");
        service.saveClass(c.id(), new PedagogicalViews.ClassInput(c.name(), false, List.of(teacher.id()), archived.version()), manager);
        service.delete(p.id(), finalized.version(), manager);
        assertThatThrownBy(() -> service.get(p.id(), manager)).isInstanceOf(DomainException.class).hasMessageContaining("não encontrado");
        assertThat(service.list(c.id(), null, null, null, null, manager)).isEmpty();
        assertThat(deletionAudit.list(org.springframework.data.domain.Pageable.unpaged()).getContent())
            .filteredOn(e -> p.id().toString().equals(e.entityId()) && e.action().equals("PED_PLANNING_DELETED"))
            .singleElement().satisfies(e -> {
                assertThat(e.actorId()).isEqualTo(manager.id());
                assertThat(e.entityType()).isEqualTo("WeeklyPlanning");
                assertThat(e.details()).isNull();
            });
        var draft = service.create(c.id(), LocalDate.of(2026,9,28), manager);
        service.delete(draft.id(), draft.version(), administrator);
        assertThat(service.list(c.id(), null, null, null, null, manager)).isEmpty();
    }
    @Test void deleteHttpRequiresAdministrativeRoleCsrfAndCurrentVersion() throws Exception {
        var manager = user(Role.MANAGER); var teacher = user(Role.REQUESTER);
        identity.changePassword(manager.id(), "permanent-password-123");
        identity.changePassword(teacher.id(), "permanent-password-123");
        var c = service.saveClass(null, new PedagogicalViews.ClassInput("Excluir HTTP", false, List.of(teacher.id()), null), manager);
        var p = service.create(c.id(), LocalDate.of(2026,9,28), manager);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var url = "/api/v1/pedagogical/plans/" + p.id();
        var auth = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager);
        mvc.perform(delete(url).param("version", Long.toString(p.version())).with(auth)).andExpect(status().isForbidden());
        mvc.perform(delete(url).with(auth).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete(url).param("version", Long.toString(p.version())).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacher)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(url).param("version", "-1").with(auth).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete(url).param("version", Long.toString(p.version())).with(auth).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(url).param("version", Long.toString(p.version())).with(auth).with(csrf())).andExpect(status().isNotFound());
    }
    private EditInput complete(PlanView p) {
        var days = new ArrayList<DayInput>();
        days.add(new DayInput(false,null,"Proposta","Objetivos","Desenvolvimento","Recursos"));
        for(int i=1;i<5;i++) days.add(new DayInput(true,"Recesso",null,null,null,null));
        return new EditInput(p.version(),"Experiências",p.teachers().stream().map(Teacher::id).toList(),days);
    }
    private AuthenticatedUser user(Role role) {
        var u=identity.create(UUID.randomUUID()+"@example.test","Professora "+UUID.randomUUID(),Set.of(role),null).user();
        return new AuthenticatedUser(u.id(),u.email(),u.displayName(),"unused",u.roles(),true,false);
    }
}
