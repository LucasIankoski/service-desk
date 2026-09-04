package com.centralservicos.agenda;

import java.time.Instant;
import java.util.UUID;

public record AgendaItemView(UUID id, AgendaItemKind kind, String title, String description, String location,
                             UUID assigneeId, String assigneeName, AgendaItemStatus status,
                             Instant startAt, Instant endAt, boolean allDay,
                             long version, Instant createdAt, Instant updatedAt) {
}
