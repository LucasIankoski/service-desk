package com.centralservicos.attachments;

import java.io.IOException;

public interface AttachmentStorage {
    void store(String key, byte[] content) throws IOException;
    StoredResource load(String key, String mediaType, String filename) throws IOException;
}
