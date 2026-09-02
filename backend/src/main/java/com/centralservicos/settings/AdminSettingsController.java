package com.centralservicos.settings;

import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/settings")
class AdminSettingsController {

    private final SettingsService settings;
    private final SmtpService smtp;
    private final CurrentUser currentUser;

    AdminSettingsController(SettingsService settings, SmtpService smtp, CurrentUser currentUser) {
        this.settings = settings;
        this.smtp = smtp;
        this.currentUser = currentUser;
    }

    @GetMapping
    AdminSettingsView get() {
        return settings.adminSettings();
    }

    @PatchMapping("/general")
    AdminSettingsView general(@Valid @org.springframework.web.bind.annotation.RequestBody
                              SettingsService.UpdateGeneralRequest request) {
        return settings.updateGeneral(request, currentUser.id());
    }

    @PostMapping("/theme/preview")
    ThemeValidation preview(@Valid @org.springframework.web.bind.annotation.RequestBody ThemeView theme) {
        return settings.previewTheme(theme);
    }

    @PatchMapping("/theme")
    AdminSettingsView theme(@Valid @org.springframework.web.bind.annotation.RequestBody
                            SettingsService.UpdateThemeRequest request) {
        return settings.updateTheme(request, currentUser.id());
    }

    @PostMapping("/login-background")
    AdminSettingsView loginBackground(@RequestPart("file") MultipartFile file) {
        return settings.updateLoginBackground(file, currentUser.id());
    }

    @PatchMapping("/smtp")
    AdminSettingsView smtp(@Valid @org.springframework.web.bind.annotation.RequestBody
                           SettingsService.UpdateSmtpRequest request) {
        return settings.updateSmtp(request, currentUser.id());
    }

    @PostMapping("/smtp/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void testSmtp() {
        smtp.testConnection();
    }
}
