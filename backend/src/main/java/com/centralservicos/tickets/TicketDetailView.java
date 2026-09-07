package com.centralservicos.tickets;

import com.centralservicos.attachments.AttachmentView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketDetailView(UUID id, String publicNumber, String description,
                               TicketStatus status, UUID requesterId, String requesterName,
                               UUID assigneeId, String assigneeName, UUID categoryId, String categoryName,
                               Instant createdAt, Instant updatedAt, long version,
                               List<AttachmentView> attachments, List<TicketCommentView> comments) {
}
