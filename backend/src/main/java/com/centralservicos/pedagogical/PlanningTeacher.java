package com.centralservicos.pedagogical;
import jakarta.persistence.Embeddable;
import java.util.UUID;
@Embeddable
class PlanningTeacher {
    UUID teacherId;
    String displayName;
    protected PlanningTeacher() {}
    PlanningTeacher(UUID id, String name) { teacherId = id; displayName = name; }
}
