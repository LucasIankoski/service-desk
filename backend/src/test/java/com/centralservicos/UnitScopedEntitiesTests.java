package com.centralservicos;

import jakarta.persistence.Entity;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda entidade nova pertence a uma unidade, a menos que seja declarada global aqui. Esquecer o {@code @TenantId}
 * expõe os dados para todas as unidades, por isso a exceção precisa ser explícita.
 */
class UnitScopedEntitiesTests {

    /** Entidades da rede, ou com a unidade tratada explicitamente no código. */
    private static final Set<String> NETWORK_OR_EXPLICIT = Set.of(
            "com.centralservicos.identity.UserAccount",
            "com.centralservicos.identity.Unit",
            "com.centralservicos.identity.PasswordResetToken",
            "com.centralservicos.settings.AppSettings",
            "com.centralservicos.audit.AuditEvent",
            "com.centralservicos.notifications.Notification",
            "com.centralservicos.tickets.TicketCounter");

    @Test
    void everyEntityIsUnitScopedOrDeclaredGlobal() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        var missing = new TreeSet<String>();
        var found = new TreeSet<String>();
        for (var candidate : scanner.findCandidateComponents("com.centralservicos")) {
            var type = Class.forName(candidate.getBeanClassName());
            found.add(type.getName());
            var scoped = Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.isAnnotationPresent(TenantId.class));
            if (!scoped && !NETWORK_OR_EXPLICIT.contains(type.getName())) {
                missing.add(type.getName());
            }
        }
        assertThat(found).contains("com.centralservicos.tickets.Ticket", "com.centralservicos.pedagogical.AeeStudent");
        assertThat(missing).as("Entidades sem @TenantId nem declaração de escopo global").isEmpty();
    }
}
