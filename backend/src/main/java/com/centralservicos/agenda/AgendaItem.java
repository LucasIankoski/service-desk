package com.centralservicos.agenda;

import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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
import java.util.List;
import java.util.ArrayList;

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
    @ElementCollection
    @CollectionTable(name = "agenda_item_assignee", joinColumns = @JoinColumn(name = "agenda_item_id"))
    @Column(name = "assignee_id")
    @OrderColumn(name = "position_index")
    private List<UUID> assigneeIds = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    private AgendaItemStatus statusName;
    @Enumerated(EnumType.STRING)
    private AgendaItemPriority priority;
    private Instant startAt;
    private Instant endAt;
    private boolean allDay;
    @Enumerated(EnumType.STRING)
    private AgendaShift shift;
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
    List<UUID> assigneeIds() { return List.copyOf(assigneeIds); }
    void assign(List<UUID> ids) {
        assigneeIds.clear();
        assigneeIds.addAll(ids);
        assigneeId = ids.isEmpty() ? null : ids.getFirst();
        touch();
    }
    AgendaItemStatus statusName() { return statusName; }
    AgendaItemPriority priority() { return priority; }
    void changePriority(AgendaItemPriority priority) { this.priority = priority; }
    Instant startAt() { return startAt; }
    Instant endAt() { return endAt; }
    boolean allDay() { return allDay; }
    AgendaShift shift() { return shift; }
    void changeShift(AgendaShift shift) { this.shift = shift; }
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
