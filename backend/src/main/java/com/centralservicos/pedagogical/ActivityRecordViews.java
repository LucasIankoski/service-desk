package com.centralservicos.pedagogical;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

final class ActivityRecordViews {
    record Input(@NotBlank @Size(max = 200) String title, @NotNull LocalDate activityDate,
                 @PositiveOrZero Long version, @NotNull @Size(max = 20) List<@NotNull UUID> retainedPhotoIds) {}
    record Photo(UUID id, String url, String thumbnailUrl, int width, int height) {}
    record Summary(UUID id, String classId, String title, LocalDate activityDate, String authorName,
                   int photoCount, Photo cover, long version) {}
    record Page(List<Summary> items, int page, int totalPages, long totalElements, int attachmentLimitMb) {}
    record View(UUID id, UUID classId, String className, boolean archived, String title, LocalDate activityDate,
                UUID authorId, String authorName, List<Photo> photos, long version, boolean canEdit,
                Instant createdAt, Instant updatedAt, int attachmentLimitMb) {}
}
