package com.centralservicos.pedagogical;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
interface WeeklyPlanningRepository extends JpaRepository<WeeklyPlanning, UUID> {
    List<WeeklyPlanning> findByClassIdOrderByWeekStartDesc(UUID classId);
    boolean existsByClassIdAndWeekStart(UUID classId, LocalDate weekStart);
}
