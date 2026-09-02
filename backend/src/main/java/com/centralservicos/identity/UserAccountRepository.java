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

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Page<UserAccount> findAllByOrderByDisplayNameAsc(Pageable pageable);

    @Query("select count(distinct u) from UserAccount u join u.roles r where u.active = true and r = :role")
    long countActiveWithRole(Role role);

    @Query("select distinct u.id from UserAccount u join u.roles r where u.active = true and r in :roles")
    List<UUID> findActiveIdsWithAnyRole(Set<Role> roles);

    List<UserAccount> findAllByIdIn(Collection<UUID> ids);
}
