package dev.bkjohnson.notification.controller;

import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import dev.bkjohnson.notification.service.WebhookSubscriptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.auth.RequireRole;
import dev.bkjohnson.api.auth.RequireScope;
import dev.bkjohnson.api.version.ApiVersion;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for webhook subscription management.
 */
@RestController
@RequestMapping("/api/v1/webhook-subscriptions")
@ApiVersion("v1")
@RequireScope("notifications.admin")
@RequireRole("NotificationAdmin")
public class WebhookSubscriptionController {

  private final WebhookSubscriptionService subscriptionService;
  private final ObjectMapper objectMapper;

  public WebhookSubscriptionController(
      WebhookSubscriptionService subscriptionService,
      ObjectMapper objectMapper) {
    this.subscriptionService = subscriptionService;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public Page<SubscriptionView> listSubscriptions(Pageable pageable) {
    return subscriptionService.listSubscriptions(pageable)
        .map(this::toView);
  }

  @GetMapping("/{subscriptionId}")
  public SubscriptionView getSubscription(
      @PathVariable String subscriptionId) {
    return toView(subscriptionService.getSubscription(subscriptionId));
  }

  @PostMapping
  public ResponseEntity<SubscriptionCreateView> createSubscription(
      @RequestBody CreateSubscriptionBody body) {
    WebhookSubscription subscription = subscriptionService.createSubscription(
        body.name(), body.callbackUrl(), body.eventTypes());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(toCreateView(subscription));
  }

  @PutMapping("/{subscriptionId}")
  public SubscriptionView updateSubscription(
      @PathVariable String subscriptionId,
      @RequestBody UpdateSubscriptionBody body) {
    return toView(subscriptionService.updateSubscription(
        subscriptionId, body.name(), body.callbackUrl(),
        body.eventTypes(), body.active()));
  }

  @DeleteMapping("/{subscriptionId}")
  public ResponseEntity<Void> deleteSubscription(
      @PathVariable String subscriptionId) {
    subscriptionService.deleteSubscription(subscriptionId);
    return ResponseEntity.noContent().build();
  }

  private SubscriptionView toView(WebhookSubscription subscription) {
    return new SubscriptionView(
        subscription.getId(),
        subscription.getName(),
        subscription.getCallbackUrl(),
        deserializeEventTypes(subscription.getEventTypes()),
        subscription.isActive(),
        subscription.getCreatedAt() != null
            ? subscription.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
        subscription.getUpdatedAt() != null
            ? subscription.getUpdatedAt().atOffset(ZoneOffset.UTC) : null
    );
  }

  private SubscriptionCreateView toCreateView(
      WebhookSubscription subscription) {
    return new SubscriptionCreateView(
        subscription.getId(),
        subscription.getName(),
        subscription.getCallbackUrl(),
        deserializeEventTypes(subscription.getEventTypes()),
        subscription.isActive(),
        subscription.getSecretEncrypted(),
        subscription.getCreatedAt() != null
            ? subscription.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
        subscription.getUpdatedAt() != null
            ? subscription.getUpdatedAt().atOffset(ZoneOffset.UTC) : null
    );
  }

  private List<String> deserializeEventTypes(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {
      });
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  record SubscriptionView(
      String id,
      String name,
      String callbackUrl,
      List<String> eventTypes,
      boolean active,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
  }

  record SubscriptionCreateView(
      String id,
      String name,
      String callbackUrl,
      List<String> eventTypes,
      boolean active,
      String hmacSecret,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
  }

  record CreateSubscriptionBody(
      String name,
      String callbackUrl,
      List<String> eventTypes
  ) {
  }

  record UpdateSubscriptionBody(
      String name,
      String callbackUrl,
      List<String> eventTypes,
      Boolean active
  ) {
  }
}
