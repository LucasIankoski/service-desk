package com.centralservicos.tickets;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category")
class Category {

    @Id
    private UUID id;
    private String name;
    private boolean active;
    private Instant createdAt;

    protected Category() {
    }

    Category(String name) {
        this.id = UUID.randomUUID();
        this.name = name.trim();
        this.active = true;
        this.createdAt = Instant.now();
    }

    UUID id() { return id; }
    String name() { return name; }
    boolean active() { return active; }
    Instant createdAt() { return createdAt; }

    void update(String name, boolean active) {
        this.name = name.trim();
        this.active = active;
    }

    CategoryView toView() {
        return new CategoryView(id, name, active, createdAt);
    }
}
