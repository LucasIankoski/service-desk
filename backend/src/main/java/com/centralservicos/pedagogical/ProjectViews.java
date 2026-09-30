package com.centralservicos.pedagogical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class ProjectViews {
    private ProjectViews() {}
    public record Run(@NotNull @Size(max=40000) String text, boolean bold) {}
    public record Block(@NotNull @Pattern(regexp="PARAGRAPH|BULLET|HEADING") String type,
                        @NotNull @Size(max=100) List<@NotNull @Valid Run> runs) {}
    public record RichText(@NotNull @Size(max=100) List<@NotNull @Valid Block> blocks) {
        public static RichText empty() { return new RichText(List.of()); }
        public boolean blank() { return blocks.stream().flatMap(b -> b.runs().stream()).allMatch(r -> r.text().isBlank()); }
        public int length() { return blocks.stream().flatMap(b -> b.runs().stream()).mapToInt(r -> r.text().length()).sum(); }
    }
    public record Activity(LocalDate date, @NotNull @Size(max=300) String title,
                           @NotNull @Valid RichText description) {}
    public record CreateInput(@NotBlank @Size(max=300) String title, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record EditInput(@NotNull Long version, @NotBlank @Size(max=300) String title,
                            @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                            @NotNull @Size(max=200) String ageRange,
                            @NotNull @Size(max=100) List<@NotNull UUID> teacherIds,
                            @NotNull @Valid RichText generalObjective,
                            @NotNull @Size(max=100) List<@NotNull @Size(max=2000) String> specificObjectives,
                            @NotNull @Size(max=200) List<@NotNull @Valid Activity> activities,
                            @NotNull @Valid RichText conclusion) {}
    public record Summary(UUID id, UUID classId, String title, LocalDate startDate, LocalDate endDate,
                          String status, List<PedagogicalViews.Teacher> teachers, long version) {}
    public record View(UUID id, UUID classId, String className, boolean archived, String title,
                       LocalDate startDate, LocalDate endDate, String ageRange, String status,
                       List<PedagogicalViews.Teacher> teachers, RichText generalObjective,
                       List<String> specificObjectives, List<Activity> activities, RichText conclusion,
                       long version, Instant createdAt, Instant updatedAt) {}
}
