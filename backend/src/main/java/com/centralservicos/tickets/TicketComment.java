package com.centralservicos.tickets;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

import com.centralservicos.shared.CommentVisibility;

@Entity
@Table(name = "ticket_comment")
class TicketComment {

    @Id
    private UUID id;
    private UUID ticketId;
    private UUID authorId;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String body;
    @Enumerated(EnumType.STRING)
    private CommentVisibility visibilityName;
    private Instant createdAt;

    protected TicketComment() {
    }

    TicketComment(UUID ticketId, UUID authorId, String body, CommentVisibility visibility) {
        this.id = UUID.randomUUID();
        this.ticketId = ticketId;
        this.authorId = authorId;
        this.body = body.trim();
        this.visibilityName = visibility;
        this.createdAt = Instant.now();
    }

    UUID id() { return id; }
    UUID authorId() { return authorId; }
    String body() { return body; }
    CommentVisibility visibilityName() { return visibilityName; }
    Instant createdAt() { return createdAt; }
}
