package com.centralservicos.notifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Notificações são pessoais e mostradas de todas as unidades a que o usuário tem acesso. */
interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findAllByUserIdAndUnitIdInOrderByCreatedAtDesc(UUID userId, Collection<UUID> unitIds,
                                                                      Pageable pageable);
    long countByUserIdAndUnitIdInAndReadAtIsNull(UUID userId, Collection<UUID> unitIds);
    List<Notification> findAllByUserIdAndUnitIdInAndReadAtIsNull(UUID userId, Collection<UUID> unitIds);
}
