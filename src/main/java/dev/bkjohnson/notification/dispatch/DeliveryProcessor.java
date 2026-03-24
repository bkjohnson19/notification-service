package dev.bkjohnson.notification.dispatch;

import dev.bkjohnson.notification.model.command.DeliveryCommand;
import dev.bkjohnson.notification.model.entity.Channel;
import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import dev.bkjohnson.notification.model.event.NotificationEventType;
import dev.bkjohnson.notification.repository.NotificationDeliveryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.events.DefaultDomainEvent;
import dev.bkjohnson.api.events.DomainEventPublisher;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.messaging.MessageEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes delivery commands from Service Bus to dispatch notifications.
 */
@Component
public class DeliveryProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(DeliveryProcessor.class);

  private final NotificationDeliveryRepository deliveryRepository;
  private final EmailDispatcher emailDispatcher;
  private final WebhookDispatcher webhookDispatcher;
  private final TemplateRenderer templateRenderer;
  private final DomainEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public DeliveryProcessor(
      NotificationDeliveryRepository deliveryRepository,
      EmailDispatcher emailDispatcher,
      WebhookDispatcher webhookDispatcher,
      TemplateRenderer templateRenderer,
      DomainEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.deliveryRepository = deliveryRepository;
    this.emailDispatcher = emailDispatcher;
    this.webhookDispatcher = webhookDispatcher;
    this.templateRenderer = templateRenderer;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  /**
   * Processes a delivery command received from Service Bus.
   */
  public void processMessage(MessageEnvelope<DeliveryCommand> envelope) {
    DeliveryCommand command = envelope.payload();
    LOG.info("Processing delivery: {}", command.deliveryId());
    processDelivery(command.deliveryId());
  }

  @Transactional
  public void processDelivery(String deliveryId) {
    NotificationDelivery delivery = deliveryRepository.findById(deliveryId)
        .orElseThrow(() -> new NotFoundException(
            "Delivery not found: " + deliveryId));

    delivery.markProcessing();
    delivery.recordAttempt();
    deliveryRepository.save(delivery);

    try {
      if (delivery.getChannel() == Channel.EMAIL) {
        processEmailDelivery(delivery);
      } else {
        processWebhookDelivery(delivery);
      }

      delivery.markDelivered();
      deliveryRepository.save(delivery);
      publishDeliveredEvent(delivery);

    } catch (Exception e) {
      LOG.error("Delivery failed for {}: {}", deliveryId, e.getMessage(), e);
      delivery.markFailed(e.getMessage());
      deliveryRepository.save(delivery);
      publishFailedEvent(delivery, e.getMessage());
      throw e;
    }
  }

  private void processEmailDelivery(NotificationDelivery delivery) {
    String subject;
    String htmlBody;

    NotificationTemplate template = delivery.getTemplate();
    if (template != null) {
      Map<String, Object> variables = deserializePayload(delivery.getPayload());
      htmlBody = templateRenderer.render(template.getBodyTemplate(), variables);
      subject = template.getSubject();

      if (subject != null && variables != null) {
        subject = templateRenderer.render(subject, variables);
      }
    } else {
      Map<String, Object> rawContent = deserializePayload(delivery.getPayload());
      subject = (String) rawContent.getOrDefault("subject", "");
      htmlBody = (String) rawContent.getOrDefault("body", "");
    }

    emailDispatcher.send(delivery.getRecipientAddress(), subject, htmlBody);
  }

  private void processWebhookDelivery(NotificationDelivery delivery) {
    WebhookSubscription subscription = delivery.getWebhookSubscription();
    if (subscription == null) {
      throw new IllegalStateException(
          "Webhook subscription not found for delivery: " + delivery.getId());
    }

    webhookDispatcher.send(
        subscription.getCallbackUrl(),
        subscription.getSecretEncrypted(),
        delivery.getId(),
        delivery.getPayload()
    );
  }

  private void publishDeliveredEvent(NotificationDelivery delivery) {
    eventPublisher.publish(new DefaultDomainEvent(
        UUID.randomUUID().toString(),
        NotificationEventType.NOTIFICATION_DELIVERED,
        delivery.getId(),
        Instant.now(),
        Map.of("channel", delivery.getChannel().name())
    ));
  }

  private void publishFailedEvent(NotificationDelivery delivery, String error) {
    eventPublisher.publish(new DefaultDomainEvent(
        UUID.randomUUID().toString(),
        NotificationEventType.NOTIFICATION_FAILED,
        delivery.getId(),
        Instant.now(),
        Map.of(
            "channel", delivery.getChannel().name(),
            "error", error != null ? error : "unknown"
        )
    ));
  }

  private Map<String, Object> deserializePayload(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {
      });
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize payload", e);
    }
  }
}
