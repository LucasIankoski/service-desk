package com.centralservicos.pedagogical;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    List<SchoolClass> findAllByOrderByNameAsc();
    @Query("select distinct c from SchoolClass c left join c.teacherIds t where t = :teacher or c.contentVisibility = :visibility or (c.aee = true and exists (select r.id from SchoolClass r join r.teacherIds rt where r.aee = false and rt = :teacher)) order by c.name")
    List<SchoolClass> findVisible(UUID teacher, ClassContentVisibility visibility);
    @Query("select (count(c) > 0) from SchoolClass c join c.teacherIds t where c.aee = false and t = :teacher")
    boolean hasRegularAllocation(UUID teacher);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SchoolClass c where c.id = :id")
    Optional<SchoolClass> lockById(UUID id);
}
