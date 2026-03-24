package dev.bkjohnson.notification.repository;

import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for notification template persistence.
 */
public interface NotificationTemplateRepository
    extends JpaRepository<NotificationTemplate, String>,
    JpaSpecificationExecutor<NotificationTemplate> {

  Optional<NotificationTemplate> findByTenantIdAndNameAndDeletedAtIsNull(
      String tenantId, String name);

  boolean existsByTenantIdAndNameAndDeletedAtIsNull(String tenantId, String name);
}
