package com.centralservicos.tickets;

import java.time.Instant;
import java.util.UUID;

public record CategoryView(UUID id, String name, boolean active, Instant createdAt) {
}
