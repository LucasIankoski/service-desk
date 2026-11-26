package com.centralservicos.identity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    interface UserListProjection {
        UUID getId();
        String getEmail();
        String getDisplayName();
        boolean getActive();
        boolean getPasswordChangeRequired();
        boolean getAnonymized();
        Instant getCreatedAt();
    }

    interface UserRoleProjection {
        UUID getUserId();
        Role getRole();
    }

    interface MembershipProjection {
        UUID getUserId();
        UUID getUnitId();
        Role getRole();
    }

    // Keep legacy role names out of enum hydration, without changing stored permissions.
    @Query(value = "select u.id as id, u.email as email, u.displayName as displayName, "
            + "u.active as active, u.passwordChangeRequired as passwordChangeRequired, "
            + "u.anonymized as anonymized, u.createdAt as createdAt from UserAccount u order by u.displayName, u.id",
            countQuery = "select count(u) from UserAccount u")
    Page<UserListProjection> findUserPage(Pageable pageable);

    @Query("select u.id as userId, r as role from UserAccount u join u.roles r where u.id in :ids and r in :roles")
    List<UserRoleProjection> findSupportedRoles(Collection<UUID> ids, Set<Role> roles);

    @Query("select u.id as userId, m.unitId as unitId, m.role as role from UserAccount u join u.memberships m "
            + "where u.id in :ids")
    List<MembershipProjection> findMemberships(Collection<UUID> ids);

    @Query("select count(distinct u) from UserAccount u join u.roles r where u.active = true and r = :role")
    long countActiveWithRole(Role role);

    @Query("select distinct u.id from UserAccount u join u.roles r where u.active = true and r in :roles")
    List<UUID> findActiveIdsWithAnyRole(Set<Role> roles);

    @Query("select distinct u.id from UserAccount u join u.memberships m "
            + "where u.active = true and m.unitId = :unitId and m.role in :roles")
    List<UUID> findActiveMemberIds(UUID unitId, Set<Role> roles);

    List<UserAccount> findAllByIdIn(Collection<UUID> ids);

    interface CollaboratorProjection {
        UUID getId();
        String getDisplayName();
        boolean getActive();
    }
    interface NameProjection {
        UUID getId();
        String getDisplayName();
    }

    // Scalar projections deliberately avoid hydrating roles/passwords for labels and selectors.
    // Colaboradores da unidade: os vinculados a ela e os administradores da rede.
    @Query("select u.id as id, u.displayName as displayName, u.active as active from UserAccount u "
            + "where u.anonymized = false and (exists (select 1 from UserAccount x join x.memberships m "
            + "where x.id = u.id and m.unitId = :unitId) or exists (select 1 from UserAccount y join y.roles r "
            + "where y.id = u.id and r = :admin)) order by u.displayName, u.id")
    List<CollaboratorProjection> findUnitCollaborators(UUID unitId, Role admin);

    @Query("select u.id as id, u.displayName as displayName from UserAccount u where u.id in :ids")
    List<NameProjection> findNames(Collection<UUID> ids);

    @Query("select (count(u) > 0) from UserAccount u where u.id = :id and u.anonymized = false "
            + "and (exists (select 1 from UserAccount x join x.memberships m where x.id = u.id and m.unitId = :unitId) "
            + "or exists (select 1 from UserAccount y join y.roles r where y.id = u.id and r = :admin))")
    boolean existsUnitCollaborator(UUID id, UUID unitId, Role admin);
}
