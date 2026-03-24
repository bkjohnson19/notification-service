package dev.bkjohnson.notification.repository;

import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for webhook subscription persistence.
 */
public interface WebhookSubscriptionRepository
    extends JpaRepository<WebhookSubscription, String>,
    JpaSpecificationExecutor<WebhookSubscription> {
}
