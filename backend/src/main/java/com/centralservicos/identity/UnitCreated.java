package com.centralservicos.identity;

import java.util.UUID;

/**
 * Publicado após a criação de uma unidade.
 *
 * @param copyConfigurationFrom unidade cujas categorias devem ser copiadas, ou {@code null}
 */
public record UnitCreated(UUID unitId, UUID copyConfigurationFrom, UUID actorId) {
}
