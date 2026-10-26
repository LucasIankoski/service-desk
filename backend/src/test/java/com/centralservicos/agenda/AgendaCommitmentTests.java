package com.centralservicos.agenda;

import com.centralservicos.identity.*;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:commitments;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class AgendaCommitmentTests {
    @Autowired AgendaOccurrenceService notes;
    @Autowired IdentityService identity;
    @Autowired WebApplicationContext context;
    @Autowired AgendaOccurrenceRepository repository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void legacyRoleDoesNotBreakCollaboratorSelectorOrCommitmentCreationAndListing() throws Exception {
        var manager=actor(Role.MANAGER); var collaborator=actor(Role.REQUESTER);
        jdbc.update("insert into user_role(user_id,role_name) values(?,?)",collaborator.id().toString(),"GUARDIAN");
        try {
            var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get("/api/v1/agenda/commitments/collaborators").with(user(manager)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '"+collaborator.id()+"')].displayName").value(collaborator.displayName()));
            var day=LocalDate.of(2034,1,3);
            var note=notes.create(day,"Estará ausente.",manager,DailyNoteKind.COMMITMENT,collaborator.id());
            assertThat(note.collaboratorName()).isEqualTo(collaborator.displayName());
            assertThat(notes.list(day,day.plusDays(1),manager,DailyNoteKind.COMMITMENT)).extracting(AgendaOccurrenceView::collaboratorId).containsExactly(collaborator.id());
            var changed=notes.update(note.id(),day,"Anotação revisada",note.version(),manager,DailyNoteKind.COMMITMENT,collaborator.id());
            notes.delete(note.id(),changed.version(),manager,DailyNoteKind.COMMITMENT);
        } finally {
            jdbc.update("delete from user_role where user_id=? and role_name=?",collaborator.id().toString(),"GUARDIAN");
        }
    }
    @Test void collaboratorsIncludeAllProfilesAndCommitmentsKeepSubjectSeparateFromAuthor() throws Exception {
        var manager=actor(Role.MANAGER); var requester=actor(Role.REQUESTER); var agent=actor(Role.AGENT); var admin=actor(Role.ADMIN);
        identity.update(requester.id(), "Pamela Vidaletti", requester.roles(), true, manager.id());
        identity.update(agent.id(), agent.displayName(), agent.roles(), false, manager.id());
        assertThat(notes.collaborators(manager)).extracting(IdentityService.CollaboratorView::id).contains(requester.id(),agent.id(),admin.id(),manager.id());
        var day=LocalDate.of(2033,1,3);
        assertThatThrownBy(() -> notes.create(day,"Vai ao médico e estará ausente.",manager,DailyNoteKind.COMMITMENT)).hasMessageContaining("Selecione");
        var note=notes.create(day,"Vai ao médico e estará ausente.",manager,DailyNoteKind.COMMITMENT,requester.id());
        assertThat(note.createdById()).isEqualTo(manager.id());
        assertThat(note.collaboratorId()).isEqualTo(requester.id());
        assertThat(note.collaboratorName()).isEqualTo("Pamela Vidaletti");
        var changed=notes.update(note.id(),day,note.body(),note.version(),manager,DailyNoteKind.COMMITMENT,agent.id());
        assertThat(changed.collaboratorId()).isEqualTo(agent.id());
        assertThatThrownBy(() -> notes.create(day,"Inválido",manager,DailyNoteKind.COMMITMENT,UUID.randomUUID())).isInstanceOf(DomainException.class);
        var legacy=repository.saveAndFlush(new AgendaOccurrence(day,"Compromisso anterior",manager.id(),DailyNoteKind.COMMITMENT));
        assertThat(notes.list(day,day.plusDays(1),manager,DailyNoteKind.COMMITMENT)).anySatisfy(n -> { assertThat(n.id()).isEqualTo(legacy.id()); assertThat(n.collaboratorId()).isNull(); });
        var assigned=notes.update(legacy.id(),day,legacy.body(),legacy.version(),manager,DailyNoteKind.COMMITMENT,requester.id());
        assertThat(assigned.collaboratorName()).isEqualTo("Pamela Vidaletti");
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/agenda/commitments/collaborators").with(user(manager))).andExpect(status().isOk()).andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(get("/api/v1/agenda/commitments/collaborators").with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/agenda/commitments").with(user(manager)).with(csrf()).contentType("application/json").content("{\"date\":\"2033-01-03\",\"body\":\"Sem colaborador\"}")).andExpect(status().isUnprocessableEntity());
        var occurrence=notes.create(day,"Ocorrência independente",manager);
        assertThat(occurrence.collaboratorId()).isNull();
        notes.delete(changed.id(),changed.version(),manager,DailyNoteKind.COMMITMENT);
        notes.delete(assigned.id(),assigned.version(),manager,DailyNoteKind.COMMITMENT);
        notes.delete(occurrence.id(),occurrence.version(),manager);
    }
    @Test void commitmentsAndOccurrencesRemainIndependentVersionedAndSharedWithManagers() throws Exception {
        var a = actor(Role.MANAGER); var b = actor(Role.MANAGER); var day = LocalDate.of(2032, 10, 25);
        var occurrence = notes.create(day, "Acontecimento", a);
        var commitment = notes.create(day, "  Compromisso  ", a, DailyNoteKind.COMMITMENT, a.id());
        assertThat(commitment.body()).isEqualTo("Compromisso");
        assertThat(notes.list(day, day.plusDays(1), b)).extracting(AgendaOccurrenceView::id).containsExactly(occurrence.id());
        assertThat(notes.list(day, day.plusDays(1), b, DailyNoteKind.COMMITMENT)).extracting(AgendaOccurrenceView::id).containsExactly(commitment.id());
        var changed = notes.update(commitment.id(), day, "Revisado", commitment.version(), b, DailyNoteKind.COMMITMENT, a.id());
        assertThatThrownBy(() -> notes.update(commitment.id(), day, "Antigo", commitment.version(), a, DailyNoteKind.COMMITMENT, a.id())).hasMessageContaining("mudou");
        assertThatThrownBy(() -> notes.delete(commitment.id(), commitment.version(), a, DailyNoteKind.COMMITMENT)).hasMessageContaining("mudou");
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (var pair : List.of(Map.entry("occurrences", commitment.id()), Map.entry("commitments", occurrence.id()))) {
            var url = "/api/v1/agenda/" + pair.getKey() + "/" + pair.getValue();
            mvc.perform(patch(url).with(user(b)).with(csrf()).contentType("application/json")
                    .content("{\"date\":\"2031-10-25\",\"body\":\"Negado\",\"version\":0}")).andExpect(status().isNotFound());
            mvc.perform(delete(url).param("version", "0").with(user(b)).with(csrf())).andExpect(status().isNotFound());
        }
        notes.delete(commitment.id(), changed.version(), a, DailyNoteKind.COMMITMENT);
        assertThat(notes.list(day, day.plusDays(1), b, DailyNoteKind.COMMITMENT)).isEmpty();
        assertThat(notes.list(day, day.plusDays(1), b)).extracting(AgendaOccurrenceView::id).containsExactly(occurrence.id());
    }
    @Test void httpValidatesCsrfPayloadRolesAndCurrentAccountState() throws Exception {
        var manager = actor(Role.MANAGER); var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var url = "/api/v1/agenda/commitments";
        for (var denied : List.of(actor(Role.ADMIN), actor(Role.REQUESTER), actor(Role.AGENT))) {
            mvc.perform(get(url).param("start", "2031-10-01").param("end", "2031-11-01").with(user(denied))).andExpect(status().isForbidden());
            mvc.perform(post(url).with(user(denied)).with(csrf()).contentType("application/json")
                    .content("{\"date\":\"2031-10-25\",\"body\":\"Negado\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(post(url).with(user(manager)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(manager)).with(csrf()).contentType("application/json").content("{\"body\":\"Sem data\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(user(manager)).with(csrf()).contentType("application/json")
                .content("{\"date\":\"2031-10-25\",\"body\":\"Compromisso HTTP\",\"collaboratorId\":\""+manager.id()+"\"}")).andExpect(status().isCreated());
        assertThatThrownBy(() -> notes.create(LocalDate.now(), "x".repeat(4001), manager, DailyNoteKind.COMMITMENT)).isInstanceOf(DomainException.class);
        identity.update(manager.id(), manager.displayName(), Set.of(Role.REQUESTER), true, manager.id());
        assertThatThrownBy(() -> notes.list(LocalDate.now(), LocalDate.now().plusDays(1), manager, DailyNoteKind.COMMITMENT)).isInstanceOf(DomainException.class);
        identity.update(manager.id(), manager.displayName(), Set.of(Role.MANAGER), false, manager.id());
        assertThatThrownBy(() -> notes.create(LocalDate.now(), "Negado", manager, DailyNoteKind.COMMITMENT)).isInstanceOf(DomainException.class);
    }
    private AuthenticatedUser actor(Role role) {
        var u = identity.create(UUID.randomUUID() + "@example.test", "Pessoa " + UUID.randomUUID(), Set.of(role), null).user();
        identity.changePassword(u.id(), "permanent-password-123");
        return new AuthenticatedUser(u.id(), u.email(), u.displayName(), "unused", u.roles(), true, false);
    }
}
