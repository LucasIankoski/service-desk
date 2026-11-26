package com.centralservicos.tickets;

import com.centralservicos.identity.UnitCreated;
import com.centralservicos.shared.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Copia as categorias ativas de uma unidade para a unidade recém-criada. Cada leitura e escrita roda numa
 * transação nova, aberta já com a unidade correta no {@link TenantContext}.
 */
@Component
class UnitCategorySeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnitCategorySeeder.class);
    private final CategoryService categories;
    private final TransactionTemplate transaction;

    UnitCategorySeeder(CategoryService categories, PlatformTransactionManager transactionManager) {
        this.categories = categories;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUnitCreated(UnitCreated event) {
        if (event.copyConfigurationFrom() == null) {
            return;
        }
        try {
            List<String> names;
            try (var ignored = TenantContext.enter(event.copyConfigurationFrom())) {
                names = transaction.execute(status -> categories.active().stream().map(CategoryView::name).toList());
            }
            try (var ignored = TenantContext.enter(event.unitId())) {
                transaction.executeWithoutResult(status -> names.forEach(name ->
                        categories.createIfMissing(name, event.actorId())));
            }
        } catch (RuntimeException exception) {
            // A unidade já foi criada; as categorias podem ser cadastradas manualmente.
            LOGGER.warn("Não foi possível copiar as categorias para a nova unidade.", exception);
        }
    }
}
