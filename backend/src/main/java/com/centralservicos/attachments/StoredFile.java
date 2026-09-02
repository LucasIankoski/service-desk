package com.centralservicos.attachments;

public record StoredFile(String key, String mediaType, long size, String sha256) {
}
