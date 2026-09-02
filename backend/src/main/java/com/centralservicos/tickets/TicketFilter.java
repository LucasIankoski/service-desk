package com.centralservicos.tickets;

import java.time.Instant;
import java.util.UUID;

public record TicketFilter(String number, String subject, TicketStatus status, Priority priority,
                           UUID categoryId, UUID assigneeId, Instant dueBefore, Instant dueAfter) {
}
