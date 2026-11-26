package com.centralservicos.identity;

import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import com.centralservicos.shared.TenantContext;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.EnumSet;

/**
 * Contas e perfis. ADMIN é global e acessa todas as unidades ativas; os demais perfis valem por unidade e são
 * avaliados na unidade do {@link TenantContext}.
 */
@Service
public class IdentityService {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final Set<Role> UNIT_ROLES = EnumSet.of(Role.REQUESTER, Role.AGENT, Role.MANAGER);
    private final UserAccountRepository users;
    private final UnitRepository units;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    IdentityService(UserAccountRepository users, UnitRepository units, PasswordResetTokenRepository resetTokens,
                    PasswordEncoder passwordEncoder, AuditService audit, ApplicationEventPublisher events) {
        this.users = users;
        this.units = units;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadForAuthentication(String rawEmail) {
        var user = users.findByEmailIgnoreCase(normalizeEmail(rawEmail))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
        return principal(user, null);
    }

    @Transactional(readOnly = true)
    public UserView find(UUID id) {
        return requiredUser(id).toView(currentUnit());
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(Pageable pageable) {
        var page = users.findUserPage(pageable);
        var globalRoles = new HashMap<UUID, Set<Role>>();
        var unitRoles = new HashMap<UUID, Map<UUID, Set<Role>>>();
        if (!page.isEmpty()) {
            var ids = page.getContent().stream().map(UserAccountRepository.UserListProjection::getId).toList();
            users.findSupportedRoles(ids, Set.of(Role.ADMIN)).forEach(row ->
                    globalRoles.computeIfAbsent(row.getUserId(), ignored -> EnumSet.noneOf(Role.class)).add(row.getRole()));
            users.findMemberships(ids).forEach(row -> unitRoles
                    .computeIfAbsent(row.getUserId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getUnitId(), ignored -> EnumSet.noneOf(Role.class)).add(row.getRole()));
        }
        var activeUnit = currentUnit();
        return page.map(user -> {
            var admin = globalRoles.getOrDefault(user.getId(), Set.of()).contains(Role.ADMIN);
            var byUnit = unitRoles.getOrDefault(user.getId(), Map.of());
            var effective = EnumSet.noneOf(Role.class);
            if (admin) effective.add(Role.ADMIN);
            if (activeUnit != null) effective.addAll(byUnit.getOrDefault(activeUnit, Set.of()));
            var memberships = byUnit.entrySet().stream()
                    .map(entry -> new UserView.UnitRoles(entry.getKey(), Set.copyOf(entry.getValue())))
                    .sorted(Comparator.comparing(unit -> unit.unitId().toString()))
                    .toList();
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), Set.copyOf(effective),
                    user.getActive(), user.getPasswordChangeRequired(), user.getAnonymized(), user.getCreatedAt(),
                    admin, memberships);
        });
    }

    public record CollaboratorView(UUID id, String displayName, boolean active) {}

    @Transactional(readOnly = true)
    public List<CollaboratorView> collaborators() {
        var unitId = currentUnit();
        if (unitId == null) return List.of();
        return users.findUnitCollaborators(unitId, Role.ADMIN).stream()
                .map(u -> new CollaboratorView(u.getId(), u.getDisplayName(), u.getActive())).toList();
    }

    @Transactional(readOnly = true)
    public boolean registeredCollaborator(UUID id) {
        var unitId = currentUnit();
        return unitId != null && users.existsUnitCollaborator(id, unitId, Role.ADMIN);
    }

    /** Usuários ativos com algum dos perfis na unidade atual. ADMIN, se pedido, inclui os administradores da rede. */
    @Transactional(readOnly = true)
    public List<UUID> activeUserIdsWithAnyRole(Set<Role> roles) {
        var ids = new HashSet<UUID>();
        if (roles.contains(Role.ADMIN)) {
            ids.addAll(users.findActiveIdsWithAnyRole(Set.of(Role.ADMIN)));
        }
        var unitRoles = EnumSet.noneOf(Role.class);
        roles.stream().filter(UNIT_ROLES::contains).forEach(unitRoles::add);
        var unitId = currentUnit();
        if (unitId != null && !unitRoles.isEmpty()) {
            ids.addAll(users.findActiveMemberIds(unitId, unitRoles));
        }
        return List.copyOf(ids);
    }

