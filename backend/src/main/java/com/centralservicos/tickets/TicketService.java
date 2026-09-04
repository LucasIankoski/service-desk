package com.centralservicos.tickets;

import com.centralservicos.attachments.AttachmentService;
import com.centralservicos.attachments.AttachmentView;
import com.centralservicos.attachments.StoredResource;
import com.centralservicos.audit.AuditService;
import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.identity.IdentityService;
import com.centralservicos.identity.Role;
import com.centralservicos.notifications.NotificationService;
import com.centralservicos.notifications.NotificationType;
import com.centralservicos.settings.SettingsService;
import com.centralservicos.shared.CommentVisibility;
import com.centralservicos.shared.DomainException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private static final Set<TicketStatus> TERMINAL_STATUSES = Set.of(TicketStatus.RESOLVED);
    private static final Pattern MENTION_PATTERN = Pattern.compile("<@([0-9a-fA-F-]{36})>");

    private final TicketRepository tickets;
    private final TicketCounterRepository counters;
    private final TicketCommentRepository comments;
    private final CategoryService categories;
    private final AttachmentService attachments;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final IdentityService identity;
    private final AuditService audit;

    TicketService(TicketRepository tickets, TicketCounterRepository counters, TicketCommentRepository comments,
                  CategoryService categories, AttachmentService attachments, SettingsService settings,
                  NotificationService notifications, IdentityService identity, AuditService audit) {
        this.tickets = tickets;
        this.counters = counters;
        this.comments = comments;
        this.categories = categories;
        this.attachments = attachments;
        this.settings = settings;
        this.notifications = notifications;
        this.identity = identity;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<TicketSummaryView> list(TicketFilter filter, Pageable pageable, AuthenticatedUser actor) {
        return tickets.findAll(specification(filter, actor), pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public TicketDetailView detail(UUID id, AuthenticatedUser actor) {
        var ticket = required(id);
        assertCanView(ticket, actor);
        return toDetail(ticket, actor);
    }

    @Transactional
    public TicketDetailView create(String subject, String description, UUID categoryId, List<MultipartFile> files,
                                   AuthenticatedUser actor) {
        validateText(subject, description);
        if (categoryId != null) {
            categories.requiredActive(categoryId);
        }
        var ticket = tickets.save(new Ticket(nextPublicNumber(), actor.id(), subject, description, categoryId));
        var policy = settings.ticketPolicy();
        attachments.saveTicketFiles(ticket.id(), null, actor.id(), CommentVisibility.PUBLIC, files,
                policy.attachmentLimitMb());
        audit.record(actor.id(), "TICKET_CREATED", "Ticket", ticket.id(), "{\"number\":\"" + ticket.publicNumber() + "\"}");
        notifications.notify(identity.activeUserIdsWithAnyRole(Set.of(Role.AGENT, Role.MANAGER)), ticket.id(),
                NotificationType.STATUS_CHANGED, "Nova solicitação " + ticket.publicNumber(), ticket.subject());
        return flushedDetail(ticket, actor);
    }

    @Transactional
    public TicketDetailView assign(UUID id, UUID assigneeId, long version, AuthenticatedUser actor) {
        var ticket = requiredForOperation(id, version, actor);
        if (!identity.activeUserHasAnyRole(assigneeId, Set.of(Role.AGENT, Role.MANAGER))) {
            throw DomainException.unprocessable("Responsável inválido para atendimento.");
        }
        if (!isManager(actor) && !assigneeId.equals(actor.id())) {
            throw DomainException.forbidden("Atendentes podem apenas assumir chamados para si.");
        }
        ticket.assign(assigneeId);
        audit.record(actor.id(), "TICKET_ASSIGNED", "Ticket", id, "{\"assigneeId\":\"" + assigneeId + "\"}");
        notifications.notify(assigneeId, ticket.id(), NotificationType.ASSIGNED,
                "Você foi atribuído a " + ticket.publicNumber(), ticket.subject());
        return flushedDetail(ticket, actor);
    }

    @Transactional
    public TicketDetailView classify(UUID id, UUID categoryId, Priority priority, Instant dueAt, long version,
                                     AuthenticatedUser actor) {
        var ticket = requiredForOperation(id, version, actor);
        categories.requiredActive(categoryId);
        ticket.classify(categoryId, priority, dueAt);
        audit.record(actor.id(), "TICKET_CLASSIFIED", "Ticket", id, null);
        return flushedDetail(ticket, actor);
    }

    @Transactional
    public TicketDetailView setPriority(UUID id, Priority priority, long version, AuthenticatedUser actor) {
        var ticket = requiredForOperation(id, version, actor);
        ticket.setPriority(priority);
        audit.record(actor.id(), "TICKET_PRIORITY_CHANGED", "Ticket", id, "{\"priority\":\"" + priority + "\"}");
        return flushedDetail(ticket, actor);
    }

    @Transactional
    public TicketDetailView setDueAt(UUID id, Instant dueAt, long version, AuthenticatedUser actor) {
        var ticket = requiredForOperation(id, version, actor);
        ticket.setDueAt(dueAt);
        audit.record(actor.id(), "TICKET_DEADLINE_CHANGED", "Ticket", id,
                dueAt == null ? null : "{\"dueAt\":\"" + dueAt + "\"}");
        return flushedDetail(ticket, actor);
    }

    @Transactional
    public TicketDetailView transition(UUID id, TicketStatus next, long version, AuthenticatedUser actor) {
        var ticket = required(id);
        assertVersion(ticket, version);
        assertCanTransition(ticket, next, actor);
        ticket.transition(next);
        audit.record(actor.id(), "TICKET_STATUS_CHANGED", "Ticket", id, "{\"status\":\"" + next + "\"}");
        notifyStatus(ticket, actor.id());
        return flushedDetail(ticket, actor);
    }

    @Transactional
    public TicketDetailView comment(UUID id, String body, CommentVisibility visibility, List<MultipartFile> files,
                                    AuthenticatedUser actor) {
        var ticket = required(id);
        assertCanComment(ticket, visibility, actor);
        if (body == null || body.isBlank() || body.length() > 4000) {
            throw DomainException.unprocessable("Informe um comentário com até 4000 caracteres.");
        }
        var comment = comments.save(new TicketComment(ticket.id(), actor.id(), body, visibility));
        ticket.touch();
        var policy = settings.ticketPolicy();
        attachments.saveTicketFiles(ticket.id(), comment.id(), actor.id(), visibility, files,
                policy.attachmentLimitMb());
        audit.record(actor.id(), "TICKET_COMMENTED", "Ticket", id, "{\"visibility\":\"" + visibility + "\"}");
        notifyComment(ticket, comment, actor);
        return toDetail(ticket, actor);
    }

    @Transactional(readOnly = true)
    public StoredResource download(UUID attachmentId, AuthenticatedUser actor) {
        var file = attachments.requiredFile(attachmentId);
        var ticket = required(file.ticketId());
        assertCanView(ticket, actor);
        if (file.visibility() == CommentVisibility.INTERNAL && !operatesTickets(actor)) {
            throw DomainException.forbidden("Anexo indisponível para este usuário.");
        }
        return attachments.loadStored(file.storedName(), file.mediaType(), file.originalName());
    }

    @Transactional
    public int processDeadlineNotifications() {
        var policy = settings.ticketPolicy();
        var now = Instant.now();
        var threshold = now.plusSeconds(policy.deadlineWarningHours() * 3600L);
        int count = 0;
        for (Ticket ticket : tickets.findDeadlineWarnings(now, threshold, TERMINAL_STATUSES)) {
            notifyDeadline(ticket, NotificationType.DEADLINE_SOON, "Prazo próximo em " + ticket.publicNumber());
            ticket.markDeadlineWarningSent();
            count++;
        }
        for (Ticket ticket : tickets.findOverdue(now, TERMINAL_STATUSES)) {
            notifyDeadline(ticket, NotificationType.OVERDUE, "Prazo vencido em " + ticket.publicNumber());
            ticket.markOverdueSent();
            count++;
        }
        return count;
    }

    private Ticket requiredForOperation(UUID id, long version, AuthenticatedUser actor) {
        var ticket = required(id);
        assertCanOperate(actor);
        assertVersion(ticket, version);
        if (ticket.terminal()) {
            throw DomainException.unprocessable("Chamados encerrados não podem ser alterados.");
        }
        return ticket;
    }

    private Ticket required(UUID id) {
        return tickets.findById(id).orElseThrow(() -> DomainException.notFound("Solicitação não encontrada."));
    }

    private TicketDetailView flushedDetail(Ticket ticket, AuthenticatedUser actor) {
        tickets.flush();
        return toDetail(ticket, actor);
    }

    private String nextPublicNumber() {
        var year = Year.now(settings.ticketPolicy().zoneId()).getValue();
        var counter = counters.lockByYear(year).orElseGet(() -> counters.saveAndFlush(new TicketCounter(year)));
        return "SD-%04d-%06d".formatted(year, counter.next());
    }

    private void validateText(String subject, String description) {
        if (subject == null || subject.isBlank() || subject.length() > 160) {
            throw DomainException.unprocessable("Informe um assunto com até 160 caracteres.");
        }
        if (description == null || description.isBlank() || description.length() > 8000) {
            throw DomainException.unprocessable("Informe uma descrição com até 8000 caracteres.");
        }
    }

    private void assertCanView(Ticket ticket, AuthenticatedUser actor) {
        if (operatesTickets(actor) || ticket.requesterId().equals(actor.id())) {
            return;
        }
        throw DomainException.forbidden("Solicitação indisponível para este usuário.");
    }

    private void assertCanOperate(AuthenticatedUser actor) {
        if (!operatesTickets(actor)) {
            throw DomainException.forbidden("Perfil operacional necessário.");
        }
    }

    private void assertCanComment(Ticket ticket, CommentVisibility visibility, AuthenticatedUser actor) {
        assertCanView(ticket, actor);
        if (ticket.terminal()) {
            throw DomainException.unprocessable("Chamados encerrados não recebem novos comentários.");
        }
        if (visibility == CommentVisibility.INTERNAL && !operatesTickets(actor)) {
            throw DomainException.forbidden("Notas internas exigem perfil operacional.");
        }
    }

    private void assertCanTransition(Ticket ticket, TicketStatus next, AuthenticatedUser actor) {
        if (next == null) {
            throw DomainException.unprocessable("Informe o próximo status.");
        }
        var current = ticket.statusName();
        if (current == next) {
            throw DomainException.unprocessable("A solicitação já está neste status.");
        }
        if (current == TicketStatus.RESOLVED && next == TicketStatus.IN_PROGRESS
                && ticket.requesterId().equals(actor.id())) {
            assertWithinReopenWindow(ticket);
            return;
        }
        if (ticket.terminal()) {
            throw DomainException.unprocessable("Chamados resolvidos não podem mudar de status.");
        }
        assertCanOperate(actor);
        if (next == TicketStatus.IN_PROGRESS && ticket.categoryId() == null) {
            throw DomainException.unprocessable("A categoria é obrigatória antes do atendimento.");
        }
        var allowed = switch (current) {
            case OPEN -> Set.of(TicketStatus.IN_PROGRESS);
            case IN_PROGRESS -> Set.of(TicketStatus.WAITING_REQUESTER, TicketStatus.RESOLVED);
            case WAITING_REQUESTER -> Set.of(TicketStatus.IN_PROGRESS);
            case RESOLVED -> Set.<TicketStatus>of();
        };
        if (!allowed.contains(next)) {
            throw DomainException.unprocessable("Transição de status inválida.");
        }
    }

    private void assertWithinReopenWindow(Ticket ticket) {
        var resolvedAt = ticket.resolvedAt();
        if (resolvedAt == null || resolvedAt.plusSeconds(settings.ticketPolicy().reopenDays() * 86400L).isBefore(Instant.now())) {
            throw DomainException.unprocessable("A janela de reabertura expirou.");
        }
    }

    private void assertVersion(Ticket ticket, long expected) {
        if (ticket.rowVersion() != null && ticket.rowVersion() != expected) {
            throw DomainException.conflict("Esta solicitação mudou. Recarregue antes de salvar.");
        }
    }

    private boolean operatesTickets(AuthenticatedUser actor) {
        return actor.roles().contains(Role.AGENT) || actor.roles().contains(Role.MANAGER);
    }

    private boolean isManager(AuthenticatedUser actor) {
        return actor.roles().contains(Role.MANAGER);
    }

    private Specification<Ticket> specification(TicketFilter filter, AuthenticatedUser actor) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (!operatesTickets(actor)) {
                predicates.add(cb.equal(root.get("requesterId"), actor.id()));
            }
            if (filter != null) {
                if (filter.number() != null && !filter.number().isBlank()) {
                    predicates.add(cb.like(cb.upper(root.get("publicNumber")),
                            "%" + filter.number().trim().toUpperCase() + "%"));
                }
                if (filter.subject() != null && !filter.subject().isBlank()) {
                    predicates.add(cb.like(cb.upper(root.get("subject")),
                            "%" + filter.subject().trim().toUpperCase() + "%"));
                }
                if (filter.status() != null) {
                    predicates.add(cb.equal(root.get("statusName"), filter.status()));
                }
                if (filter.priority() != null) {
                    predicates.add(cb.equal(root.get("priorityName"), filter.priority()));
                }
                if (filter.categoryId() != null) {
                    predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
                }
                if (filter.assigneeId() != null) {
                    predicates.add(cb.equal(root.get("assigneeId"), filter.assigneeId()));
                }
                if (filter.dueAfter() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dueAt"), filter.dueAfter()));
                }
                if (filter.dueBefore() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dueAt"), filter.dueBefore()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private TicketSummaryView toSummary(Ticket ticket) {
        var names = identity.displayNames(nonNull(ticket.requesterId(), ticket.assigneeId()));
        var categoryNames = ticket.categoryId() == null
                ? Map.<UUID, String>of()
                : categories.names(List.of(ticket.categoryId()));
        var categoryName = ticket.categoryId() == null ? null : categoryNames.get(ticket.categoryId());
        return new TicketSummaryView(ticket.id(), ticket.publicNumber(), ticket.subject(), ticket.statusName(),
                ticket.priorityName(), ticket.requesterId(), names.get(ticket.requesterId()), ticket.assigneeId(),
                names.get(ticket.assigneeId()), ticket.categoryId(), categoryName,
                ticket.dueAt(), ticket.createdAt(), ticket.updatedAt(),
                ticket.rowVersion() == null ? 0 : ticket.rowVersion());
    }

    private TicketDetailView toDetail(Ticket ticket, AuthenticatedUser actor) {
        var ticketComments = comments.findAllByTicketIdOrderByCreatedAtAsc(ticket.id());
        var files = attachments.listTicketFiles(ticket.id()).stream()
                .filter(file -> file.visibility() == CommentVisibility.PUBLIC || operatesTickets(actor))
                .toList();
        var ids = new HashSet<UUID>();
        ids.add(ticket.requesterId());
        if (ticket.assigneeId() != null) ids.add(ticket.assigneeId());
        ticketComments.forEach(comment -> ids.add(comment.authorId()));
        var names = identity.displayNames(ids);
        var categoryNames = ticket.categoryId() == null
                ? Map.<UUID, String>of()
                : categories.names(List.of(ticket.categoryId()));
        var byComment = files.stream().filter(file -> file.commentId() != null)
                .collect(Collectors.groupingBy(AttachmentView::commentId));
        var rootFiles = files.stream().filter(file -> file.commentId() == null).toList();
        var visibleComments = ticketComments.stream()
                .filter(comment -> comment.visibilityName() == CommentVisibility.PUBLIC || operatesTickets(actor))
                .map(comment -> new TicketCommentView(comment.id(), comment.authorId(), names.get(comment.authorId()),
                        comment.body(), comment.visibilityName(), comment.createdAt(),
                        byComment.getOrDefault(comment.id(), List.of())))
                .toList();
        var categoryName = ticket.categoryId() == null ? null : categoryNames.get(ticket.categoryId());
        return new TicketDetailView(ticket.id(), ticket.publicNumber(), ticket.subject(), ticket.description(),
                ticket.statusName(), ticket.priorityName(), ticket.requesterId(), names.get(ticket.requesterId()),
                ticket.assigneeId(), names.get(ticket.assigneeId()), ticket.categoryId(),
                categoryName, ticket.dueAt(), ticket.createdAt(), ticket.updatedAt(),
                ticket.rowVersion() == null ? 0 : ticket.rowVersion(), rootFiles, visibleComments);
    }

    private Set<UUID> nonNull(UUID first, UUID second) {
        var ids = new HashSet<UUID>();
        if (first != null) ids.add(first);
        if (second != null) ids.add(second);
        return ids;
    }

    private void notifyStatus(Ticket ticket, UUID actorId) {
        var recipients = new HashSet<UUID>();
        recipients.add(ticket.requesterId());
        if (ticket.assigneeId() != null) recipients.add(ticket.assigneeId());
        recipients.remove(actorId);
        notifications.notify(recipients, ticket.id(), NotificationType.STATUS_CHANGED,
                "Status alterado em " + ticket.publicNumber(), ticket.statusName().name());
    }

    private void notifyComment(Ticket ticket, TicketComment comment, AuthenticatedUser actor) {
        var recipients = new HashSet<UUID>();
        if (comment.visibilityName() == CommentVisibility.PUBLIC) {
            if (ticket.requesterId().equals(actor.id()) && ticket.assigneeId() != null) {
                recipients.add(ticket.assigneeId());
            } else {
                recipients.add(ticket.requesterId());
            }
        } else if (ticket.assigneeId() != null) {
            recipients.add(ticket.assigneeId());
        }
        recipients.remove(actor.id());
        var mentions = new HashSet<>(mentionedUserIds(comment.body()));
        var allowedMentions = new HashSet<>(identity.activeUserIdsWithAnyRole(Set.of(Role.AGENT, Role.MANAGER)));
        if (comment.visibilityName() == CommentVisibility.PUBLIC) {
            allowedMentions.add(ticket.requesterId());
        }
        mentions.retainAll(allowedMentions);
        mentions.remove(actor.id());
        recipients.removeAll(mentions);
        notifications.notify(recipients, ticket.id(), NotificationType.PUBLIC_COMMENT,
                comment.visibilityName() == CommentVisibility.INTERNAL
                        ? "Nova nota interna em " + ticket.publicNumber()
                        : "Novo comentário em " + ticket.publicNumber(),
                ticket.subject());
        notifications.notify(mentions, ticket.id(), NotificationType.MENTION,
                "Você foi mencionado em " + ticket.publicNumber(), ticket.subject());
    }

    private Collection<UUID> mentionedUserIds(String body) {
        var matcher = MENTION_PATTERN.matcher(body);
        var ids = new HashSet<UUID>();
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(1)));
        }
        return ids;
    }

    private void notifyDeadline(Ticket ticket, NotificationType type, String title) {
        var recipients = new HashSet<UUID>();
        if (ticket.assigneeId() != null) {
            recipients.add(ticket.assigneeId());
        }
        recipients.addAll(identity.activeUserIdsWithAnyRole(Set.of(Role.MANAGER)));
        notifications.notify(recipients, ticket.id(), type, title, ticket.subject());
    }
}
