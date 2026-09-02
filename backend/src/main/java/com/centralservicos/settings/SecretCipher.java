package com.centralservicos.settings;

public interface SecretCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
