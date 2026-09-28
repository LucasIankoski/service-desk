package com.centralservicos.pedagogical;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "ped_record")
class ActivityRecord {
    @Id UUID id = UUID.randomUUID();
    UUID classId;
    @Column(length = 200) String title;
    LocalDate activityDate;
    UUID authorId;
    @Column(length = 160) String authorName;
    @ElementCollection
    @CollectionTable(name = "ped_record_photo", joinColumns = @JoinColumn(name = "record_id"))
    @OrderColumn(name = "position_index")
    List<ActivityPhoto> photos = new ArrayList<>();
    @Version Long rowVersion;
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt;
    protected ActivityRecord() {}
}
