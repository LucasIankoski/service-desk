package com.centralservicos.agenda;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "agenda_occurrence")
class AgendaOccurrence {
    @Id
    private UUID id;
    private LocalDate occurrenceDate;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String body;
    private UUID createdById;
    @Version
    private Long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgendaOccurrence() {}

    AgendaOccurrence(LocalDate date, String body, UUID actorId) {
        this.id = UUID.randomUUID();
        this.occurrenceDate = date;
        this.body = body;
        this.createdById = actorId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void update(LocalDate date, String body) {
        this.occurrenceDate = date;
        this.body = body;
        this.updatedAt = Instant.now();
    }

    UUID id() { return id; }
    LocalDate occurrenceDate() { return occurrenceDate; }
    String body() { return body; }
    UUID createdById() { return createdById; }
    long version() { return rowVersion == null ? 0 : rowVersion; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
