package com.centralservicos.settings;

import java.time.Instant;

public record AdminSettingsView(String institutionName, String supportEmail, String supportPhone,
                                String timezoneName, int attachmentLimitMb, int reopenDays,
                                int deadlineWarningHours, ThemeView theme, boolean loginBackgroundConfigured,
                                SmtpView smtp, long version, Instant updatedAt) {

    public record SmtpView(String host, Integer port, boolean tls, String fromName, String fromAddress,
                           String username, boolean passwordConfigured) {
    }
}
