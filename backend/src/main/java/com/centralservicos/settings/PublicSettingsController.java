package com.centralservicos.settings;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/public/settings")
class PublicSettingsController {

    private final SettingsService settings;

    PublicSettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    ResponseEntity<PublicSettingsView> settings() {
        var view = settings.publicSettings();
        return ResponseEntity.ok()
                .eTag("\"settings-" + view.version() + "\"")
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(view);
    }

    @GetMapping("/login-background")
    ResponseEntity<?> loginBackground() {
        var file = settings.loadLoginBackground();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, file.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"login-background\"")
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(file.resource());
    }
}
