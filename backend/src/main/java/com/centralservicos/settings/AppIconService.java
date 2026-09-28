package com.centralservicos.settings;

import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

@Service
class AppIconService {
    private final SettingsService settings;
    private final Map<Integer, byte[]> icons = new HashMap<>();
    private long cachedVersion = -1;

    AppIconService(SettingsService settings) {
        this.settings = settings;
    }

    // Retain only the current settings version and the four supported sizes.
    synchronized byte[] icon(int size) throws IOException {
        if (size != 32 && size != 180 && size != 192 && size != 512) {
            throw new IllegalArgumentException("Unsupported icon size");
        }
        var view = settings.publicSettings();
        if (cachedVersion != view.version()) {
            icons.clear();
            cachedVersion = view.version();
        }
        if (icons.containsKey(size)) return icons.get(size);
        BufferedImage logo = null;
        if (view.schoolLogoUrl() != null) {
            try (var source = settings.loadSchoolLogo().resource().getInputStream();
                 var input = ImageIO.createImageInputStream(source)) {
                var readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        var params = reader.getDefaultReadParam();
                        // Decode large uploads at reduced resolution to bound memory use.
                        int sample = Math.max(1, Math.max(reader.getWidth(0), reader.getHeight(0)) / 1024);
                        params.setSourceSubsampling(sample, sample, 0, 0);
                        logo = reader.read(0, params);
                    } finally {
                        reader.dispose();
                    }
                }
            }
        }
        byte[] result = render(logo, size);
        icons.put(size, result);
        return result;
    }

    static byte[] render(BufferedImage logo, int size) throws IOException {
        var canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (logo != null) {
                // A 55% square fits inside the maskable safe circle (80% diameter).
                double scale = size * 0.55 / Math.max(logo.getWidth(), logo.getHeight());
                int width = Math.max(1, (int) Math.round(logo.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(logo.getHeight() * scale));
                graphics.drawImage(logo, (size - width) / 2, (size - height) / 2, width, height, null);
            } else {
                graphics.scale(size / 100.0, size / 100.0);
                graphics.setColor(new Color(0x245B78));
                graphics.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.drawPolyline(new int[]{23, 50, 77}, new int[]{43, 25, 43}, 3);
                graphics.drawRect(29, 43, 42, 31);
                graphics.fillRect(45, 57, 10, 17);
                graphics.fillRect(35, 49, 5, 6);
                graphics.fillRect(60, 49, 5, 6);
                graphics.drawLine(24, 75, 76, 75);
            }
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", output);
        return output.toByteArray();
    }
}
