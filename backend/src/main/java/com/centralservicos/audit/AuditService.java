package com.centralservicos.audit;

import com.centralservicos.shared.CorrelationIdFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String entityType, Object entityId, String safeDetails) {
        var attributes = RequestContextHolder.getRequestAttributes();
        var correlationId = attributes instanceof ServletRequestAttributes servletAttributes
                ? (String) servletAttributes.getRequest().getAttribute(CorrelationIdFilter.ATTRIBUTE)
                : null;
        repository.save(new AuditEvent(actorId, action, entityType,
                entityId == null ? null : entityId.toString(), safeDetails, correlationId));
    }

    @Transactional(readOnly = true)
    public Page<AuditView> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(AuditEvent::toView);
    }
}
