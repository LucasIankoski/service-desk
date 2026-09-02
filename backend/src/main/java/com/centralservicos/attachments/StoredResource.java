package com.centralservicos.attachments;

import org.springframework.core.io.Resource;

public record StoredResource(Resource resource, String mediaType, String filename, long size) {
}
