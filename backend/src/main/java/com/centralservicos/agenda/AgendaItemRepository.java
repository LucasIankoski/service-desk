package com.centralservicos.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface AgendaItemRepository extends JpaRepository<AgendaItem, UUID> {

    @Query("select i from AgendaItem i where i.startAt < :rangeEnd and i.endAt > :rangeStart "
            + "order by i.startAt asc, i.title asc")
    List<AgendaItem> findOverlapping(Instant rangeStart, Instant rangeEnd);

    @Query("select i from AgendaItem i where i.kindName = :kind and i.startAt < :rangeEnd "
            + "and i.endAt > :rangeStart order by i.startAt asc, i.title asc")
    List<AgendaItem> findOverlappingByKind(AgendaItemKind kind, Instant rangeStart, Instant rangeEnd);
}
