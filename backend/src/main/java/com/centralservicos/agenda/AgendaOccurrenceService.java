package com.centralservicos.agenda;

import com.centralservicos.audit.AuditService;
import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.identity.IdentityService;
import com.centralservicos.identity.Role;
import com.centralservicos.shared.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgendaOccurrenceService {
    private final AgendaOccurrenceRepository occurrences;
    private final IdentityService identity;
    private final AuditService audit;

    AgendaOccurrenceService(AgendaOccurrenceRepository occurrences, IdentityService identity, AuditService audit) {
        this.occurrences = occurrences;
        this.identity = identity;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AgendaOccurrenceView> list(LocalDate start, LocalDate end, AuthenticatedUser actor) {
        assertManager(actor);
        if (start == null || end == null || !end.isAfter(start)) {
            throw DomainException.unprocessable("Informe um período válido.");
        }
        var rows = occurrences.findInPeriod(start, end);
        var names = identity.displayNames(rows.stream().map(AgendaOccurrence::createdById).distinct().toList());
        return rows.stream().map(row -> view(row, names)).toList();
    }

    @Transactional
    public AgendaOccurrenceView create(LocalDate date, String body, AuthenticatedUser actor) {
        assertManager(actor);
        validate(date, body);
        var row = occurrences.saveAndFlush(new AgendaOccurrence(date, body.trim(), actor.id()));
        audit.record(actor.id(), "AGENDA_OCCURRENCE_CREATED", "AgendaOccurrence", row.id(), null);
        return view(row);
    }

    @Transactional
    public AgendaOccurrenceView update(UUID id, LocalDate date, String body, long version, AuthenticatedUser actor) {
        assertManager(actor);
        var row = required(id, version);
        validate(date, body);
        row.update(date, body.trim());
        occurrences.flush();
        audit.record(actor.id(), "AGENDA_OCCURRENCE_UPDATED", "AgendaOccurrence", id, null);
        return view(row);
    }

    @Transactional
    public void delete(UUID id, long version, AuthenticatedUser actor) {
        assertManager(actor);
        occurrences.delete(required(id, version));
        occurrences.flush();
        audit.record(actor.id(), "AGENDA_OCCURRENCE_DELETED", "AgendaOccurrence", id, null);
    }

    private AgendaOccurrence required(UUID id, long version) {
        var row = occurrences.findById(id).orElseThrow(() -> DomainException.notFound("Ocorrência não encontrada."));
        if (row.version() != version) {
            throw DomainException.conflict("Esta ocorrência mudou. Feche a edição e abra novamente para revisar a versão atual. Seu texto não foi salvo.");
        }
        return row;
    }

    private void assertManager(AuthenticatedUser actor) {
        if (!actor.roles().contains(Role.MANAGER)) {
            throw DomainException.forbidden("As ocorrências exigem perfil Administrativo.");
        }
    }

    private void validate(LocalDate date, String body) {
        if (date == null || body == null || body.isBlank() || body.length() > 4000) {
            throw DomainException.unprocessable("Informe a data e uma anotação com até 4000 caracteres.");
        }
    }

    private AgendaOccurrenceView view(AgendaOccurrence row) {
        return view(row, identity.displayNames(List.of(row.createdById())));
    }

    private AgendaOccurrenceView view(AgendaOccurrence row, Map<UUID, String> names) {
        return new AgendaOccurrenceView(row.id(), row.occurrenceDate(), row.body(), row.createdById(),
                names.getOrDefault(row.createdById(), "Administrativo"), row.version(), row.createdAt(), row.updatedAt());
    }
}
