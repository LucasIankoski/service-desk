package com.centralservicos.attachments;

import com.centralservicos.shared.CommentVisibility;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachment")
class Attachment {

    @Id
    private UUID id;
    private UUID ticketId;
    private UUID commentId;
    private UUID uploadedBy;
    private String originalName;
    private String storedName;
    private String mediaType;
    private long fileSize;
    private String sha256;
    @Enumerated(EnumType.STRING)
    private AttachmentScanStatus scanStatus;
    @Enumerated(EnumType.STRING)
    private CommentVisibility visibilityName;
    private Instant createdAt;

    protected Attachment() {
    }

    Attachment(UUID ticketId, UUID commentId, UUID uploadedBy, String originalName, String storedName,
               String mediaType, long fileSize, String sha256, CommentVisibility visibility) {
        this.id = UUID.randomUUID();
        this.ticketId = ticketId;
        this.commentId = commentId;
        this.uploadedBy = uploadedBy;
        this.originalName = originalName;
        this.storedName = storedName;
        this.mediaType = mediaType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.scanStatus = AttachmentScanStatus.CLEAN;
        this.visibilityName = visibility;
        this.createdAt = Instant.now();
    }

    UUID id() { return id; }
    UUID ticketId() { return ticketId; }
    UUID commentId() { return commentId; }
    UUID uploadedBy() { return uploadedBy; }
    String originalName() { return originalName; }
    String storedName() { return storedName; }
    String mediaType() { return mediaType; }
    long fileSize() { return fileSize; }
    String sha256() { return sha256; }
    AttachmentScanStatus scanStatus() { return scanStatus; }
    CommentVisibility visibilityName() { return visibilityName; }
    Instant createdAt() { return createdAt; }

    AttachmentView toView() {
        return new AttachmentView(id, ticketId, commentId, originalName, mediaType, fileSize,
                sha256, scanStatus, visibilityName, createdAt);
    }
}
