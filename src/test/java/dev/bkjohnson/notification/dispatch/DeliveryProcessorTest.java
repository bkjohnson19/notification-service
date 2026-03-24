package dev.bkjohnson.notification.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.events.DefaultDomainEvent;
import dev.bkjohnson.api.events.DomainEventPublisher;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.notification.model.entity.Channel;
import dev.bkjohnson.notification.model.entity.DeliveryStatus;
import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import dev.bkjohnson.notification.repository.NotificationDeliveryRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryProcessorTest {

  @Mock
  private NotificationDeliveryRepository deliveryRepository;
  @Mock
  private EmailDispatcher emailDispatcher;
  @Mock
  private WebhookDispatcher webhookDispatcher;
  @Mock
  private TemplateRenderer templateRenderer;
  @Mock
  private DomainEventPublisher eventPublisher;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private DeliveryProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new DeliveryProcessor(
        deliveryRepository,
        emailDispatcher,
        webhookDispatcher,
        templateRenderer,
        eventPublisher,
        objectMapper
    );
  }

  @Test
  void testProcessEmailDeliveryWithTemplate() {
    String deliveryId = "delivery-001";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL,
        "user@example.com");
    delivery.setId(deliveryId);
    delivery.setPayload("{\"name\": \"Alice\"}");

    NotificationTemplate template = new NotificationTemplate(
        "tenant-1", "welcome", Channel.EMAIL,
        "Hello [[${name}]]", "<p th:text=\"${name}\"></p>", null);
    delivery.setTemplate(template);

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(templateRenderer.render(eq("<p th:text=\"${name}\"></p>"), anyMap()))
        .thenReturn("<p>Alice</p>");
    when(templateRenderer.render(eq("Hello [[${name}]]"), anyMap()))
        .thenReturn("Hello Alice");
    when(deliveryRepository.save(any())).thenReturn(delivery);

    processor.processDelivery(deliveryId);

    verify(emailDispatcher).send("user@example.com", "Hello Alice", "<p>Alice</p>");
    verify(eventPublisher).publish(any(DefaultDomainEvent.class));
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
  }

  @Test
  void testProcessEmailDeliveryWithRawContent() {
    String deliveryId = "delivery-002";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL,
        "user@example.com");
    delivery.setId(deliveryId);
    delivery.setPayload("{\"subject\": \"Test Subject\", \"body\": \"<p>Raw body</p>\"}");
    // No template set — raw content mode

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(deliveryRepository.save(any())).thenReturn(delivery);

    processor.processDelivery(deliveryId);

    verify(emailDispatcher).send("user@example.com", "Test Subject", "<p>Raw body</p>");
    verify(templateRenderer, never()).render(anyString(), anyMap());
    verify(eventPublisher).publish(any(DefaultDomainEvent.class));
  }

  @Test
  void testProcessWebhookDelivery() {
    String deliveryId = "delivery-003";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.WEBHOOK, null);
    delivery.setId(deliveryId);
    delivery.setPayload("{\"event\": \"order.created\"}");

    WebhookSubscription subscription = new WebhookSubscription(
        "tenant-1", "my-webhook", "https://example.com/hook", "secret123",
        "[\"order.created\"]");
    delivery.setWebhookSubscription(subscription);

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(deliveryRepository.save(any())).thenReturn(delivery);

    processor.processDelivery(deliveryId);

    verify(webhookDispatcher).send(
        "https://example.com/hook",
        "secret123",
        delivery.getId(),
        "{\"event\": \"order.created\"}"
    );
    verify(eventPublisher).publish(any(DefaultDomainEvent.class));
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
  }

  @Test
  void testProcessDeliveryNotFound() {
    String deliveryId = "nonexistent";
    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> processor.processDelivery(deliveryId))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(deliveryId);
  }

  @Test
  void testProcessDeliveryFailure() {
    String deliveryId = "delivery-004";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL,
        "user@example.com");
    delivery.setId(deliveryId);
    delivery.setPayload("{\"subject\": \"Test\", \"body\": \"Hello\"}");

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(deliveryRepository.save(any())).thenReturn(delivery);
    doThrow(new RuntimeException("SMTP connection refused"))
        .when(emailDispatcher).send(anyString(), anyString(), anyString());

    assertThatThrownBy(() -> processor.processDelivery(deliveryId))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("SMTP connection refused");

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    assertThat(delivery.getErrorDetail()).contains("SMTP connection refused");

    ArgumentCaptor<DefaultDomainEvent> eventCaptor =
        ArgumentCaptor.forClass(DefaultDomainEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());
    DefaultDomainEvent event = eventCaptor.getValue();
    assertThat(event.eventType()).isEqualTo("notification.failed");
  }

  @Test
  void testProcessDeliveryWebhookNoSubscription() {
    String deliveryId = "delivery-005";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.WEBHOOK, null);
    delivery.setId(deliveryId);
    delivery.setPayload("{\"event\": \"test\"}");
    // No webhook subscription set — should throw IllegalStateException

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(deliveryRepository.save(any())).thenReturn(delivery);

    assertThatThrownBy(() -> processor.processDelivery(deliveryId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Webhook subscription not found for delivery");

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    assertThat(delivery.getErrorDetail()).contains("Webhook subscription not found");
  }

  @Test
  void testProcessEmailDeliveryWithEmptyPayload() {
    String deliveryId = "delivery-006";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL,
        "user@example.com");
    delivery.setId(deliveryId);
    delivery.setPayload(null);
    // No template, null payload — should use empty map defaults

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(deliveryRepository.save(any())).thenReturn(delivery);

    processor.processDelivery(deliveryId);

    verify(emailDispatcher).send("user@example.com", "", "");
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
  }

  @Test
  void testProcessEmailDeliveryWithTemplateNullSubject() {
    // Covers the branch where template.getSubject() is null
    String deliveryId = "delivery-007";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL,
        "user@example.com");
    delivery.setId(deliveryId);
    delivery.setPayload("{\"name\": \"Bob\"}");

    NotificationTemplate template = new NotificationTemplate(
        "tenant-1", "no-subject", Channel.EMAIL,
        null, "<p>Hi [[${name}]]</p>", null);
    delivery.setTemplate(template);

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(templateRenderer.render(eq("<p>Hi [[${name}]]</p>"), anyMap()))
        .thenReturn("<p>Hi Bob</p>");
    when(deliveryRepository.save(any())).thenReturn(delivery);

    processor.processDelivery(deliveryId);

    // subject is null, so templateRenderer.render should NOT be called for subject
    verify(emailDispatcher).send("user@example.com", null, "<p>Hi Bob</p>");
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
  }

  @Test
  void testProcessEmailDeliveryWithTemplateSubjectRendered() {
    // Covers the branch where subject != null && variables != null (both true)
    // so subject gets rendered through templateRenderer
    String deliveryId = "delivery-008";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL,
        "user@example.com");
    delivery.setId(deliveryId);
    delivery.setPayload("   "); // blank payload -> deserializePayload returns empty Map.of()

    NotificationTemplate template = new NotificationTemplate(
        "tenant-1", "render-subject", Channel.EMAIL,
        "Hello [[${name}]]", "<p>Static body</p>", null);
    delivery.setTemplate(template);

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    // deserializePayload("   ") returns Map.of() which is non-null
    when(templateRenderer.render(eq("<p>Static body</p>"), anyMap()))
        .thenReturn("<p>Static body</p>");
    when(templateRenderer.render(eq("Hello [[${name}]]"), anyMap()))
        .thenReturn("Hello ");
    when(deliveryRepository.save(any())).thenReturn(delivery);

    processor.processDelivery(deliveryId);

    // subject is non-null and variables (Map.of()) is non-null, so subject is rendered
    verify(templateRenderer).render(eq("Hello [[${name}]]"), anyMap());
    verify(emailDispatcher).send("user@example.com", "Hello ", "<p>Static body</p>");
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
  }

  @Test
  void testDeserializePayloadWithBlankString() {
    // Covers the `json.isBlank()` branch in deserializePayload
    String deliveryId = "delivery-009";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL,
        "user@example.com");
    delivery.setId(deliveryId);
    delivery.setPayload("");
    // No template — raw content mode with empty payload

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(deliveryRepository.save(any())).thenReturn(delivery);

    processor.processDelivery(deliveryId);

    verify(emailDispatcher).send("user@example.com", "", "");
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
  }

  @Test
  void testProcessDeliveryFailureWithNullErrorMessage() {
    // Covers the `error != null ? error : "unknown"` null branch in publishFailedEvent
    String deliveryId = "delivery-010";
    NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL,
        "user@example.com");
    delivery.setId(deliveryId);
    delivery.setPayload("{\"subject\": \"Test\", \"body\": \"Hello\"}");

    when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
    when(deliveryRepository.save(any())).thenReturn(delivery);
    doThrow(new RuntimeException((String) null))
        .when(emailDispatcher).send(anyString(), anyString(), anyString());

    assertThatThrownBy(() -> processor.processDelivery(deliveryId))
        .isInstanceOf(RuntimeException.class);

    // The failed event should use "unknown" for the null error message
    ArgumentCaptor<DefaultDomainEvent> eventCaptor =
        ArgumentCaptor.forClass(DefaultDomainEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());
    DefaultDomainEvent event = eventCaptor.getValue();
    assertThat(event.payload().get("error")).isEqualTo("unknown");
  }
}
