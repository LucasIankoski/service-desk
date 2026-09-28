package com.centralservicos.pedagogical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

interface PhotoCleanupRepository extends JpaRepository<PhotoCleanup, UUID> {
    List<PhotoCleanup> findTop100ByEligibleAtBeforeOrderByEligibleAtAsc(Instant now);
}
