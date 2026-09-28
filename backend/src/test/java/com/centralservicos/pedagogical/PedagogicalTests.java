package com.centralservicos.pedagogical;
import com.centralservicos.identity.*;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
