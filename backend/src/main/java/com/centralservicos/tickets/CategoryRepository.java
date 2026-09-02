package com.centralservicos.tickets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    Optional<Category> findByIdAndActiveTrue(UUID id);
    List<Category> findAllByActiveTrueOrderByNameAsc();
    List<Category> findAllByOrderByNameAsc();
}
