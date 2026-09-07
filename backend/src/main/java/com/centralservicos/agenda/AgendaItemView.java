package com.centralservicos.agenda;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record AgendaItemView(UUID id, AgendaItemKind kind, String title, String description, String location,
                             UUID assigneeId, String assigneeName, AgendaItemStatus status, AgendaItemPriority priority,
                             Instant startAt, Instant endAt, boolean allDay,
                             long version, Instant createdAt, Instant updatedAt, List<AssigneeView> assignees, AgendaShift shift) {
    public record AssigneeView(UUID id, String displayName) {}
}
