package com.centralservicos.identity;

import java.time.Instant;
import java.util.UUID;

public record UnitView(UUID id, String code, String name, String supportEmail, String supportPhone,
                       boolean active, long version, Instant createdAt) {
}
