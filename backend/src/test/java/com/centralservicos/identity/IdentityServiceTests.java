package com.centralservicos.identity;

import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityServiceTests {

    @Test
    void acceptsPasswordWithEightCharacters() {
        var users = mock(UserAccountRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var account = new UserAccount("user@example.test", "User", "old-hash", Set.of(Role.REQUESTER));
        when(users.findById(account.id())).thenReturn(Optional.of(account));
        when(encoder.encode("12345678")).thenReturn("new-hash");
        var identity = new IdentityService(users, mock(PasswordResetTokenRepository.class), encoder,
                mock(AuditService.class), mock(ApplicationEventPublisher.class));

        identity.changePassword(account.id(), "12345678");

        assertThat(account.passwordHash()).isEqualTo("new-hash");
        assertThat(account.passwordChangeRequired()).isFalse();
    }

    @Test
    void rejectsPasswordWithFewerThanEightCharacters() {
        var identity = new IdentityService(mock(UserAccountRepository.class),
                mock(PasswordResetTokenRepository.class), mock(PasswordEncoder.class), mock(AuditService.class),
                mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> identity.changePassword(UUID.randomUUID(), "1234567"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("entre 8 e 128 caracteres");
    }

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
