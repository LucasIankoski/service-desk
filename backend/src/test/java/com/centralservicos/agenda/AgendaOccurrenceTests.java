package com.centralservicos.agenda;

import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.identity.CurrentUser;
import com.centralservicos.identity.IdentityService;
import com.centralservicos.identity.Role;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class AgendaOccurrenceTests {
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired AgendaOccurrenceService occurrences;
    @Autowired IdentityService identity;

    @Test
    void dailyNotesAreIndependentSharedVersionedAndUseExclusiveMonthEnd() {
        var author = user(Role.MANAGER);
        var editor = user(Role.MANAGER);
        var date = LocalDate.of(2029, 1, 31);
        var first = occurrences.create(date, "  First note\nSecond line  ", author);
        var second = occurrences.create(date, "Another event", editor);
        occurrences.create(date.plusDays(1), "Next month", author);
        assertThat(occurrences.list(date.withDayOfMonth(1), date.plusDays(1), author))
                .extracting(AgendaOccurrenceView::id).containsExactly(first.id(), second.id());
        assertThat(first.body()).isEqualTo("First note\nSecond line");
        var changed = occurrences.update(first.id(), date, "Edited by colleague", first.version(), editor);
        assertThat(changed.createdById()).isEqualTo(author.id());
        assertThat(changed.version()).isGreaterThan(first.version());
        assertThatThrownBy(() -> occurrences.update(first.id(), date, "Stale", first.version(), author))
                .isInstanceOf(DomainException.class).hasMessageContaining("mudou");
        assertThatThrownBy(() -> occurrences.delete(first.id(), first.version(), author)).isInstanceOf(DomainException.class);
        occurrences.delete(first.id(), changed.version(), editor);
        assertThat(occurrences.list(date, date.plusDays(1), author)).extracting(AgendaOccurrenceView::id).containsExactly(second.id());
    }

    @Test
    void allOperationsRequireManagerAndNotesCannotBeBlank() {
        var manager = user(Role.MANAGER);
        var date = LocalDate.of(2029, 2, 1);
        var note = occurrences.create(date, "Internal", manager);
        for (var role : Set.of(Role.REQUESTER, Role.AGENT, Role.ADMIN)) {
            var actor = user(role);
            assertThatThrownBy(() -> occurrences.list(date, date.plusDays(1), actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> occurrences.create(date, "Denied", actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> occurrences.update(note.id(), date, "Denied", note.version(), actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> occurrences.delete(note.id(), note.version(), actor)).isInstanceOf(DomainException.class);
        }
        assertThatThrownBy(() -> occurrences.create(date, " \n ", manager)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> occurrences.create(date, "x".repeat(4001), manager)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> occurrences.list(date, date, manager)).isInstanceOf(DomainException.class);
    }

    @Test
    void httpContractValidatesDateVersionAndAuthorization() throws Exception {
        var current = mock(CurrentUser.class);
        when(current.required()).thenReturn(user(Role.MANAGER));
        var mvc = MockMvcBuilders.standaloneSetup(new AgendaOccurrenceController(occurrences, current))
                .setControllerAdvice(context.getBean("apiExceptionHandler")).build();
        mvc.perform(post("/api/v1/agenda/occurrences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2029-03-01\",\"body\":\"Meeting notes\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.date").value("2029-03-01"))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(post("/api/v1/agenda/occurrences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Missing date\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/agenda/occurrences/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2029-03-01\",\"body\":\"Missing version\"}"))
                .andExpect(status().isBadRequest());
        when(current.required()).thenReturn(user(Role.REQUESTER));
        mvc.perform(get("/api/v1/agenda/occurrences").param("start", "2029-03-01").param("end", "2029-04-01"))
                .andExpect(status().isForbidden());
    }

    private AuthenticatedUser user(Role role) {
        var user = identity.create(UUID.randomUUID() + "@example.test", "Occurrence test", Set.of(role), null).user();
        return new AuthenticatedUser(user.id(), user.email(), user.displayName(), "{noop}unused", user.roles(), true, false);
    }
}
