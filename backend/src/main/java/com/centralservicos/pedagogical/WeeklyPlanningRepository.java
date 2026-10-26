package com.centralservicos.pedagogical;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
interface WeeklyPlanningRepository extends JpaRepository<WeeklyPlanning, UUID> {
    List<WeeklyPlanning> findByClassIdAndStudentIdOrderByWeekStartDesc(UUID classId, UUID studentId);
    boolean existsByScopeTypeAndScopeIdAndWeekStart(String scopeType, UUID scopeId, LocalDate weekStart);
}
