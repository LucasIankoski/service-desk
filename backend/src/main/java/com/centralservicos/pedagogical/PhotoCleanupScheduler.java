package com.centralservicos.pedagogical;

import com.centralservicos.shared.TenantContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Executa a limpeza como sistema, antes de a transação abrir, para que a fila e as referências de todas as
 * unidades sejam consideradas.
 */
@Component
class PhotoCleanupScheduler {

    private final PhotoCleanupService cleanup;

    PhotoCleanupScheduler(PhotoCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(fixedDelayString = "${app.photo-cleanup-delay-ms:60000}")
    void run() {
        try (var ignored = TenantContext.asSystem()) {
            cleanup.clean();
        }
    }
}
