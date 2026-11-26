package com.centralservicos.shared;

import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Unidade escolar ativa na thread atual.
 *
 * <p>O Hibernate lê este valor ao abrir cada sessão para filtrar as entidades com {@code @TenantId}. Por isso a
 * unidade precisa estar definida antes de a transação começar. Sem unidade, o identificador é {@link #NONE}: as
 * consultas não retornam linhas e as inserções falham na chave estrangeira, nunca expondo outra unidade.</p>
 */
public final class TenantContext {

    /** Identificador que não corresponde a nenhuma unidade. */
    public static final UUID NONE = new UUID(0L, 0L);
    /** Tenant raiz do Hibernate, reservado para rotinas do sistema que precisam enxergar todas as unidades. */
    public static final UUID SYSTEM = new UUID(-1L, -1L);

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> unitId() {
        var current = CURRENT.get();
        return current == null || SYSTEM.equals(current) ? Optional.empty() : Optional.of(current);
    }

    public static UUID requiredUnitId() {
        return unitId().orElseThrow(() -> new DomainException(HttpStatus.BAD_REQUEST, "Selecione a unidade."));
    }

    public static boolean system() {
        return SYSTEM.equals(CURRENT.get());
    }

    static UUID tenantIdentifier() {
        var current = CURRENT.get();
        return current == null ? NONE : current;
    }

    /** Define a unidade até o fechamento do escopo, restaurando a anterior em seguida. */
    public static Scope enter(UUID unitId) {
        return swap(Objects.requireNonNull(unitId, "unitId"));
    }

    /** Executa como sistema, sem filtro de unidade. Use apenas em rotinas internas. */
    public static Scope asSystem() {
        return swap(SYSTEM);
    }

    /** Remove a unidade até o fechamento do escopo. */
    public static Scope clear() {
        return swap(null);
    }

    private static Scope swap(UUID next) {
        var previous = CURRENT.get();
        set(next);
        return () -> set(previous);
    }

    private static void set(UUID value) {
        if (value == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(value);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
