package com.centralservicos.identity;

public record PasswordResetRequested(String recipientEmail, String recipientName, String rawToken) {
}
