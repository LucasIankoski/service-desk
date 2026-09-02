package com.centralservicos.tickets;

import com.centralservicos.attachments.AttachmentView;
import com.centralservicos.shared.CommentVisibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketCommentView(UUID id, UUID authorId, String authorName, String body,
                                CommentVisibility visibility, Instant createdAt,
                                List<AttachmentView> attachments) {
}
