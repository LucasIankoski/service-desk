package com.centralservicos.pedagogical;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.*;

@Entity
@Table(name="ped_project")
class PedagogicalProject {
    @Id UUID id = UUID.randomUUID();
    UUID classId;
    String title;
    LocalDate startDate;
    LocalDate endDate;
    String ageRange = "";
    String status = "DRAFT";
    @Convert(converter=ProjectRichTextConverter.class)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) ProjectViews.RichText generalObjective = ProjectViews.RichText.empty();
    @Convert(converter=ProjectRichTextConverter.class)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) ProjectViews.RichText conclusion = ProjectViews.RichText.empty();
    @ElementCollection @CollectionTable(name="ped_project_teacher", joinColumns=@JoinColumn(name="project_id"))
    @OrderColumn(name="position_index") List<PlanningTeacher> teachers = new ArrayList<>();
    @ElementCollection @CollectionTable(name="ped_project_objective", joinColumns=@JoinColumn(name="project_id"))
    @OrderColumn(name="position_index") @Column(name="objective_text")
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) List<String> specificObjectives = new ArrayList<>();
    @ElementCollection @CollectionTable(name="ped_project_activity", joinColumns=@JoinColumn(name="project_id"))
    @OrderColumn(name="position_index") List<ProjectActivity> activities = new ArrayList<>();
    UUID createdById;
    @Version Long rowVersion;
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt;
    protected PedagogicalProject() {}
    PedagogicalProject(UUID classId, ProjectViews.CreateInput input, UUID actor) {
        this.classId = classId; title = input.title().trim(); startDate = input.startDate(); endDate = input.endDate(); createdById = actor;
    }
    void touch() { updatedAt = Instant.now(); }
}
