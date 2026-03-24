package dev.bkjohnson.notification.repository;

import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for notification delivery persistence.
 */
public interface NotificationDeliveryRepository
    extends JpaRepository<NotificationDelivery, String>,
    JpaSpecificationExecutor<NotificationDelivery> {

  Optional<NotificationDelivery> findByTenantIdAndIdempotencyKey(
      String tenantId, String idempotencyKey);
}
