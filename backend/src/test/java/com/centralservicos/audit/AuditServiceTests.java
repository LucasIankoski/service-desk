package com.centralservicos.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTests {

    @Test
    void recordsEventsOutsideAnHttpRequest() {
        var repository = mock(AuditEventRepository.class);
        var service = new AuditService(repository);

        service.record(UUID.randomUUID(), "USER_CREATED", "UserAccount", UUID.randomUUID(), null);

        verify(repository).save(any(AuditEvent.class));
    }
}
