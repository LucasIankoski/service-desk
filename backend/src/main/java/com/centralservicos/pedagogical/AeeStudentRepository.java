package com.centralservicos.pedagogical;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

interface AeeStudentRepository extends JpaRepository<AeeStudent, UUID> {
    List<AeeStudent> findByAeeClassIdAndClassIdOrderByNameAscIdAsc(UUID aeeClassId, UUID classId);
    boolean existsByAeeClassId(UUID id);
    boolean existsByClassId(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AeeStudent s where s.id = :id")
    Optional<AeeStudent> lockById(UUID id);
}
