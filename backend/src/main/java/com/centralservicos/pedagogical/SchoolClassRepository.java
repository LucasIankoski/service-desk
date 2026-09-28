package com.centralservicos.pedagogical;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    List<SchoolClass> findAllByOrderByNameAsc();
    @Query("select distinct c from SchoolClass c join c.teacherIds t where t = :teacher order by c.name")
    List<SchoolClass> findAllocated(UUID teacher);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SchoolClass c where c.id = :id")
    Optional<SchoolClass> lockById(UUID id);
}
