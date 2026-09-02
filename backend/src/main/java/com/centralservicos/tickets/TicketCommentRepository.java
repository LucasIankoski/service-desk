package com.centralservicos.tickets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {
    List<TicketComment> findAllByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
