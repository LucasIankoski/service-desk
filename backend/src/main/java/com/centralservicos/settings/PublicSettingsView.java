package com.centralservicos.settings;

import java.time.Instant;

public record PublicSettingsView(String institutionName, String supportEmail, String supportPhone,
                                 String timezoneName, ThemeView theme, String loginBackgroundUrl,
                                 long version, Instant updatedAt) {
}
