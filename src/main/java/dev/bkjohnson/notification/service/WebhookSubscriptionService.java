package dev.bkjohnson.notification.service;

import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import dev.bkjohnson.notification.repository.WebhookSubscriptionRepository;
import dev.bkjohnson.notification.security.HmacSigner;
import dev.bkjohnson.notification.security.SsrfValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.api.exception.UnprocessableEntityException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing webhook subscriptions.
 */
@Service
public class WebhookSubscriptionService {

  private final WebhookSubscriptionRepository repository;
  private final SsrfValidator ssrfValidator;
  private final HmacSigner hmacSigner;
  private final ObjectMapper objectMapper;

  public WebhookSubscriptionService(WebhookSubscriptionRepository repository,
      SsrfValidator ssrfValidator, HmacSigner hmacSigner,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.ssrfValidator = ssrfValidator;
    this.hmacSigner = hmacSigner;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public Page<WebhookSubscription> listSubscriptions(Pageable pageable) {
    Specification<WebhookSubscription> spec = tenantScope().and(notDeleted());
    return repository.findAll(spec, pageable);
  }

  @Transactional(readOnly = true)
  public WebhookSubscription getSubscription(String subscriptionId) {
    return repository.findById(subscriptionId)
        .filter(s -> s.getDeletedAt() == null)
        .filter(s -> s.getTenantId().equals(currentTenantId()))
        .orElseThrow(() -> new NotFoundException(
            "Webhook subscription not found: " + subscriptionId));
  }

  @Transactional
  public WebhookSubscription createSubscription(String name, String callbackUrl,
      List<String> eventTypes) {
    validateCallbackUrl(callbackUrl);

    String tenantId = currentTenantId();
    String secret = hmacSigner.generateSecret();
    String eventTypesJson = serializeEventTypes(eventTypes);

    WebhookSubscription subscription = new WebhookSubscription(
        tenantId, name, callbackUrl, secret, eventTypesJson);
    return repository.save(subscription);
  }

  @Transactional
  public WebhookSubscription updateSubscription(String subscriptionId, String name,
      String callbackUrl, List<String> eventTypes, Boolean active) {
    WebhookSubscription subscription = getSubscription(subscriptionId);

    if (callbackUrl != null) {
      validateCallbackUrl(callbackUrl);
      subscription.setCallbackUrl(callbackUrl);
    }
    if (name != null) {
      subscription.setName(name);
    }
    if (eventTypes != null) {
      subscription.setEventTypes(serializeEventTypes(eventTypes));
    }
    if (active != null) {
      subscription.setActive(active);
    }

    return repository.save(subscription);
  }

  @Transactional
  public void deleteSubscription(String subscriptionId) {
    WebhookSubscription subscription = getSubscription(subscriptionId);
    subscription.setActive(false);
    subscription.setDeletedAt(java.time.Instant.now());
    repository.save(subscription);
  }

  private void validateCallbackUrl(String callbackUrl) {
    List<String> errors = ssrfValidator.validate(callbackUrl);
    if (!errors.isEmpty()) {
      throw new UnprocessableEntityException(
          "Invalid callback URL: " + String.join("; ", errors));
    }
  }

  private String currentTenantId() {
    return RequestContext.current().tenantId();
  }

  private Specification<WebhookSubscription> tenantScope() {
    return (root, query, cb) ->
        cb.equal(root.get("tenantId"), currentTenantId());
  }

  private Specification<WebhookSubscription> notDeleted() {
    return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
  }

  private String serializeEventTypes(List<String> eventTypes) {
    if (eventTypes == null || eventTypes.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(eventTypes);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize event types", e);
    }
  }
}
