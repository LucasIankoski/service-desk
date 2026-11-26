package com.centralservicos.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Entity
@Table(name = "user_account")
class UserAccount {

    @Id
    private UUID id;
    private String email;
    private String displayName;
    private String passwordHash;
    private boolean active;
    private boolean passwordChangeRequired;
    private boolean anonymized;
    private Instant createdAt;
    private Instant updatedAt;

    /** Perfis globais. Desde a 017 guarda apenas ADMIN, que administra todas as unidades. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "unit_membership", joinColumns = @JoinColumn(name = "user_id"))
    private Set<UnitRole> memberships = new HashSet<>();

    protected UserAccount() {
    }

    UserAccount(String email, String displayName, String passwordHash, boolean admin,
                Map<UUID, Set<Role>> unitRoles) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        if (admin) {
            this.roles.add(Role.ADMIN);
        }
        replaceMemberships(unitRoles);
        this.active = true;
        this.passwordChangeRequired = true;
        this.anonymized = false;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    UUID id() { return id; }
    String email() { return email; }
    String displayName() { return displayName; }
    String passwordHash() { return passwordHash; }
    boolean active() { return active; }
    boolean passwordChangeRequired() { return passwordChangeRequired; }
    Instant createdAt() { return createdAt; }

    boolean admin() {
        return roles.contains(Role.ADMIN);
    }

    /** Perfis efetivos na unidade: ADMIN global mais os perfis vinculados àquela unidade. */
    Set<Role> rolesIn(UUID unitId) {
        var effective = EnumSet.noneOf(Role.class);
        if (admin()) {
            effective.add(Role.ADMIN);
        }
        if (unitId != null) {
            memberships.stream().filter(m -> unitId.equals(m.unitId())).forEach(m -> effective.add(m.role()));
        }
        return effective;
    }

    Set<UUID> memberUnitIds() {
        var ids = new HashSet<UUID>();
        memberships.forEach(m -> ids.add(m.unitId()));
        return ids;
    }

    Map<UUID, Set<Role>> unitRoles() {
        var grouped = new TreeMap<UUID, Set<Role>>();
        memberships.forEach(m -> grouped.computeIfAbsent(m.unitId(), ignored -> EnumSet.noneOf(Role.class)).add(m.role()));
        return grouped;
    }

    void changePassword(String encodedPassword, boolean requireChange) {
        passwordHash = encodedPassword;
        passwordChangeRequired = requireChange;
        updatedAt = Instant.now();
    }

    void updateProfile(String newDisplayName, boolean enabled) {
        displayName = newDisplayName;
        active = enabled;
        updatedAt = Instant.now();
    }

    void grantAdmin(boolean admin) {
        if (admin) {
            roles.add(Role.ADMIN);
        } else {
            roles.remove(Role.ADMIN);
        }
        updatedAt = Instant.now();
    }

    void replaceMemberships(Map<UUID, Set<Role>> unitRoles) {
        memberships.clear();
        unitRoles.forEach((unitId, unitSet) -> unitSet.stream()
                .filter(role -> role != Role.ADMIN)
                .forEach(role -> memberships.add(new UnitRole(unitId, role))));
        updatedAt = Instant.now();
    }

    void replaceRolesInUnit(UUID unitId, Set<Role> unitSet) {
        memberships.removeIf(m -> unitId.equals(m.unitId()));
        unitSet.stream().filter(role -> role != Role.ADMIN)
                .forEach(role -> memberships.add(new UnitRole(unitId, role)));
        updatedAt = Instant.now();
    }

    void anonymize(String replacementEmail, String unusablePassword) {
        email = replacementEmail;
        displayName = "Usuário anonimizado";
        passwordHash = unusablePassword;
        active = false;
        passwordChangeRequired = false;
        anonymized = true;
        roles.clear();
        memberships.clear();
        updatedAt = Instant.now();
    }

    UserView toView(UUID activeUnitId) {
        List<UserView.UnitRoles> units = unitRoles().entrySet().stream()
                .map(entry -> new UserView.UnitRoles(entry.getKey(), Set.copyOf(entry.getValue())))
                .sorted(Comparator.comparing(unit -> unit.unitId().toString()))
                .toList();
        return new UserView(id, email, displayName, Set.copyOf(rolesIn(activeUnitId)), active,
                passwordChangeRequired, anonymized, createdAt, admin(), units);
    }
}
