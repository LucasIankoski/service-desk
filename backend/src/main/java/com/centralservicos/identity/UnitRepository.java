package com.centralservicos.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface UnitRepository extends JpaRepository<Unit, UUID> {
    List<Unit> findAllByOrderByNameAsc();
    List<Unit> findAllByActiveTrueOrderByNameAsc();
    List<Unit> findAllByIdInAndActiveTrueOrderByNameAsc(java.util.Collection<UUID> ids);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    long countByActiveTrue();
}
