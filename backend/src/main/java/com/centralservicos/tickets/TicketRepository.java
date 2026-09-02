package com.centralservicos.tickets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    Optional<Ticket> findByPublicNumberIgnoreCase(String publicNumber);

    @Query("select t from Ticket t where t.dueAt is not null and t.deadlineWarningSent = false "
            + "and t.dueAt > :now and t.dueAt <= :threshold and t.statusName not in :terminalStatuses")
    List<Ticket> findDeadlineWarnings(Instant now, Instant threshold, Collection<TicketStatus> terminalStatuses);

    @Query("select t from Ticket t where t.dueAt is not null and t.overdueSent = false "
            + "and t.dueAt < :now and t.statusName not in :terminalStatuses")
    List<Ticket> findOverdue(Instant now, Collection<TicketStatus> terminalStatuses);
}
