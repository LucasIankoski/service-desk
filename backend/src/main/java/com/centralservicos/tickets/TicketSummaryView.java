package com.centralservicos.tickets;

import java.time.Instant;
import java.util.UUID;

public record TicketSummaryView(UUID id, String publicNumber, TicketStatus status,
                                UUID requesterId, String requesterName,
                                UUID assigneeId, String assigneeName, UUID categoryId, String categoryName,
                                Instant createdAt, Instant updatedAt, long version) {
}
