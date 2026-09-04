package com.centralservicos.agenda;

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
@Table(name = "agenda_item")
class AgendaItem {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private AgendaItemKind kindName;
    private String title;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String description;
    private String location;
    private UUID assigneeId;
    @Enumerated(EnumType.STRING)
    private AgendaItemStatus statusName;
    private Instant startAt;
    private Instant endAt;
    private boolean allDay;
    private UUID createdById;
    @Version
    private Long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgendaItem() {
    }

    AgendaItem(AgendaItemKind kind, String title, String description, String location, UUID assigneeId,
               Instant startAt, Instant endAt, boolean allDay, UUID createdById) {
        this.id = UUID.randomUUID();
        this.kindName = kind;
        this.title = title;
        this.description = description;
        this.location = location;
        this.assigneeId = assigneeId;
        this.statusName = kind == AgendaItemKind.INTERNAL_DEMAND ? AgendaItemStatus.PENDING : null;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.createdById = createdById;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    UUID id() { return id; }
    AgendaItemKind kindName() { return kindName; }
    String title() { return title; }
    String description() { return description; }
    String location() { return location; }
    UUID assigneeId() { return assigneeId; }
    AgendaItemStatus statusName() { return statusName; }
    Instant startAt() { return startAt; }
    Instant endAt() { return endAt; }
    boolean allDay() { return allDay; }
    Long rowVersion() { return rowVersion; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }

    void update(String title, String description, String location, UUID assigneeId,
                Instant startAt, Instant endAt, boolean allDay) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.assigneeId = assigneeId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        touch();
    }

    void changeStatus(AgendaItemStatus status) {
        this.statusName = status;
        touch();
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
