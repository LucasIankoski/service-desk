package com.centralservicos.pedagogical;

import com.centralservicos.attachments.AttachmentService;
import com.centralservicos.attachments.AttachmentStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ActivityImageTests {
    @Autowired ActivityImageProcessor processor;

    @Test void decodesWebpAndRespectsJpegExifOrientation() throws Exception {
        try (var input = getClass().getResourceAsStream("/images/activity.webp")) {
            var webp = processor.prepare(new MockMultipartFile("files", "photo.webp", "image/webp", input.readAllBytes()), 5);
            assertThat(webp.mediaType()).isEqualTo("image/webp");
            assertThat(ImageIO.read(new ByteArrayInputStream(webp.thumbnail())).getWidth()).isEqualTo(20);
        }
        var jpeg = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg);
        // Minimal EXIF APP1 containing orientation=6, inserted directly after JPEG SOI.
        byte[] exif = java.util.HexFormat.of().parseHex("ffe1002245786966000049492a0008000000010012010300010000000600000000000000");
        var oriented = new ByteArrayOutputStream(); oriented.write(jpeg.toByteArray(), 0, 2); oriented.write(exif); oriented.write(jpeg.toByteArray(), 2, jpeg.size() - 2);
        var prepared = processor.prepare(new MockMultipartFile("files", "photo.jpg", "image/jpeg", oriented.toByteArray()), 5);
        assertThat(prepared.width()).isEqualTo(20); assertThat(prepared.height()).isEqualTo(40);
        assertThat(ImageIO.read(new ByteArrayInputStream(prepared.thumbnail())).getHeight()).isEqualTo(40);
    }

    @Test void mirroredOrientationsKeepAllPixels() {
        var source = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xff123456);
        assertThat(ActivityImageProcessor.orient(source, 2).getRGB(1, 0)).isEqualTo(source.getRGB(0, 0));
        assertThat(ActivityImageProcessor.orient(source, 6).getRGB(2, 0)).isEqualTo(source.getRGB(0, 0));
        assertThat(ActivityImageProcessor.orient(source, 8).getRGB(0, 1)).isEqualTo(source.getRGB(0, 0));
    }

    @Test void failedCleanupStaysQueuedAndRetriesWithoutDeletingReferencedFiles() throws Exception {
        var queue = mock(PhotoCleanupRepository.class); var records = mock(ActivityRecordRepository.class); var storage = mock(AttachmentStorage.class);
        var service = new PhotoCleanupService(queue, records, storage);
        var abandoned = new PhotoCleanup("pedagogical/abandoned.png", Instant.now().minusSeconds(1));
        var referenced = new PhotoCleanup("pedagogical/referenced.png", Instant.now().minusSeconds(1));
        when(queue.findTop100ByEligibleAtBeforeOrderByEligibleAtAsc(any())).thenReturn(List.of(abandoned, referenced));
        when(records.references(referenced.storageKey)).thenReturn(1L);
        doThrow(new IOException()).doNothing().when(storage).delete(abandoned.storageKey);
        service.clean();
        verify(queue, never()).delete(abandoned); verify(storage, never()).delete(referenced.storageKey);
        assertThat(abandoned.eligibleAt).isAfter(Instant.now());
        service.clean(); verify(queue).delete(abandoned);
    }
}
