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
        return list(start, end, actor, DailyNoteKind.OCCURRENCE);
    }

    @Transactional(readOnly = true)
    public List<AgendaOccurrenceView> list(LocalDate start, LocalDate end, AuthenticatedUser actor, DailyNoteKind kind) {
        assertManager(actor);
        if (start == null || end == null || !end.isAfter(start)) {
            throw DomainException.unprocessable("Informe um período válido.");
        }
        var rows = occurrences.findInPeriod(start, end, kind);
        var names = identity.displayNames(rows.stream().flatMap(r -> java.util.stream.Stream.of(r.createdById(), r.collaboratorId())).filter(java.util.Objects::nonNull).distinct().toList());
        return rows.stream().map(row -> view(row, names)).toList();
    }

    @Transactional
    public AgendaOccurrenceView create(LocalDate date, String body, AuthenticatedUser actor) {
        return create(date, body, actor, DailyNoteKind.OCCURRENCE);
    }

    @Transactional
    public AgendaOccurrenceView create(LocalDate date, String body, AuthenticatedUser actor, DailyNoteKind kind) {
        return create(date, body, actor, kind, null);
    }

    @Transactional
    public AgendaOccurrenceView create(LocalDate date, String body, AuthenticatedUser actor, DailyNoteKind kind, UUID collaboratorId) {
        assertManager(actor);
        validate(date, body);
        validateCollaborator(kind, collaboratorId, null);
        var row = new AgendaOccurrence(date, body.trim(), actor.id(), kind);
        row.collaborator(kind == DailyNoteKind.COMMITMENT ? collaboratorId : null);
        occurrences.saveAndFlush(row);
        audit.record(actor.id(), "AGENDA_" + kind + "_CREATED", "AgendaOccurrence", row.id(), null);
        return view(row);
    }

    @Transactional
    public AgendaOccurrenceView update(UUID id, LocalDate date, String body, long version, AuthenticatedUser actor) {
        return update(id, date, body, version, actor, DailyNoteKind.OCCURRENCE);
    }

    @Transactional
    public AgendaOccurrenceView update(UUID id, LocalDate date, String body, long version, AuthenticatedUser actor, DailyNoteKind kind) {
        return update(id, date, body, version, actor, kind, null);
    }

    @Transactional
    public AgendaOccurrenceView update(UUID id, LocalDate date, String body, long version, AuthenticatedUser actor, DailyNoteKind kind, UUID collaboratorId) {
        assertManager(actor);
        var row = required(id, version, kind);
        validate(date, body);
        validateCollaborator(kind, collaboratorId, row.collaboratorId());
        row.collaborator(kind == DailyNoteKind.COMMITMENT ? collaboratorId : null);
        row.update(date, body.trim());
        occurrences.flush();
        audit.record(actor.id(), "AGENDA_" + kind + "_UPDATED", "AgendaOccurrence", id, null);
        return view(row);
    }

    @Transactional
    public void delete(UUID id, long version, AuthenticatedUser actor) {
        delete(id, version, actor, DailyNoteKind.OCCURRENCE);
    }

    @Transactional
    public void delete(UUID id, long version, AuthenticatedUser actor, DailyNoteKind kind) {
        assertManager(actor);
        occurrences.delete(required(id, version, kind));
        occurrences.flush();
        audit.record(actor.id(), "AGENDA_" + kind + "_DELETED", "AgendaOccurrence", id, null);
    }

    private AgendaOccurrence required(UUID id, long version, DailyNoteKind kind) {
        var label = kind == DailyNoteKind.COMMITMENT ? "Compromisso" : "Ocorrência";
        var row = occurrences.findById(id).filter(r -> r.kind() == kind)
                .orElseThrow(() -> DomainException.notFound(label + " não encontrado(a)."));
        if (row.version() != version) {
            throw DomainException.conflict(label + " mudou. Feche a edição e abra novamente para revisar a versão atual. Seu texto não foi salvo.");
        }
        return row;
    }

    private void assertManager(AuthenticatedUser actor) {
        if (!identity.activeUserHasAnyRole(actor.id(), java.util.Set.of(Role.MANAGER))) {
            throw DomainException.forbidden("As anotações do dia exigem perfil Administrativo ativo.");
        }
    }

    @Transactional(readOnly = true)
    public List<IdentityService.CollaboratorView> collaborators(AuthenticatedUser actor) {
        assertManager(actor);
        return identity.collaborators();
    }

    private void validateCollaborator(DailyNoteKind kind, UUID id, UUID previous) {
        if (kind != DailyNoteKind.COMMITMENT) return;
        if (id == null) throw DomainException.unprocessable("Selecione o colaborador do compromisso.");
        if (!id.equals(previous) && !identity.registeredCollaborator(id)) throw DomainException.unprocessable("Selecione um usuário cadastrado disponível.");
    }

    private void validate(LocalDate date, String body) {
        if (date == null || body == null || body.isBlank() || body.length() > 4000) {
            throw DomainException.unprocessable("Informe a data e uma anotação com até 4000 caracteres.");
        }
    }

    private AgendaOccurrenceView view(AgendaOccurrence row) {
        return view(row, identity.displayNames(java.util.stream.Stream.of(row.createdById(), row.collaboratorId()).filter(java.util.Objects::nonNull).toList()));
    }

    private AgendaOccurrenceView view(AgendaOccurrence row, Map<UUID, String> names) {
        return new AgendaOccurrenceView(row.id(), row.occurrenceDate(), row.body(), row.createdById(),
                names.getOrDefault(row.createdById(), "Administrativo"), row.version(), row.createdAt(), row.updatedAt(),
                row.collaboratorId(), row.collaboratorId() == null ? null : names.getOrDefault(row.collaboratorId(), "Colaborador indisponível"));
    }
}
