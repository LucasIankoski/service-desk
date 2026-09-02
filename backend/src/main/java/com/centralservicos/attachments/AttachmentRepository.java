package com.centralservicos.attachments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findAllByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
