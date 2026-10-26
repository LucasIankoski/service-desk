package com.centralservicos.pedagogical;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface ProjectRepository extends JpaRepository<PedagogicalProject, UUID> {
    List<PedagogicalProject> findByClassIdAndStudentIdOrderByStartDateDescCreatedAtDescIdAsc(UUID classId, UUID studentId);
}
