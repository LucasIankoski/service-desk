package com.centralservicos.pedagogical;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.UUID;

interface ActivityRecordRepository extends JpaRepository<ActivityRecord, UUID> {
    Page<ActivityRecord> findByClassIdAndStudentIdAndActivityDateGreaterThanEqualAndActivityDateLessThan(
            UUID classId, UUID studentId, LocalDate start, LocalDate end, Pageable pageable);
    Page<ActivityRecord> findByClassIdIsNullAndStudentIdIsNullAndActivityDateGreaterThanEqualAndActivityDateLessThan(
            LocalDate start, LocalDate end, Pageable pageable);
    @Query("select count(r) from ActivityRecord r join r.photos p where p.originalKey = :key or p.thumbnailKey = :key")
    long references(String key);
}
