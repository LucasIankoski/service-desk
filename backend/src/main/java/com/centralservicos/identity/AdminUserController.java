package com.centralservicos.identity;

import com.centralservicos.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

    private final IdentityService identity;
    private final CurrentUser currentUser;

    AdminUserController(IdentityService identity, CurrentUser currentUser) {
        this.identity = identity;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<UserView> list(Pageable pageable) {
        return PageResponse.from(identity.list(pageable));
    }

    @PostMapping
    IdentityService.CreatedUser create(@Valid @RequestBody CreateUserRequest request) {
        if (request.units() == null) {
            return identity.create(request.email(), request.displayName(), roles(request.roles()), currentUser.id());
        }
        return identity.create(request.email(), request.displayName(), request.admin(), unitRoles(request.units()),
                currentUser.id());
    }

    @PatchMapping("/{id}")
    UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        if (request.units() == null) {
            return identity.update(id, request.displayName(), roles(request.roles()), request.active(),
                    currentUser.id());
        }
        return identity.update(id, request.displayName(), request.admin(), unitRoles(request.units()),
                request.active(), currentUser.id());
    }

    @PostMapping("/{id}/temporary-password")
    Map<String, String> reset(@PathVariable UUID id) {
        return Map.of("temporaryPassword", identity.adminReset(id, currentUser.id()));
    }

    @PostMapping("/{id}/anonymize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void anonymize(@PathVariable UUID id) {
        identity.anonymize(id, currentUser.id());
    }

    private static Set<Role> roles(Set<Role> roles) {
        return roles == null ? Set.of() : roles;
    }

    private static Map<UUID, Set<Role>> unitRoles(List<UnitRolesRequest> units) {
        var byUnit = new HashMap<UUID, Set<Role>>();
        units.stream().filter(unit -> unit != null && unit.unitId() != null).forEach(unit ->
                byUnit.put(unit.unitId(), roles(unit.roles())));
        return byUnit;
    }

    /**
     * {@code units} define os perfis em cada unidade e {@code admin} o acesso à rede inteira. Sem {@code units},
     * vale o formato anterior: {@code roles} na unidade ativa, com ADMIN tratado como perfil global.
     */
    record CreateUserRequest(@NotBlank @Email String email,
                             @NotBlank @Size(max = 120) String displayName,
                             Set<Role> roles,
                             boolean admin,
                             List<UnitRolesRequest> units) { }
    record UpdateUserRequest(@NotBlank @Size(max = 120) String displayName,
                             Set<Role> roles,
                             boolean active,
                             boolean admin,
                             List<UnitRolesRequest> units) { }
    record UnitRolesRequest(UUID unitId, Set<Role> roles) { }
}
