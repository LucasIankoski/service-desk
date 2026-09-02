package com.centralservicos.settings;

import com.centralservicos.shared.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
class AesGcmSecretCipher implements SecretCipher {

    private static final String KEY_ID = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    AesGcmSecretCipher(@Value("${app.encryption-key:}") String encodedKey) {
        this.key = decodeKey(encodedKey);
    }

    @Override
    public String encrypt(String plaintext) {
        requireKey();
        try {
            var iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return KEY_ID + ":" + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception exception) {
            throw new IllegalStateException("Falha ao criptografar segredo.", exception);
        }
    }

    @Override
    public String decrypt(String encrypted) {
        requireKey();
        try {
            var parts = encrypted.split(":", 3);
            if (parts.length != 3 || !KEY_ID.equals(parts[0])) {
                throw new IllegalArgumentException("Formato inválido");
            }
            var iv = Base64.getDecoder().decode(parts[1]);
            var ciphertext = Base64.getDecoder().decode(parts[2]);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível ler o segredo configurado.");
        }
    }

    private void requireKey() {
        if (key.length != 32) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure APP_ENCRYPTION_KEY com uma chave Base64 de 32 bytes.");
        }
    }

    private static byte[] decodeKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }
}
