package dev.bkjohnson.notification.service;

import dev.bkjohnson.notification.config.NotificationProperties;
import dev.bkjohnson.notification.model.command.DeliveryCommand;
import dev.bkjohnson.notification.model.entity.Channel;
import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import dev.bkjohnson.notification.model.event.NotificationEventType;
import dev.bkjohnson.notification.repository.NotificationDeliveryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.events.DefaultDomainEvent;
import dev.bkjohnson.api.events.DomainEventPublisher;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.api.exception.UnprocessableEntityException;
import dev.bkjohnson.messaging.MessageEnvelope;
import dev.bkjohnson.messaging.MessageProducer;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for submitting notifications for async delivery.
 */
@Service
public class NotificationService {

  private final NotificationDeliveryRepository deliveryRepository;
  private final TemplateService templateService;
  private final WebhookSubscriptionService webhookSubscriptionService;
  private final MessageProducer<DeliveryCommand> messageProducer;
  private final DomainEventPublisher eventPublisher;
  private final NotificationProperties properties;
  private final ObjectMapper objectMapper;

  public NotificationService(
      NotificationDeliveryRepository deliveryRepository,
      TemplateService templateService,
      WebhookSubscriptionService webhookSubscriptionService,
      MessageProducer<DeliveryCommand> messageProducer,
      DomainEventPublisher eventPublisher,
      NotificationProperties properties,
      ObjectMapper objectMapper) {
    this.deliveryRepository = deliveryRepository;
    this.templateService = templateService;
    this.webhookSubscriptionService = webhookSubscriptionService;
    this.messageProducer = messageProducer;
    this.eventPublisher = eventPublisher;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public NotificationDelivery submitNotification(
      String channel, String recipientAddress, String recipientName,
      String templateId, Map<String, Object> templateVariables,
      String subject, String body, String contentType,
      String webhookSubscriptionId, Map<String, Object> payload,
      String callbackUrl, Map<String, String> metadata,
      String idempotencyKey) {

    String tenantId = currentTenantId();

    // Check idempotency
    if (idempotencyKey != null) {
      Optional<NotificationDelivery> existing =
          deliveryRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
      if (existing.isPresent()) {
        return existing.get();
      }
    }

    Channel channelEnum = Channel.valueOf(channel);
    NotificationDelivery delivery = new NotificationDelivery(
        tenantId, channelEnum, recipientAddress);
    delivery.setIdempotencyKey(idempotencyKey);

    if (metadata != null) {
      delivery.setMetadata(serializeJson(metadata));
    }

    if (channelEnum == Channel.EMAIL) {
      configureEmailDelivery(delivery, templateId, templateVariables,
          subject, body, contentType);
    } else {
      configureWebhookDelivery(delivery, webhookSubscriptionId, payload);
    }

    // Check dry-run mode
    if (properties.getFeature().isDryRunMode()) {
      delivery.markDryRun();
      delivery = deliveryRepository.save(delivery);
      return delivery;
    }

    delivery = deliveryRepository.save(delivery);

    publishToServiceBus(delivery);
    publishSentEvent(delivery);

    return delivery;
  }

  private void configureEmailDelivery(NotificationDelivery delivery,
      String templateId, Map<String, Object> templateVariables,
      String subject, String body, String contentType) {
    if (templateId != null) {
      NotificationTemplate template = templateService.getTemplate(templateId);
      delivery.setTemplate(template);
      if (templateVariables != null) {
        delivery.setPayload(serializeJson(templateVariables));
      }
    } else {
      Map<String, Object> rawContent = Map.of(
          "subject", subject != null ? subject : "",
          "body", body != null ? body : "",
          "contentType", contentType != null ? contentType : "text/html"
      );
      delivery.setPayload(serializeJson(rawContent));
    }
  }

  private void configureWebhookDelivery(NotificationDelivery delivery,
      String webhookSubscriptionId, Map<String, Object> payload) {
    if (webhookSubscriptionId == null) {
      throw new UnprocessableEntityException(
          "webhookSubscriptionId is required for WEBHOOK channel");
    }

    WebhookSubscription subscription =
        webhookSubscriptionService.getSubscription(webhookSubscriptionId);

    if (!subscription.isActive()) {
      throw new UnprocessableEntityException(
          "Webhook subscription is inactive: " + webhookSubscriptionId);
    }

    delivery.setWebhookSubscription(subscription);
    delivery.setRecipientAddress(subscription.getCallbackUrl());
    if (payload != null) {
      delivery.setPayload(serializeJson(payload));
    }
  }

  private void publishToServiceBus(NotificationDelivery delivery) {
    DeliveryCommand command = new DeliveryCommand(
        delivery.getId(), delivery.getTenantId());

    MessageEnvelope<DeliveryCommand> envelope = new MessageEnvelope<>(
        UUID.randomUUID().toString(),
        RequestContext.current().requestId(),
        RequestContext.current().traceId(),
        null,
        "notification-service",
        "DeliveryCommand",
        1,
        Instant.now(),
        command,
        Map.of()
    );

    messageProducer.send(envelope);
  }

  private void publishSentEvent(NotificationDelivery delivery) {
    eventPublisher.publish(new DefaultDomainEvent(
        UUID.randomUUID().toString(),
        NotificationEventType.NOTIFICATION_SENT,
        delivery.getId(),
        Instant.now(),
        Map.of(
            "channel", delivery.getChannel().name(),
            "recipientAddress",
            delivery.getRecipientAddress() != null ? delivery.getRecipientAddress() : ""
        )
    ));
  }

  private String currentTenantId() {
    return RequestContext.current().tenantId();
  }

  private String serializeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize to JSON", e);
    }
  }
}
