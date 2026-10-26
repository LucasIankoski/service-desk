package com.centralservicos.pedagogical;

import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;

final class GeneralRecordViews {
    record Input(@NotBlank @Size(max = 200) String title, @NotNull LocalDate activityDate,
                 @PositiveOrZero Long version, @NotNull @Size(max = 100) List<@NotNull UUID> retainedPhotoIds) {
        ActivityRecordViews.Input recordInput() { return new ActivityRecordViews.Input(title, activityDate, version, retainedPhotoIds); }
    }
    record Summary(UUID id, String title, LocalDate activityDate, String authorName,
                   int photoCount, ActivityRecordViews.Photo cover, long version) {}
    record Page(List<Summary> items, int page, int totalPages, long totalElements,
                int attachmentLimitMb, int maxPhotos, boolean canCreate) {}
    record View(UUID id, String title, LocalDate activityDate, UUID authorId, String authorName,
                List<ActivityRecordViews.Photo> photos, long version, boolean canEdit,
                Instant createdAt, Instant updatedAt, int attachmentLimitMb, int maxPhotos) {}
}
