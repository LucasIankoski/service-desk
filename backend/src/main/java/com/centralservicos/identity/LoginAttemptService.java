package com.centralservicos.identity;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final long BLOCK_SECONDS = 900;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    boolean blocked(String email) {
        var key = normalize(email);
        var attempt = attempts.get(key);
        if (attempt == null) return false;
        if (attempt.blockedUntil() != null && attempt.blockedUntil().isAfter(Instant.now())) return true;
        if (attempt.blockedUntil() != null) attempts.remove(key);
        return false;
    }

    void failed(String email) {
        var key = normalize(email);
        attempts.compute(key, (ignored, current) -> {
            var failures = current == null ? 1 : current.failures() + 1;
            return failures >= MAX_FAILURES
                    ? new Attempt(failures, Instant.now().plusSeconds(BLOCK_SECONDS))
                    : new Attempt(failures, null);
        });
    }

    void succeeded(String email) {
        attempts.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempt(int failures, Instant blockedUntil) {
    }
}
