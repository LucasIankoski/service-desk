package com.centralservicos.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class AppIconTests {
    @Autowired SettingsService settings;
    @Autowired WebApplicationContext context;

    @Test
    void anonymousIconsFollowLogoUploadReplacementAndRemoval() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var initial = settings.adminSettings();
        settings.updateSchoolLogo(null, initial.version(), null);
        try {
            var fallback = mvc.perform(get("/api/v1/public/settings/app-icon/180.png"))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(ImageIO.read(new ByteArrayInputStream(fallback)).getWidth()).isEqualTo(180);
            for (var color : new Color[]{Color.RED, Color.BLUE}) {
                var logo = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
                var graphics = logo.createGraphics();
                graphics.setColor(color);
                graphics.fillRect(0, 0, 400, 200);
                graphics.dispose();
                var bytes = new ByteArrayOutputStream();
                ImageIO.write(logo, "png", bytes);
                var uploaded = settings.updateSchoolLogo(new MockMultipartFile("file", "logo.png", "image/png",
                        bytes.toByteArray()), settings.adminSettings().version(), null);
                mvc.perform(get("/api/v1/public/settings/manifest.webmanifest"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType("application/manifest+json"))
                        .andExpect(jsonPath("$.name").value(uploaded.institutionName()))
                        .andExpect(jsonPath("$.start_url").value("/"))
                        .andExpect(jsonPath("$.icons[0].src").value("/api/v1/public/settings/app-icon/192.png?v=" + uploaded.version()))
                        .andExpect(jsonPath("$.icons[1].sizes").value("512x512"));
                for (int size : new int[]{32, 180, 192, 512}) {
                    var response = mvc.perform(get("/api/v1/public/settings/app-icon/" + size + ".png"))
                            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-cache"))
                            .andReturn().getResponse().getContentAsByteArray();
                    var icon = ImageIO.read(new ByteArrayInputStream(response));
                    assertThat(icon.getWidth()).isEqualTo(size);
                    assertThat(icon.getHeight()).isEqualTo(size);
                    assertThat(icon.getRGB(size / 2, size / 2)).isEqualTo(color.getRGB());
                    assertThat(icon.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
                    // The wide logo stays wide, with white space above it.
                    assertThat(icon.getRGB(size / 2, size / 4)).isEqualTo(Color.WHITE.getRGB());
                    assertThat(icon.getRGB(size / 3, size / 2)).isEqualTo(color.getRGB());
                }
            }
            settings.updateSchoolLogo(null, settings.adminSettings().version(), null);
            mvc.perform(get("/api/v1/public/settings/app-icon/180.png"))
                    .andExpect(status().isOk()).andExpect(content().bytes(fallback));
            mvc.perform(get("/api/v1/public/settings/app-icon/99999.png")).andExpect(status().isNotFound());
        } finally {
            settings.updateSchoolLogo(null, settings.adminSettings().version(), null);
        }
    }

    @Test
    void transparentLogoHasAnOpaqueWhiteBackground() throws Exception {
        var logo = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        var icon = ImageIO.read(new ByteArrayInputStream(AppIconService.render(logo, 192)));
        assertThat(icon.getColorModel().hasAlpha()).isFalse();
        assertThat(icon.getRGB(96, 96)).isEqualTo(Color.WHITE.getRGB());
    }
}
