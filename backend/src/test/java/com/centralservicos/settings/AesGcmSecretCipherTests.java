package com.centralservicos.settings;

import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretCipherTests {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void secretRoundTripsWithoutAppearingInCiphertext() {
        var cipher = new AesGcmSecretCipher(KEY);

        var encrypted = cipher.encrypt("smtp-password-123");

        assertThat(encrypted).startsWith("v1:").doesNotContain("smtp-password-123");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("smtp-password-123");
    }

    @Test
    void tamperedCiphertextIsRejected() {
        var cipher = new AesGcmSecretCipher(KEY);
        var encrypted = cipher.encrypt("smtp-password-123");
        var tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Não foi possível ler o segredo");
    }

    @Test
    void missingMasterKeyFailsClosed() {
        var cipher = new AesGcmSecretCipher("");

        assertThatThrownBy(() -> cipher.encrypt("smtp-password-123"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");
    }
}
