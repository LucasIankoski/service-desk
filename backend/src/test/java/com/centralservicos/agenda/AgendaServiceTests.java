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
import java.util.List;
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
    void shiftsPersistAcrossDatesAndRespectPermissionsAndVersion() {
        var manager = user("shift-manager", Role.MANAGER);
        var requester = user("shift-requester", Role.REQUESTER);
        for (var shift : AgendaShift.values()) {
            var start = LocalDate.of(2027, 1, 30).atTime(shift.start()).atZone(INSTITUTION_ZONE).toInstant();
            var endDate = LocalDate.of(2027, 2, 2).plusDays(shift == AgendaShift.NIGHT ? 1 : 0);
            var end = endDate.atTime(shift.end()).atZone(INSTITUTION_ZONE).toInstant();
            var created = agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Turno", null, null, null,
                    start, end, false, null, List.of(), shift, manager);
            assertThat(created.shift()).isEqualTo(shift);
            assertThat(agenda.list(start, end, manager)).filteredOn(i -> i.id().equals(created.id()))
                    .extracting(AgendaItemView::shift).containsExactly(shift);
            assertThat(agenda.list(start, end, requester)).extracting(AgendaItemView::id).doesNotContain(created.id());
            assertThatThrownBy(() -> agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Turno", null, null, null,
                    start, end, false, null, List.of(), shift, requester)).isInstanceOf(DomainException.class);
            var completed = agenda.changeStatus(created.id(), AgendaItemStatus.COMPLETED, created.version(), manager);
            assertThat(completed.shift()).isEqualTo(shift);
            assertThatThrownBy(() -> agenda.update(created.id(), "Antiga", null, null, null,
                    start, end, false, created.version(), null, List.of(), shift, manager))
                    .isInstanceOf(DomainException.class).hasMessageContaining("mudou");
            var custom = agenda.update(created.id(), "Personalizado", null, null, null, start, end,
                    false, completed.version(), null, List.of(), null, manager);
            assertThat(custom.shift()).isNull();
            assertThat(custom.startAt()).isEqualTo(start);
            agenda.delete(custom.id(), custom.version(), manager);
        }
    }

    @Test
    void rejectsShiftsOutsideTheirHoursOrOnEventsAndAllDayItems() {
        var manager = user("invalid-shift-manager", Role.MANAGER);
        var start = Instant.parse("2027-04-01T09:00:00Z");
        var end = Instant.parse("2027-04-01T15:00:00Z");
        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Evento", null, null, null,
                start, end, false, null, List.of(), AgendaShift.MORNING, manager)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Dia", null, null, null,
                Instant.parse("2027-04-01T03:00:00Z"), Instant.parse("2027-04-02T03:00:00Z"),
                true, null, List.of(), AgendaShift.MORNING, manager)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Hora", null, null, null,
                start.plusSeconds(60), end, false, null, List.of(), AgendaShift.MORNING, manager))
                .isInstanceOf(DomainException.class).hasMessageContaining("horários do turno");
    }

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

    @Test
    void prioritiesDefaultAndSurviveLegacyUpdates() {
        var manager = user("priority-manager", Role.MANAGER);
        var start = Instant.parse("2027-01-31T12:00:00Z");
        var end = Instant.parse("2027-02-02T13:00:00Z");
        var created = agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Across months", null,
                null, null, start, end, false, manager);
        assertThat(created.priority()).isEqualTo(AgendaItemPriority.MEDIUM);
        var high = agenda.update(created.id(), created.title(), null, null, null, start, end,
                false, created.version(), AgendaItemPriority.HIGH, manager);
        var legacy = agenda.update(high.id(), high.title(), "Legacy edit", null, null, start, end,
                false, high.version(), manager);
        assertThat(legacy.priority()).isEqualTo(AgendaItemPriority.HIGH);
        assertThat(agenda.list(Instant.parse("2027-02-01T03:00:00Z"),
                Instant.parse("2027-03-01T03:00:00Z"), manager))
                .extracting(AgendaItemView::id).containsExactly(created.id());
        var completed = agenda.changeStatus(legacy.id(), AgendaItemStatus.COMPLETED, legacy.version(), manager);
        assertThat(completed.priority()).isEqualTo(AgendaItemPriority.HIGH);
        assertThatThrownBy(() -> agenda.update(completed.id(), "Stale", null, null, null, start, end,
                false, created.version(), AgendaItemPriority.LOW, manager)).isInstanceOf(DomainException.class);
    }

    @Test
    void eventsRejectPriorityAndNonManagersCannotMutateDemand() {
        var manager = user("priority-access", Role.MANAGER);
        var start = Instant.parse("2027-03-10T12:00:00Z");
        var end = start.plusSeconds(3600);
        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Event", null,
                null, null, start, end, false, AgendaItemPriority.HIGH, manager))
                .isInstanceOf(DomainException.class).hasMessageContaining("prioridade");
        var event = agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Event", null,
                null, null, start, end, false, manager);
        assertThat(event.priority()).isNull();
        assertThatThrownBy(() -> agenda.update(event.id(), "Event", null, null, null, start, end,
                false, event.version(), AgendaItemPriority.LOW, manager)).isInstanceOf(DomainException.class);
        var demand = agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Private", null,
                null, null, start, end, false, AgendaItemPriority.LOW, manager);
        for (var role : Set.of(Role.REQUESTER, Role.AGENT, Role.ADMIN)) {
            var actor = user("priority-denied", role);
            assertThatThrownBy(() -> agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Denied", null,
                    null, null, start, end, false, AgendaItemPriority.HIGH, actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> agenda.update(demand.id(), "Denied", null, null, null, start, end,
                    false, demand.version(), AgendaItemPriority.HIGH, actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> agenda.changeStatus(demand.id(), AgendaItemStatus.COMPLETED,
                    demand.version(), actor)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> agenda.delete(demand.id(), demand.version(), actor)).isInstanceOf(DomainException.class);
        }
    }

    @Test
    void multipleAssigneesAreSharedAndLegacyEditsPreserveThem() {
        var actor = user("multiple", Role.MANAGER);
        var second = user("second", Role.MANAGER);
        var start = Instant.parse("2028-01-10T12:00:00Z");
        var end = start.plusSeconds(3600);
        var created = agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Shared task", null, null, null,
                start, end, false, AgendaItemPriority.HIGH, List.of(actor.id(), second.id(), actor.id()), actor);
        assertThat(created.assignees()).extracting(AgendaItemView.AssigneeView::id).containsExactly(actor.id(), second.id());
        var legacy = agenda.update(created.id(), created.title(), null, null, actor.id(), start, end, false,
                created.version(), actor);
        assertThat(legacy.assignees()).hasSize(2);
        var swapped = agenda.update(legacy.id(), legacy.title(), null, null, null, start, end, false,
                legacy.version(), null, List.of(second.id(), actor.id()), second);
        assertThat(swapped.assigneeId()).isEqualTo(second.id());
        assertThat(agenda.list(start, end, actor)).filteredOn(item -> item.id().equals(created.id()))
                .singleElement().satisfies(item -> assertThat(item.assignees()).hasSize(2));
        assertThatThrownBy(() -> agenda.update(swapped.id(), swapped.title(), null, null, null, start, end, false,
                created.version(), null, List.of(), actor)).isInstanceOf(DomainException.class);
        var cleared = agenda.update(swapped.id(), swapped.title(), null, null, null, start, end, false,
                swapped.version(), null, List.of(), actor);
        assertThat(cleared.assignees()).isEmpty();
        assertThat(cleared.assigneeId()).isNull();
        var reassigned = agenda.update(cleared.id(), cleared.title(), null, null, null, start, end, false,
                cleared.version(), null, List.of(actor.id(), second.id()), actor);
        agenda.delete(reassigned.id(), reassigned.version(), actor);
        assertThat(agenda.list(start, end, actor)).noneMatch(item -> item.id().equals(created.id()));
    }

    @Test
    void everyAssigneeMustBeAnActiveManagerAndEventsCannotHaveMultipleAssignees() {
        var manager = user("multiple-access", Role.MANAGER);
        var requester = user("multiple-requester", Role.REQUESTER);
        var start = Instant.parse("2028-02-01T12:00:00Z");
        var end = start.plusSeconds(3600);
        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INTERNAL_DEMAND, "Denied", null, null, null,
                start, end, false, null, List.of(manager.id(), requester.id()), manager))
                .isInstanceOf(DomainException.class).hasMessageContaining("Administrativo ativo");
        assertThatThrownBy(() -> agenda.create(AgendaItemKind.INSTITUTION_EVENT, "Denied", null, null, null,
                start, end, false, null, List.of(manager.id()), manager)).isInstanceOf(DomainException.class);
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
