package com.centralservicos.tickets;

import java.util.UUID;

public record TicketFilter(String number, TicketStatus status, UUID categoryId, UUID assigneeId) {
}