    /** Verifica os perfis do usuário na unidade atual. ADMIN vale em qualquer unidade. */
    @Transactional(readOnly = true)
    public boolean activeUserHasAnyRole(UUID id, Set<Role> roles) {
        var unitId = currentUnit();
        return users.findById(id)
                .filter(UserAccount::active)
                .map(user -> user.rolesIn(unitId).stream().anyMatch(roles::contains))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        var names = new HashMap<UUID, String>();
        users.findNames(ids).forEach(user -> names.put(user.getId(), user.getDisplayName()));
        return names;
    }

    @Transactional(readOnly = true)
    public List<AssigneeView> activeAssignees() {
        return assignees(Set.of(Role.AGENT, Role.MANAGER));
    }

    @Transactional(readOnly = true)
    public List<AssigneeView> activeRequesters() {
        return assignees(Set.of(Role.REQUESTER));
    }

    @Transactional(readOnly = true)
    public List<AssigneeView> activeManagers() {
        return assignees(Set.of(Role.MANAGER));
    }

    private List<AssigneeView> assignees(Set<Role> roles) {
        var ids = activeUserIdsWithAnyRole(roles);
        return users.findAllByIdIn(ids).stream()
                .map(user -> new AssigneeView(user.id(), user.displayName()))
                .sorted(Comparator.comparing(AssigneeView::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Estado da sessão para uma requisição. A unidade pedida só vale se o usuário tiver acesso a ela; sem pedido,
     * a única unidade disponível é escolhida. Com várias unidades e nenhum pedido, a unidade fica indefinida.
     */
    @Transactional(readOnly = true)
    public UnitSession session(UUID userId, UUID requestedUnitId) {
        var user = users.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        var accessible = accessibleUnits(user);
        UUID activeUnit = null;
        var denied = false;
        if (requestedUnitId != null) {
            if (accessible.stream().anyMatch(unit -> unit.id().equals(requestedUnitId))) {
                activeUnit = requestedUnitId;
            } else {
                denied = true;
            }
        }
        if (activeUnit == null && accessible.size() == 1) {
            activeUnit = accessible.getFirst().id();
        }
        var access = accessible.stream().map(unit -> new UnitSession.UnitAccess(unit.id(), unit.code(), unit.name(),
                unitOnly(user.rolesIn(unit.id())))).toList();
        return new UnitSession(principal(user, activeUnit), activeUnit, denied, user.admin(), access);
    }

    /** Unidades ativas que o usuário pode abrir: todas para ADMIN, as vinculadas para os demais. */
    @Transactional(readOnly = true)
    public Set<UUID> accessibleUnitIds(UUID userId) {
        return users.findById(userId).map(user -> {
            var ids = new HashSet<UUID>();
            accessibleUnits(user).forEach(unit -> ids.add(unit.id()));
            return Set.copyOf(ids);
        }).orElse(Set.of());
    }

    private List<Unit> accessibleUnits(UserAccount user) {
        if (!user.active()) {
            return List.of();
        }
        return user.admin() ? units.findAllByActiveTrueOrderByNameAsc()
                : units.findAllByIdInAndActiveTrueOrderByNameAsc(user.memberUnitIds());
    }

    /** Mantém a assinatura anterior: ADMIN vira perfil global e os demais perfis valem na unidade atual. */
    @Transactional
    public CreatedUser create(String email, String displayName, Set<Role> roles, UUID actorId) {
        return create(email, displayName, roles.contains(Role.ADMIN), unitRolesInCurrentUnit(roles), actorId);
    }

    @Transactional
    public CreatedUser create(String email, String displayName, boolean admin, Map<UUID, Set<Role>> unitRoles,
                              UUID actorId) {
        var normalized = normalizeEmail(email);
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw DomainException.conflict("Já existe uma conta com este e-mail.");
        }
        var memberships = validMemberships(unitRoles);
        if (!admin && memberships.isEmpty()) {
            throw DomainException.unprocessable("Selecione ao menos um perfil.");
        }
        var temporaryPassword = temporaryPassword();
        var user = users.save(new UserAccount(normalized, displayName.trim(),
                passwordEncoder.encode(temporaryPassword), admin, memberships));
        audit.record(actorId, "USER_CREATED", "UserAccount", user.id(), accessDetails(admin, memberships));
        return new CreatedUser(user.toView(currentUnit()), temporaryPassword);
    }

    /** Mantém a assinatura anterior: altera ADMIN e os perfis apenas da unidade atual. */
    @Transactional
    public UserView update(UUID id, String displayName, Set<Role> roles, boolean active, UUID actorId) {
        var user = requiredUser(id);
        var admin = roles.contains(Role.ADMIN);
        var unitId = currentUnit();
        var unitRoles = unitOnly(roles);
        if (unitId == null && !unitRoles.isEmpty()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Selecione a unidade.");
        }
        var next = new HashMap<>(user.unitRoles());
        if (unitId != null) {
            if (unitRoles.isEmpty()) next.remove(unitId); else next.put(unitId, unitRoles);
        }
        return update(user, displayName, admin, next, active, actorId);
    }

    @Transactional
    public UserView update(UUID id, String displayName, boolean admin, Map<UUID, Set<Role>> unitRoles,
                           boolean active, UUID actorId) {
        return update(requiredUser(id), displayName, admin, unitRoles, active, actorId);
    }

    private UserView update(UserAccount user, String displayName, boolean admin, Map<UUID, Set<Role>> unitRoles,
                            boolean active, UUID actorId) {
        var memberships = validMemberships(unitRoles);
        if (!admin && memberships.isEmpty()) {
            throw DomainException.unprocessable("Selecione ao menos um perfil.");
        }
        ensureAdministratorRemains(user, admin, active);
        user.updateProfile(displayName.trim(), active);
        user.grantAdmin(admin);
        user.replaceMemberships(memberships);
        audit.record(actorId, "USER_UPDATED", "UserAccount", user.id(),
                "{\"active\":" + active + ",\"admin\":" + admin + ",\"units\":" + memberships.size() + "}");
        return user.toView(currentUnit());
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
        ensureAdministratorRemains(user, false, false);
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

    private void ensureAdministratorRemains(UserAccount user, boolean nextAdmin, boolean nextActive) {
        var removesActiveAdministrator = user.active() && user.admin() && (!nextActive || !nextAdmin);
        if (removesActiveAdministrator && users.countActiveWithRole(Role.ADMIN) <= 1) {
            throw DomainException.unprocessable("Mantenha ao menos um administrador ativo.");
        }
    }

    private Map<UUID, Set<Role>> unitRolesInCurrentUnit(Set<Role> roles) {
        var unitRoles = unitOnly(roles);
        if (unitRoles.isEmpty()) {
            return Map.of();
        }
        var unitId = currentUnit();
        if (unitId == null) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Selecione a unidade.");
        }
        return Map.of(unitId, unitRoles);
    }

    private Map<UUID, Set<Role>> validMemberships(Map<UUID, Set<Role>> unitRoles) {
        var valid = new HashMap<UUID, Set<Role>>();
        if (unitRoles != null) {
            unitRoles.forEach((unitId, roles) -> {
                var onlyUnit = unitOnly(roles == null ? Set.of() : roles);
                if (unitId != null && !onlyUnit.isEmpty()) valid.put(unitId, onlyUnit);
            });
        }
        if (!valid.isEmpty() && units.findAllById(valid.keySet()).size() != valid.size()) {
            throw DomainException.unprocessable("Unidade inválida.");
        }
        return valid;
    }

    private static Set<Role> unitOnly(Set<Role> roles) {
        var filtered = EnumSet.noneOf(Role.class);
        roles.stream().filter(UNIT_ROLES::contains).forEach(filtered::add);
        return filtered;
    }

    private static String accessDetails(boolean admin, Map<UUID, Set<Role>> memberships) {
        var roles = EnumSet.noneOf(Role.class);
        if (admin) roles.add(Role.ADMIN);
        memberships.values().forEach(roles::addAll);
        return "{\"roles\":\"" + roles + "\",\"units\":" + memberships.size() + "}";
    }

    private UUID currentUnit() {
        return TenantContext.unitId().orElse(null);
    }

    private UserAccount requiredUser(UUID id) {
        return users.findById(id).orElseThrow(() -> DomainException.notFound("Usuário não encontrado."));
    }

    private AuthenticatedUser principal(UserAccount user, UUID unitId) {
        return new AuthenticatedUser(user.id(), user.email(), user.displayName(), user.passwordHash(),
                Set.copyOf(user.rolesIn(unitId)), user.active(), user.passwordChangeRequired());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw DomainException.unprocessable("A senha deve ter entre 8 e 128 caracteres.");
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
