package com.centralservicos.identity;

import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

@Service
public class IdentityService {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    IdentityService(UserAccountRepository users, PasswordResetTokenRepository resetTokens,
                    PasswordEncoder passwordEncoder, AuditService audit, ApplicationEventPublisher events) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadForAuthentication(String rawEmail) {
        var user = users.findByEmailIgnoreCase(normalizeEmail(rawEmail))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
        return principal(user);
    }

    @Transactional(readOnly = true)
    public UserView find(UUID id) {
        return requiredUser(id).toView();
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(Pageable pageable) {
        return users.findAllByOrderByDisplayNameAsc(pageable).map(UserAccount::toView);
    }

    @Transactional(readOnly = true)
    public List<UUID> activeUserIdsWithAnyRole(Set<Role> roles) {
        return users.findActiveIdsWithAnyRole(roles);
    }

    @Transactional(readOnly = true)
    public boolean activeUserHasAnyRole(UUID id, Set<Role> roles) {
        return users.findById(id)
                .filter(UserAccount::active)
                .map(user -> user.roles().stream().anyMatch(roles::contains))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        var names = new HashMap<UUID, String>();
        users.findAllByIdIn(ids).forEach(user -> names.put(user.id(), user.displayName()));
        return names;
    }

    @Transactional(readOnly = true)
    public List<AssigneeView> activeAssignees() {
        var ids = users.findActiveIdsWithAnyRole(Set.of(Role.AGENT, Role.MANAGER));
        return users.findAllByIdIn(ids).stream()
                .map(user -> new AssigneeView(user.id(), user.displayName()))
                .sorted(Comparator.comparing(AssigneeView::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser refreshForSession(UUID id) {
        return users.findById(id).map(this::principal).orElse(null);
    }

    @Transactional
    public CreatedUser create(String email, String displayName, Set<Role> roles, UUID actorId) {
        var normalized = normalizeEmail(email);
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw DomainException.conflict("Já existe uma conta com este e-mail.");
        }
        if (roles.isEmpty()) {
            throw DomainException.unprocessable("Selecione ao menos um perfil.");
        }
        var temporaryPassword = temporaryPassword();
        var user = users.save(new UserAccount(normalized, displayName.trim(),
                passwordEncoder.encode(temporaryPassword), roles));
        audit.record(actorId, "USER_CREATED", "UserAccount", user.id(), "{\"roles\":\"" + roles + "\"}");
        return new CreatedUser(user.toView(), temporaryPassword);
    }

    @Transactional
    public UserView update(UUID id, String displayName, Set<Role> roles, boolean active, UUID actorId) {
        var user = requiredUser(id);
        if (roles.isEmpty()) {
            throw DomainException.unprocessable("Selecione ao menos um perfil.");
        }
        ensureAdministratorRemains(user, roles, active);
        user.update(displayName.trim(), roles, active);
        audit.record(actorId, "USER_UPDATED", "UserAccount", id, "{\"active\":" + active + "}");
        return user.toView();
    }

    @Transactional
    public String adminReset(UUID id, UUID actorId) {
        var user = requiredUser(id);
        var temporaryPassword = temporaryPassword();
        user.changePassword(passwordEncoder.encode(temporaryPassword), true);
        resetTokens.deleteByUserId(id);
        audit.record(actorId, "USER_PASSWORD_RESET", "UserAccount", id, null);
        return temporaryPassword;
    }

    @Transactional
    public void setBootstrapPassword(UUID id, String password) {
        validatePassword(password);
        var user = requiredUser(id);
        user.changePassword(passwordEncoder.encode(password), true);
        audit.record(id, "BOOTSTRAP_PASSWORD_SET", "UserAccount", id, null);
    }

    @Transactional
    public void anonymize(UUID id, UUID actorId) {
        if (id.equals(actorId)) {
            throw DomainException.unprocessable("Você não pode anonimizar a própria conta.");
        }
        var user = requiredUser(id);
        ensureAdministratorRemains(user, Set.of(), false);
        user.anonymize("anonymized+" + id + "@invalid.local", passwordEncoder.encode(temporaryPassword()));
        resetTokens.deleteByUserId(id);
        audit.record(actorId, "USER_ANONYMIZED", "UserAccount", id, null);
    }

    @Transactional
    public void changePassword(UUID userId, String newPassword) {
        validatePassword(newPassword);
        var user = requiredUser(userId);
        user.changePassword(passwordEncoder.encode(newPassword), false);
        resetTokens.deleteByUserId(userId);
        audit.record(userId, "PASSWORD_CHANGED", "UserAccount", userId, null);
    }

    @Transactional
    public void requestReset(String rawEmail) {
        users.findByEmailIgnoreCase(normalizeEmail(rawEmail)).filter(UserAccount::active).ifPresent(user -> {
            resetTokens.deleteByUserId(user.id());
            var rawToken = randomToken();
            resetTokens.save(new PasswordResetToken(user.id(), sha256(rawToken)));
            events.publishEvent(new PasswordResetRequested(user.email(), user.displayName(), rawToken));
            audit.record(user.id(), "PASSWORD_RESET_REQUESTED", "UserAccount", user.id(), null);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        var token = resetTokens.findByTokenHash(sha256(rawToken))
                .filter(PasswordResetToken::validNow)
                .orElseThrow(() -> new DomainException(HttpStatus.BAD_REQUEST, "O link é inválido ou expirou."));
        var user = requiredUser(token.userId());
        user.changePassword(passwordEncoder.encode(newPassword), false);
        token.use();
        audit.record(user.id(), "PASSWORD_RESET_COMPLETED", "UserAccount", user.id(), null);
    }

    @Transactional
    public void recordAuthentication(UUID actorId, String action) {
        audit.record(actorId, action, "Authentication", actorId, null);
    }

    @Transactional(readOnly = true)
    public boolean hasAdministrator() {
        return users.countActiveWithRole(Role.ADMIN) > 0;
    }

    private void ensureAdministratorRemains(UserAccount user, Set<Role> nextRoles, boolean nextActive) {
        var removesActiveAdministrator = user.active() && user.roles().contains(Role.ADMIN)
                && (!nextActive || !nextRoles.contains(Role.ADMIN));
        if (removesActiveAdministrator && users.countActiveWithRole(Role.ADMIN) <= 1) {
            throw DomainException.unprocessable("Mantenha ao menos um administrador ativo.");
        }
    }

    private UserAccount requiredUser(UUID id) {
        return users.findById(id).orElseThrow(() -> DomainException.notFound("Usuário não encontrado."));
    }

    private AuthenticatedUser principal(UserAccount user) {
        return new AuthenticatedUser(user.id(), user.email(), user.displayName(), user.passwordHash(),
                user.roles(), user.active(), user.passwordChangeRequired());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw DomainException.unprocessable("A senha deve ter entre 12 e 128 caracteres.");
        }
    }

    private String temporaryPassword() {
        var value = new StringBuilder(20);
        for (int index = 0; index < 20; index++) {
            value.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return value.toString();
    }

    private String randomToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    public record CreatedUser(UserView user, String temporaryPassword) {
    }
}
