package com.centralservicos.pedagogical;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class PedagogicalViews {
    private PedagogicalViews() {}
    public record Teacher(UUID id, String displayName) {}
    public record ClassView(UUID id, String name, boolean archived, List<Teacher> teachers, long version) {}
    public record ClassInput(@NotBlank @Size(max=120) String name, boolean archived,
                             @NotNull @Size(max=100) List<@NotNull UUID> teacherIds, Long version) {}
    public record DayInput(boolean noClass, @Size(max=4000) String reason,
                           @Size(max=10000) String proposal, @Size(max=10000) String objectives,
                           @Size(max=10000) String development, @Size(max=10000) String resources) {}
    public record DayView(LocalDate date, boolean noClass, String reason, String proposal,
                          String objectives, String development, String resources) {}
    public record CreateInput(@NotNull LocalDate weekStart) {}
    public record EditInput(@NotNull Long version, @Size(max=1000) String theme,
                            @NotNull @Size(max=100) List<@NotNull UUID> teacherIds,
                            @NotNull @Size(min=5, max=5) List<@NotNull @Valid DayInput> days) {}
    public record VersionInput(@NotNull Long version) {}
    public record Summary(UUID id, UUID classId, LocalDate weekStart, LocalDate weekEnd, String theme,
                          String status, List<Teacher> teachers, long version) {}
    public record PlanView(UUID id, UUID classId, String className, boolean archived, LocalDate weekStart,
                           LocalDate weekEnd, String theme, String status, List<Teacher> teachers,
                           List<DayView> days, long version, Instant createdAt, Instant updatedAt) {}
}
