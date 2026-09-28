package com.centralservicos.pedagogical;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "ped_planning")
class WeeklyPlanning {
    @Id UUID id = UUID.randomUUID();
    UUID classId;
    LocalDate weekStart;
    String theme;
    String status = "DRAFT";
    @ElementCollection
    @CollectionTable(name = "ped_planning_teacher", joinColumns = @JoinColumn(name = "planning_id"))
    @OrderColumn(name = "position_index")
    List<PlanningTeacher> teachers = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "ped_planning_day", joinColumns = @JoinColumn(name = "planning_id"))
    @OrderColumn(name = "position_index")
    List<PlanningDay> days = new ArrayList<>();
    UUID createdById;
    @Version Long rowVersion;
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt;
    protected WeeklyPlanning() {}
    WeeklyPlanning(UUID classId, LocalDate weekStart, UUID actor) {
        this.classId = classId; this.weekStart = weekStart; this.createdById = actor;
    }
    void touch() { updatedAt = Instant.now(); }
}
