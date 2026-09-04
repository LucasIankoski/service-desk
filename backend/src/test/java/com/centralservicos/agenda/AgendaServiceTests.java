package com.centralservicos.agenda;

import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.identity.IdentityService;
import com.centralservicos.identity.Role;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AgendaServiceTests {

    private static final ZoneId INSTITUTION_ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired AgendaService agenda;
    @Autowired IdentityService identity;

    @Test
    void managerCreatesSharedItemsAndRequesterSeesOnlyInstitutionEvents() {
        var manager = user("agenda-manager", Role.MANAGER);
        var requester = user("agenda-requester", Role.REQUESTER);
        var start = Instant.parse("2026-09-10T12:00:00Z");
        var end = Instant.parse("2026-09-10T13:00:00Z");

        var event = agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Reunião de pais", "Auditório principal",
                "Auditório", null, start, end, false, manager);
        var demand = agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Preparar documentos", null,
                null, manager.id(), start, end, false, manager);

        assertThat(event.status()).isNull();
        assertThat(demand.status()).isEqualTo(AgendaItemStatus.PENDING);
        assertThat(demand.assigneeId()).isEqualTo(manager.id());
        assertThat(agenda.list(start.minusSeconds(60), end.plusSeconds(60), manager))
                .extracting(AgendaItemView::id).contains(event.id(), demand.id());
        assertThat(agenda.list(start.minusSeconds(60), end.plusSeconds(60), requester))
                .extracting(AgendaItemView::id).containsExactly(event.id());
    }

    @Test
    void demandAcceptsOnlyAnActiveManagerAsAssignee() {
        var manager = user("assigning-manager", Role.MANAGER);
        var agent = user("invalid-assignee", Role.AGENT);

        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Revisar ofício", null,
                null, agent.id(), Instant.parse("2026-09-11T12:00:00Z"),
                Instant.parse("2026-09-11T13:00:00Z"), false, manager))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Administrativo ativo");
    }

    @Test
    void managerCompletesReopensUpdatesAndDeletesDemandWithVersionChecks() {
        var manager = user("workflow-manager", Role.MANAGER);
        var start = Instant.parse("2026-09-12T12:00:00Z");
        var end = Instant.parse("2026-09-12T13:00:00Z");
        var created = agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Conferir contratos", null,
                null, null, start, end, false, manager);

        var completed = agenda.changeStatus(created.id(), AgendaItemStatus.COMPLETED, created.version(), manager);
        var reopened = agenda.changeStatus(completed.id(), AgendaItemStatus.PENDING, completed.version(), manager);
        var updated = agenda.update(reopened.id(), "Conferir contratos assinados", "Conferência final",
                null, manager.id(), start, end.plusSeconds(3600), false, reopened.version(), manager);

        assertThat(updated.title()).isEqualTo("Conferir contratos assinados");
        assertThat(updated.assigneeName()).isEqualTo(manager.displayName());
        assertThatThrownBy(() -> agenda.update(updated.id(), "Versão antiga", null, null, null,
                start, end, false, created.version(), manager))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("mudou");

        agenda.delete(updated.id(), updated.version(), manager);
        assertThat(agenda.list(start.minusSeconds(1), end.plusSeconds(7200), manager)).isEmpty();
    }

    @Test
    void requesterAndUnrelatedRolesCannotManageOrAccessInternalAgenda() {
        var manager = user("permission-manager", Role.MANAGER);
        var requester = user("permission-requester", Role.REQUESTER);
        var agent = user("permission-agent", Role.AGENT);
        var admin = user("permission-admin", Role.ADMIN);
        var start = Instant.parse("2026-09-13T12:00:00Z");
        var end = Instant.parse("2026-09-13T13:00:00Z");

        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Evento", null,
                null, null, start, end, false, requester)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> agenda.list(start, end, agent)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> agenda.list(start, end, admin)).isInstanceOf(DomainException.class);

        var combined = user("permission-combined", Set.of(Role.REQUESTER, Role.MANAGER));
        var demand = agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Demanda compartilhada", null,
                null, null, start, end, false, manager);
        assertThat(agenda.list(start.minusSeconds(1), end.plusSeconds(1), combined))
                .extracting(AgendaItemView::id).contains(demand.id());
    }

    @Test
    void allDayItemsMustUseInstitutionDayBoundaries() {
        var manager = user("all-day-manager", Role.MANAGER);
        var start = LocalDate.of(2026, 9, 14).atStartOfDay(INSTITUTION_ZONE).toInstant();
        var end = LocalDate.of(2026, 9, 16).atStartOfDay(INSTITUTION_ZONE).toInstant();

        var item = agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Semana pedagógica", null,
                "Escola", null, start, end, true, manager);
        assertThat(item.allDay()).isTrue();

        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Dia inválido", null,
                null, null, start.plusSeconds(3600), end, true, manager))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("meia-noite");
    }

    @Test
    void eventCannotReceiveDemandFieldsOrStatus() {
        var manager = user("event-invariants", Role.MANAGER);
        var start = Instant.parse("2026-09-15T12:00:00Z");
        var end = Instant.parse("2026-09-15T13:00:00Z");
        var event = agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Conselho", null,
                "Sala 2", null, start, end, false, manager);

        assertThatThrownBy(() -> agenda.changeStatus(event.id(), AgendaItemStatus.COMPLETED,
                event.version(), manager))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Somente demandas");
        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Demanda com local", null,
                "Sala 2", null, start, end, false, manager))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("não possuem local");
    }

    private AuthenticatedUser user(String prefix, Role role) {
        return user(prefix, Set.of(role));
    }

    private AuthenticatedUser user(String prefix, Set<Role> roles) {
        var email = prefix + "-" + UUID.randomUUID() + "@example.test";
        var created = identity.create(email, prefix, roles, null).user();
        return new AuthenticatedUser(created.id(), created.email(), created.displayName(),
                "{noop}not-used", created.roles(), true, false);
    }
}
