package com.centralservicos.identity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @param principal usuário com os perfis efetivos na unidade ativa
 * @param activeUnitId unidade ativa, ou {@code null} quando o usuário ainda precisa escolher uma
 * @param requestedUnitDenied a unidade pedida não está disponível para o usuário
 * @param units unidades que o usuário pode abrir, com os perfis que tem em cada uma
 */
public record UnitSession(AuthenticatedUser principal, UUID activeUnitId, boolean requestedUnitDenied,
                          boolean admin, List<UnitAccess> units) {

    public record UnitAccess(UUID id, String code, String name, Set<Role> roles) {
    }
}
