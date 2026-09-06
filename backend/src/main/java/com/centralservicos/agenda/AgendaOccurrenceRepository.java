package com.centralservicos.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface AgendaOccurrenceRepository extends JpaRepository<AgendaOccurrence, UUID> {
    @Query("select o from AgendaOccurrence o where o.occurrenceDate >= :start and o.occurrenceDate < :end "
            + "order by o.occurrenceDate, o.createdAt, o.id")
    List<AgendaOccurrence> findInPeriod(LocalDate start, LocalDate end);
}
