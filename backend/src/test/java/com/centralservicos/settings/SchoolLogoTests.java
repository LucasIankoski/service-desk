package com.centralservicos.settings;

import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SchoolLogoTests {
    @Autowired SettingsService settings;
    @Test void uploadReadVersionConflictAndRemoval() throws Exception {
        var image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6nAAAAABJRU5ErkJggg==");
        var before = settings.adminSettings();
        var uploaded = settings.updateSchoolLogo(new MockMultipartFile("file","logo.png","image/png",image),before.version(),null);
        assertThat(uploaded.schoolLogoUrl()).contains("/school-logo?v=");
        assertThat(settings.publicSettings().schoolLogoUrl()).isEqualTo(uploaded.schoolLogoUrl());
        assertThat(settings.loadSchoolLogo().resource().getInputStream().readAllBytes()).isEqualTo(image);
        assertThatThrownBy(() -> settings.updateSchoolLogo(null,before.version(),null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> settings.updateSchoolLogo(new MockMultipartFile("file","bad.svg","image/svg+xml","<svg/>".getBytes()),uploaded.version(),null)).isInstanceOf(DomainException.class);
        var removed = settings.updateSchoolLogo(null,uploaded.version(),null);
        assertThat(removed.schoolLogoUrl()).isNull();
        assertThatThrownBy(() -> settings.loadSchoolLogo()).isInstanceOf(DomainException.class);
    }
}
