package com.centralservicos.settings;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/settings")
class AppIconController {
    private final SettingsService settings;
    private final AppIconService icons;

    AppIconController(SettingsService settings, AppIconService icons) {
        this.settings = settings;
        this.icons = icons;
    }

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    ResponseEntity<Map<String, Object>> manifest() {
        var view = settings.publicSettings();
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; frame-ancestors 'none'")
                .body(Map.of(
                        "id", "/", "name", view.institutionName(), "short_name", view.institutionName(),
                        "start_url", "/", "scope", "/", "display", "standalone",
                        "background_color", "#ffffff", "theme_color", view.theme().primaryColor(),
                        "icons", List.of(manifestIcon(192, view.version()), manifestIcon(512, view.version()))));
    }

    private Map<String, Object> manifestIcon(int size, long version) {
        return Map.of("src", "/api/v1/public/settings/app-icon/" + size + ".png?v=" + version,
                "sizes", size + "x" + size, "type", "image/png", "purpose", "any maskable");
    }

    @GetMapping(value = "/app-icon/{size}.png", produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> icon(@PathVariable int size) throws IOException {
        if (size != 32 && size != 180 && size != 192 && size != 512) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(icons.icon(size));
    }
}
