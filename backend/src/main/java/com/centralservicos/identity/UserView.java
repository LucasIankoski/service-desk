package com.centralservicos.identity;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @param roles perfis efetivos na unidade ativa (ADMIN global incluído)
 * @param admin administrador da rede, com acesso a todas as unidades
 * @param units perfis do usuário em cada unidade
 */
public record UserView(UUID id, String email, String displayName, Set<Role> roles, boolean active,
                       boolean passwordChangeRequired, boolean anonymized, Instant createdAt,
                       boolean admin, List<UnitRoles> units) {

    public record UnitRoles(UUID unitId, Set<Role> roles) {
    }
}
