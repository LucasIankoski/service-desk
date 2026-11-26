package com.centralservicos.tickets;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface TicketCounterRepository extends JpaRepository<TicketCounter, TicketCounter.Key> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TicketCounter c where c.unitId = :unitId and c.counterYear = :year")
    Optional<TicketCounter> lock(UUID unitId, int year);
}
