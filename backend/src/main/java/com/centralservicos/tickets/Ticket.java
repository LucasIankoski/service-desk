package com.centralservicos.tickets;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket")
class Ticket {

    @Id
    private UUID id;
    private String publicNumber;
    private UUID requesterId;
    private UUID assigneeId;
    private UUID categoryId;
    private String subject;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String description;
    @Enumerated(EnumType.STRING)
    private TicketStatus statusName;
    @Enumerated(EnumType.STRING)
    private Priority priorityName;
    private Instant dueAt;
    private boolean deadlineWarningSent;
    private boolean overdueSent;
    private Instant firstRespondedAt;
    private Instant resolvedAt;
    private Instant closedAt;
    @Version
    private Long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;

    protected Ticket() {
    }

    Ticket(String publicNumber, UUID requesterId, String subject, String description, UUID categoryId) {
        this.id = UUID.randomUUID();
        this.publicNumber = publicNumber;
        this.requesterId = requesterId;
        this.subject = subject.trim();
        this.description = description.trim();
        this.categoryId = categoryId;
        this.statusName = TicketStatus.OPEN;
        this.priorityName = Priority.NORMAL;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    UUID id() { return id; }
    String publicNumber() { return publicNumber; }
    UUID requesterId() { return requesterId; }
    UUID assigneeId() { return assigneeId; }
    UUID categoryId() { return categoryId; }
    String subject() { return subject; }
    String description() { return description; }
    TicketStatus statusName() { return statusName; }
    Priority priorityName() { return priorityName; }
    Instant dueAt() { return dueAt; }
    Instant resolvedAt() { return resolvedAt; }
    Long rowVersion() { return rowVersion; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }

    boolean terminal() {
        return statusName == TicketStatus.CLOSED || statusName == TicketStatus.CANCELED;
    }

    void assign(UUID assigneeId) {
        this.assigneeId = assigneeId;
        if (statusName == TicketStatus.OPEN) {
            statusName = TicketStatus.TRIAGE;
        }
        touch();
    }

    void classify(UUID categoryId, Priority priority, Instant dueAt) {
        this.categoryId = categoryId;
        this.priorityName = priority == null ? Priority.NORMAL : priority;
        this.dueAt = dueAt;
        this.deadlineWarningSent = false;
        this.overdueSent = false;
        touch();
    }

    void setPriority(Priority priority) {
        this.priorityName = priority;
        touch();
    }

    void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
        this.deadlineWarningSent = false;
        this.overdueSent = false;
        touch();
    }

    void transition(TicketStatus next) {
        this.statusName = next;
        var now = Instant.now();
        if ((next == TicketStatus.IN_PROGRESS || next == TicketStatus.WAITING_REQUESTER)
                && firstRespondedAt == null) {
            firstRespondedAt = now;
        }
        if (next == TicketStatus.RESOLVED) {
            resolvedAt = now;
        }
        if (next == TicketStatus.CLOSED) {
            closedAt = now;
        }
        touch();
    }

    void markDeadlineWarningSent() {
        deadlineWarningSent = true;
        touch();
    }

    void markOverdueSent() {
        overdueSent = true;
        touch();
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
