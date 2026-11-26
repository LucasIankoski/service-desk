package com.centralservicos.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Unidade escolar da rede. Os dados operacionais de cada unidade ficam isolados por {@code unit_id}. */
@Entity
@Table(name = "school_unit")
class Unit {

    @Id
    private UUID id;
    private String code;
    private String name;
    private String supportEmail;
    private String supportPhone;
    private boolean active;
    @Version
    private Long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;

    protected Unit() {
    }

    Unit(String code, String name, String supportEmail, String supportPhone) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.supportEmail = supportEmail;
        this.supportPhone = supportPhone;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    UUID id() { return id; }
    String code() { return code; }
    String name() { return name; }
    boolean active() { return active; }
    Long rowVersion() { return rowVersion; }

    void update(String name, String supportEmail, String supportPhone, boolean active) {
        this.name = name;
        this.supportEmail = supportEmail;
        this.supportPhone = supportPhone;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    UnitView toView() {
        return new UnitView(id, code, name, supportEmail, supportPhone, active,
                rowVersion == null ? 0 : rowVersion, createdAt);
    }
}
