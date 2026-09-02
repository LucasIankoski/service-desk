package com.centralservicos.settings;

import java.util.List;

public record ThemeValidation(ThemeView theme, List<String> warnings) {
    public boolean valid() {
        return warnings.isEmpty();
    }
}
