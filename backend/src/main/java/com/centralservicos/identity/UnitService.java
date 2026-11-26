package com.centralservicos.identity;

import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Cadastro das unidades da rede. Apenas ADMIN chega aqui, pelas rotas {@code /api/v1/admin/**}. */
@Service
public class UnitService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]{2,8}$");
    private final UnitRepository units;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    UnitService(UnitRepository units, AuditService audit, ApplicationEventPublisher events) {
        this.units = units;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<UnitView> all() {
        return units.findAllByOrderByNameAsc().stream().map(Unit::toView).toList();
    }

    @Transactional(readOnly = true)
    public UnitView find(UUID id) {
        return required(id).toView();
    }

    /** Código curto usado no número dos chamados, como {@code SD-2026-000001}. */
    @Transactional(readOnly = true)
    public String code(UUID id) {
        return required(id).code();
    }

    @Transactional
    public UnitView create(String code, String name, String supportEmail, String supportPhone,
                           UUID copyConfigurationFrom, UUID actorId) {
        var normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalizedCode).matches()) {
            throw DomainException.unprocessable("Use um código com 2 a 8 letras maiúsculas ou números.");
        }
        if (units.existsByCodeIgnoreCase(normalizedCode)) {
            throw DomainException.conflict("Já existe uma unidade com este código.");
        }
        var normalizedName = validName(name);
        if (units.existsByNameIgnoreCase(normalizedName)) {
            throw DomainException.conflict("Já existe uma unidade com este nome.");
        }
        if (copyConfigurationFrom != null && !units.existsById(copyConfigurationFrom)) {
            throw DomainException.unprocessable("Unidade de origem inválida.");
        }
        var unit = units.save(new Unit(normalizedCode, normalizedName, validEmail(supportEmail),
                validPhone(supportPhone)));
        audit.record(actorId, "UNIT_CREATED", "Unit", unit.id(), "{\"code\":\"" + normalizedCode + "\"}");
        events.publishEvent(new UnitCreated(unit.id(), copyConfigurationFrom, actorId));
        return unit.toView();
    }

    @Transactional
    public UnitView update(UUID id, String name, String supportEmail, String supportPhone, boolean active,
                           Long version, UUID actorId) {
        var unit = required(id);
        if (version == null || !Objects.equals(unit.rowVersion(), version)) {
            throw DomainException.conflict("Esta unidade mudou. Recarregue antes de salvar.");
        }
        var normalizedName = validName(name);
        if (units.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw DomainException.conflict("Já existe uma unidade com este nome.");
        }
        if (unit.active() && !active && units.countByActiveTrue() <= 1) {
            throw DomainException.unprocessable("Mantenha ao menos uma unidade ativa.");
        }
        unit.update(normalizedName, validEmail(supportEmail), validPhone(supportPhone), active);
        units.flush();
        audit.record(actorId, "UNIT_UPDATED", "Unit", id, "{\"active\":" + active + "}");
        return unit.toView();
    }

    private Unit required(UUID id) {
        return units.findById(id).orElseThrow(() -> DomainException.notFound("Unidade não encontrada."));
    }

    private String validName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 160) {
            throw DomainException.unprocessable("Informe o nome da unidade com até 160 caracteres.");
        }
        return name.trim();
    }

    private String validEmail(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.trim().length() > 254) throw new IllegalArgumentException();
            new InternetAddress(value.trim(), true);
            return value.trim();
        } catch (Exception exception) {
            throw DomainException.unprocessable("Informe um e-mail de suporte válido.");
        }
    }

    private String validPhone(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > 40) {
            throw DomainException.unprocessable("Informe um telefone com até 40 caracteres.");
        }
        return value.trim();
    }
}
