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
import java.util.EnumSet;
import java.util.Set;
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected UserAccount() {
    }

    UserAccount(String email, String displayName, String passwordHash, Set<Role> roles) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
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
    Set<Role> roles() { return Set.copyOf(roles); }
    Instant createdAt() { return createdAt; }

    void changePassword(String encodedPassword, boolean requireChange) {
        passwordHash = encodedPassword;
        passwordChangeRequired = requireChange;
        updatedAt = Instant.now();
    }

    void update(String newDisplayName, Set<Role> newRoles, boolean enabled) {
        displayName = newDisplayName;
        roles = newRoles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(newRoles);
        active = enabled;
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
        updatedAt = Instant.now();
    }

    UserView toView() {
        return new UserView(id, email, displayName, roles(), active, passwordChangeRequired, anonymized, createdAt);
    }
}
