package com.centralservicos.tickets;

import java.time.Instant;
import java.util.UUID;

public record TicketSummaryView(UUID id, String publicNumber, String subject, TicketStatus status,
                                Priority priority, UUID requesterId, String requesterName,
                                UUID assigneeId, String assigneeName, UUID categoryId, String categoryName,
                                Instant dueAt, Instant createdAt, Instant updatedAt, long version) {
}
