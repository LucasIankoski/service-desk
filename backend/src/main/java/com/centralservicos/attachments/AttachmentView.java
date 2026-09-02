package com.centralservicos.attachments;

import com.centralservicos.shared.CommentVisibility;

import java.time.Instant;
import java.util.UUID;

public record AttachmentView(UUID id, UUID ticketId, UUID commentId, String originalName, String mediaType,
                             long fileSize, String sha256, AttachmentScanStatus scanStatus,
                             CommentVisibility visibility, Instant createdAt) {
}
