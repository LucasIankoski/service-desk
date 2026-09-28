package com.centralservicos.pedagogical;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
@Entity
@Table(name = "ped_class")
class SchoolClass {
    @Id UUID id = UUID.randomUUID();
    String name;
    boolean archived;
    @ElementCollection
    @CollectionTable(name = "ped_allocation", joinColumns = @JoinColumn(name = "class_id"))
    @Column(name = "teacher_id")
    Set<UUID> teacherIds = new HashSet<>();
    @Version Long rowVersion;
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt;
    protected SchoolClass() {}
    SchoolClass(String name) { this.name = name; }
    void touch() { updatedAt = Instant.now(); }
}
