package com.centralservicos.attachments;

import com.centralservicos.shared.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
class FileSystemAttachmentStorage implements AttachmentStorage {

    private final Path root;

    FileSystemAttachmentStorage(@Value("${app.attachments-path}") String attachmentsPath) {
        this.root = Path.of(attachmentsPath).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, byte[] content) throws IOException {
        var target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public StoredResource load(String key, String mediaType, String filename) throws IOException {
        var target = resolve(key);
        if (!Files.isRegularFile(target)) {
            throw DomainException.notFound("Arquivo não encontrado.");
        }
        Resource resource = new UrlResource(target.toUri());
        return new StoredResource(resource, mediaType, filename, Files.size(target));
    }

    private Path resolve(String key) {
        var target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Nome de arquivo inválido.");
        }
        return target;
    }
}
