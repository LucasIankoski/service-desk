package com.centralservicos.pedagogical;

import com.centralservicos.attachments.AttachmentStorage;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.*;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
class PhotoCleanupService {
    private final PhotoCleanupRepository queue;
    private final ActivityRecordRepository records;
    private final AttachmentStorage storage;
    PhotoCleanupService(PhotoCleanupRepository queue, ActivityRecordRepository records, AttachmentStorage storage) {
        this.queue = queue; this.records = records; this.storage = storage;
    }
    // Durable before writing bytes: a failed transaction or process restart leaves recoverable cleanup work.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stage(List<String> keys) {
        keys.forEach(key -> queue.save(new PhotoCleanup(key, Instant.now().plusSeconds(3600))));
    }
    @Transactional
    public void removed(ActivityPhoto photo) {
        queue.save(new PhotoCleanup(photo.originalKey, Instant.now()));
        queue.save(new PhotoCleanup(photo.thumbnailKey, Instant.now()));
    }
    @Scheduled(fixedDelayString = "${app.photo-cleanup-delay-ms:60000}")
    @Transactional
    public void clean() {
        for (var entry : queue.findTop100ByEligibleAtBeforeOrderByEligibleAtAsc(Instant.now())) {
            try {
                if (records.references(entry.storageKey) == 0) storage.delete(entry.storageKey);
                queue.delete(entry);
            } catch (IOException exception) {
                // Retain the durable job for the next attempt, without logging file or pupil data.
                entry.eligibleAt = Instant.now().plusSeconds(300);
            }
        }
    }
}
