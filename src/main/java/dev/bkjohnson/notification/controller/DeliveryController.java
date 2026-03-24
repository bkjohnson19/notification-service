package dev.bkjohnson.notification.controller;

import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.service.DeliveryQueryService;
import dev.bkjohnson.api.auth.RequireScope;
import dev.bkjohnson.api.filter.FilterRequest;
import dev.bkjohnson.api.filter.FilterableFields;
import dev.bkjohnson.api.version.ApiVersion;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for querying notification delivery records.
 */
@RestController
@RequestMapping("/api/v1/notification-deliveries")
@ApiVersion("v1")
@RequireScope("notifications.read")
@FilterableFields({"status", "channel", "recipientAddress", "createdAt", "templateId"})
public class DeliveryController {

  private static final Set<String> ALLOWED_FILTER_FIELDS =
      Set.of("status", "channel", "recipientAddress", "createdAt", "templateId");

  private final DeliveryQueryService deliveryQueryService;

  public DeliveryController(DeliveryQueryService deliveryQueryService) {
    this.deliveryQueryService = deliveryQueryService;
  }

  @GetMapping
  public Page<DeliveryView> listDeliveries(FilterRequest filterRequest,
      Pageable pageable) {
    return deliveryQueryService.listDeliveries(
            filterRequest != null
                ? filterRequest.toSpecification(ALLOWED_FILTER_FIELDS)
                : null,
            pageable)
        .map(this::toView);
  }

  @GetMapping("/{deliveryId}")
  public DeliveryView getDelivery(@PathVariable String deliveryId) {
    return toView(deliveryQueryService.getDelivery(deliveryId));
  }

  private DeliveryView toView(NotificationDelivery delivery) {
    return new DeliveryView(
        delivery.getId(),
        delivery.getChannel().name(),
        delivery.getRecipientAddress(),
        delivery.getTemplate() != null ? delivery.getTemplate().getId() : null,
        delivery.getWebhookSubscription() != null
            ? delivery.getWebhookSubscription().getId() : null,
        delivery.getStatus().name(),
        delivery.getErrorDetail(),
        delivery.getAttempts(),
        delivery.getLastAttemptedAt() != null
            ? delivery.getLastAttemptedAt().atOffset(ZoneOffset.UTC) : null,
        delivery.getDeliveredAt() != null
            ? delivery.getDeliveredAt().atOffset(ZoneOffset.UTC) : null,
        delivery.getCreatedAt() != null
            ? delivery.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
        delivery.getMetadata()
    );
  }

  record DeliveryView(
      String id,
      String channel,
      String recipientAddress,
      String templateId,
      String webhookSubscriptionId,
      String status,
      String errorDetail,
      int attempts,
      OffsetDateTime lastAttemptedAt,
      OffsetDateTime deliveredAt,
      OffsetDateTime createdAt,
      String metadata
  ) {
  }
}
