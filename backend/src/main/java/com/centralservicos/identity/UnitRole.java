package com.centralservicos.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;
import java.util.UUID;

/** Perfil de um usuário dentro de uma unidade. ADMIN não aparece aqui: ele vale para a rede inteira. */
@Embeddable
class UnitRole {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false)
    private Role role;

    protected UnitRole() {
    }

    UnitRole(UUID unitId, Role role) {
        this.unitId = unitId;
        this.role = role;
    }

    UUID unitId() { return unitId; }
    Role role() { return role; }

    @Override
    public boolean equals(Object other) {
        return other instanceof UnitRole that && Objects.equals(unitId, that.unitId) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(unitId, role);
    }
}
