package com.centralservicos.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserView(UUID id, String email, String displayName, Set<Role> roles, boolean active,
                       boolean passwordChangeRequired, boolean anonymized, Instant createdAt) {
}
