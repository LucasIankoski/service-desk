package com.centralservicos.agenda;

import com.centralservicos.audit.AuditService;
import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.identity.IdentityService;
import com.centralservicos.identity.Role;
import com.centralservicos.settings.SettingsService;
import com.centralservicos.shared.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AgendaService {

    private final AgendaItemRepository items;
    private final IdentityService identity;
    private final SettingsService settings;
    private final AuditService audit;

    AgendaService(AgendaItemRepository items, IdentityService identity, SettingsService settings,
                  AuditService audit) {
        this.items = items;
        this.identity = identity;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AgendaItemView> list(Instant rangeStart, Instant rangeEnd, AuthenticatedUser actor) {
        assertCanAccess(actor);
        validateRange(rangeStart, rangeEnd);
        var visible = isManager(actor)
                ? items.findOverlapping(rangeStart, rangeEnd)
                : items.findOverlappingByKind(AgendaItemKind.INSTITUTION_EVENT, rangeStart, rangeEnd);
        var assigneeIds = new HashSet<UUID>();
        visible.forEach(item -> {
            if (item.assigneeId() != null) assigneeIds.add(item.assigneeId());
        });
        var names = identity.displayNames(assigneeIds);
        return visible.stream().map(item -> toView(item, names)).toList();
    }

    @Transactional
    public AgendaItemView create(AgendaItemKind kind, String title, String description, String location,
                                 UUID assigneeId, Instant startAt, Instant endAt, boolean allDay,
                                 AuthenticatedUser actor) {
        assertManager(actor);
        validate(kind, title, description, location, assigneeId, startAt, endAt, allDay);
        var item = items.saveAndFlush(new AgendaItem(kind, title.trim(), optional(description),
                optional(location), assigneeId, startAt, endAt, allDay, actor.id()));
        audit.record(actor.id(), "AGENDA_ITEM_CREATED", "AgendaItem", item.id(),
                "{\"kind\":\"" + kind + "\"}");
        return toView(item);
    }

    @Transactional
    public AgendaItemView update(UUID id, String title, String description, String location, UUID assigneeId,
                                 Instant startAt, Instant endAt, boolean allDay, long version,
                                 AuthenticatedUser actor) {
        assertManager(actor);
        var item = required(id);
        assertVersion(item, version);
        validate(item.kindName(), title, description, location, assigneeId, startAt, endAt, allDay);
        item.update(title.trim(), optional(description), optional(location), assigneeId, startAt, endAt, allDay);
        items.flush();
        audit.record(actor.id(), "AGENDA_ITEM_UPDATED", "AgendaItem", id,
                "{\"kind\":\"" + item.kindName() + "\"}");
        return toView(item);
    }

    @Transactional
    public AgendaItemView changeStatus(UUID id, AgendaItemStatus status, long version,
                                       AuthenticatedUser actor) {
        assertManager(actor);
        var item = required(id);
        assertVersion(item, version);
        if (item.kindName() != AgendaItemKind.INTERNAL_DEMAND) {
            throw DomainException.unprocessable("Somente demandas internas possuem andamento.");
        }
        if (status == null) {
            throw DomainException.unprocessable("Informe o andamento da demanda.");
        }
        if (item.statusName() == status) {
            throw DomainException.unprocessable("A demanda já está neste andamento.");
        }
        item.changeStatus(status);
        items.flush();
        audit.record(actor.id(), status == AgendaItemStatus.COMPLETED
                        ? "AGENDA_DEMAND_COMPLETED" : "AGENDA_DEMAND_REOPENED",
                "AgendaItem", id, null);
        return toView(item);
    }

    @Transactional
    public void delete(UUID id, long version, AuthenticatedUser actor) {
        assertManager(actor);
        var item = required(id);
        assertVersion(item, version);
        var kind = item.kindName();
        items.delete(item);
        items.flush();
        audit.record(actor.id(), "AGENDA_ITEM_DELETED", "AgendaItem", id,
                "{\"kind\":\"" + kind + "\"}");
    }

    private AgendaItem required(UUID id) {
        return items.findById(id).orElseThrow(() -> DomainException.notFound("Item da agenda não encontrado."));
    }

    private void validate(AgendaItemKind kind, String title, String description, String location,
                          UUID assigneeId, Instant startAt, Instant endAt, boolean allDay) {
        if (kind == null) {
            throw DomainException.unprocessable("Informe o tipo do item.");
        }
        if (title == null || title.isBlank() || title.length() > 160) {
            throw DomainException.unprocessable("Informe um título com até 160 caracteres.");
        }
        if (description != null && description.length() > 4000) {
            throw DomainException.unprocessable("A descrição deve ter até 4000 caracteres.");
        }
        if (location != null && location.length() > 200) {
            throw DomainException.unprocessable("O local deve ter até 200 caracteres.");
        }
        validateRange(startAt, endAt);
        if (allDay) {
            var zone = settings.ticketPolicy().zoneId();
            if (!startAt.atZone(zone).toLocalTime().equals(LocalTime.MIDNIGHT)
                    || !endAt.atZone(zone).toLocalTime().equals(LocalTime.MIDNIGHT)) {
                throw DomainException.unprocessable("Itens de dia inteiro devem começar e terminar à meia-noite no fuso da instituição.");
            }
        }
        if (kind == AgendaItemKind.INSTITUTION_EVENT) {
            if (assigneeId != null) {
                throw DomainException.unprocessable("Eventos institucionais não possuem responsável.");
            }
        } else {
            if (location != null && !location.isBlank()) {
                throw DomainException.unprocessable("Demandas internas não possuem local.");
            }
            if (assigneeId != null && !identity.activeUserHasAnyRole(assigneeId, Set.of(Role.MANAGER))) {
                throw DomainException.unprocessable("O responsável deve ser um Administrativo ativo.");
            }
        }
    }

    private void validateRange(Instant rangeStart, Instant rangeEnd) {
        if (rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
            throw DomainException.unprocessable("Informe um período válido.");
        }
    }

    private void assertCanAccess(AuthenticatedUser actor) {
        if (!isManager(actor) && !actor.roles().contains(Role.REQUESTER)) {
            throw DomainException.forbidden("A Agenda exige perfil Solicitante ou Administrativo.");
        }
    }

    private void assertManager(AuthenticatedUser actor) {
        if (!isManager(actor)) {
            throw DomainException.forbidden("A gestão da Agenda exige perfil Administrativo.");
        }
    }

    private boolean isManager(AuthenticatedUser actor) {
        return actor.roles().contains(Role.MANAGER);
    }

    private void assertVersion(AgendaItem item, long expected) {
        if (item.rowVersion() != null && !Objects.equals(item.rowVersion(), expected)) {
            throw DomainException.conflict("Este item da agenda mudou. Recarregue antes de salvar.");
        }
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AgendaItemView toView(AgendaItem item) {
        var names = item.assigneeId() == null
                ? Map.<UUID, String>of()
                : identity.displayNames(List.of(item.assigneeId()));
        return toView(item, names);
    }

    private AgendaItemView toView(AgendaItem item, Map<UUID, String> names) {
        var assigneeName = item.assigneeId() == null ? null : names.get(item.assigneeId());
        return new AgendaItemView(item.id(), item.kindName(), item.title(), item.description(), item.location(),
                item.assigneeId(), assigneeName, item.statusName(), item.startAt(), item.endAt(),
                item.allDay(), item.rowVersion() == null ? 0 : item.rowVersion(), item.createdAt(), item.updatedAt());
    }
}
