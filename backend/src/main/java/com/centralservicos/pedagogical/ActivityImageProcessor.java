package com.centralservicos.pedagogical;

import com.centralservicos.attachments.AttachmentService;
import com.centralservicos.shared.DomainException;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

@Component
class ActivityImageProcessor {
    private final AttachmentService attachments;
    ActivityImageProcessor(AttachmentService attachments) { this.attachments = attachments; }
    record Prepared(byte[] original, String mediaType, byte[] thumbnail, int width, int height) {}

    Prepared prepare(MultipartFile file, int limitMb) {
        var upload = attachments.inspectImage(file, limitMb);
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(upload.content()))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException();
            var reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 60_000_000L)
                    throw DomainException.unprocessable("Cada foto deve ter até 60 megapixels.");
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / 1600);
                params.setSourceSubsampling(sample, sample, 0, 0);
                var decoded = reader.read(0, params);
                int orientation = orientation(upload.content());
                var oriented = orient(decoded, orientation);
                double scale = Math.min(1, 640d / Math.max(oriented.getWidth(), oriented.getHeight()));
                var thumb = new BufferedImage(Math.max(1, (int) Math.round(oriented.getWidth() * scale)),
                        Math.max(1, (int) Math.round(oriented.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = thumb.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, thumb.getWidth(), thumb.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(oriented, 0, 0, thumb.getWidth(), thumb.getHeight(), null);
                } finally { graphics.dispose(); }
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(thumb, "jpeg", output)) throw new IOException();
                return new Prepared(upload.content(), upload.mediaType(), output.toByteArray(),
                        orientation >= 5 && orientation <= 8 ? height : width,
                        orientation >= 5 && orientation <= 8 ? width : height);
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof DomainException domain) throw domain;
            throw DomainException.unprocessable("Não foi possível ler a foto. Use uma imagem JPG, PNG ou WebP válida.");
        }
    }
    private int orientation(byte[] bytes) {
        try {
            var directory = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes)).getFirstDirectoryOfType(ExifIFD0Directory.class);
            return directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)
                    ? directory.getInt(ExifIFD0Directory.TAG_ORIENTATION) : 1;
        } catch (Exception exception) { return 1; }
    }
    static BufferedImage orient(BufferedImage source, int orientation) {
        int w = source.getWidth(), h = source.getHeight();
        boolean swap = orientation >= 5 && orientation <= 8;
        var target = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int dx = x, dy = y;
            switch (orientation) {
                case 2 -> dx = w - 1 - x;
                case 3 -> { dx = w - 1 - x; dy = h - 1 - y; }
                case 4 -> dy = h - 1 - y;
                case 5 -> { dx = y; dy = x; }
                case 6 -> { dx = h - 1 - y; dy = x; }
                case 7 -> { dx = h - 1 - y; dy = w - 1 - x; }
                case 8 -> { dx = y; dy = w - 1 - x; }
                default -> { }
            }
            target.setRGB(dx, dy, source.getRGB(x, y));
        }
        return target;
    }
}
