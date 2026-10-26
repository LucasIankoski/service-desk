package com.centralservicos.pedagogical;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface AeeDocumentRepository extends JpaRepository<AeeDocument,UUID> {
    List<AeeDocument> findByStudentIdOrderByCreatedAtDescIdAsc(UUID studentId);
    boolean existsByStorageKey(String storageKey);
}
