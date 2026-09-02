package com.centralservicos.identity;

import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityServiceTests {

    @Test
    void soleActiveAdministratorCannotLoseAdministrativeAccess() {
        var users = mock(UserAccountRepository.class);
        var account = new UserAccount("admin@example.test", "Administrator", "hash", Set.of(Role.ADMIN));
        when(users.findById(account.id())).thenReturn(Optional.of(account));
        when(users.countActiveWithRole(Role.ADMIN)).thenReturn(1L);
        var identity = new IdentityService(users, mock(PasswordResetTokenRepository.class),
                mock(PasswordEncoder.class), mock(AuditService.class), mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> identity.update(account.id(), "Administrator", Set.of(Role.REQUESTER),
                true, account.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("administrador ativo");
    }
}
