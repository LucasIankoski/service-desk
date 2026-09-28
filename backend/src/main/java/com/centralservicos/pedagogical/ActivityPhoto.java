package com.centralservicos.pedagogical;

import jakarta.persistence.*;
import java.util.UUID;

@Embeddable
class ActivityPhoto {
    UUID id;
    String originalKey;
    String thumbnailKey;
    String mediaType;
    long byteSize;
    int width;
    int height;
    protected ActivityPhoto() {}
    ActivityPhoto(UUID id, String originalKey, String thumbnailKey, String mediaType, long byteSize, int width, int height) {
        this.id = id; this.originalKey = originalKey; this.thumbnailKey = thumbnailKey;
        this.mediaType = mediaType; this.byteSize = byteSize; this.width = width; this.height = height;
    }
}
