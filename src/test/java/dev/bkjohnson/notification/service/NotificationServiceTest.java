package dev.bkjohnson.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.events.DefaultDomainEvent;
import dev.bkjohnson.api.events.DomainEventPublisher;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.api.exception.UnprocessableEntityException;
import dev.bkjohnson.notification.config.NotificationProperties;
import dev.bkjohnson.notification.model.command.DeliveryCommand;
import dev.bkjohnson.notification.model.entity.Channel;
import dev.bkjohnson.notification.model.entity.DeliveryStatus;
import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import dev.bkjohnson.notification.repository.NotificationDeliveryRepository;
import dev.bkjohnson.messaging.MessageEnvelope;
import dev.bkjohnson.messaging.MessageProducer;
import dev.bkjohnson.messaging.SendResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private NotificationDeliveryRepository deliveryRepository;
    @Mock
    private TemplateService templateService;
    @Mock
    private WebhookSubscriptionService webhookSubscriptionService;
    @Mock
    private MessageProducer<DeliveryCommand> messageProducer;
    @Mock
    private DomainEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationProperties properties;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        RequestContext.set(new RequestContext(
                "req-1", "trace-1", "v1", "client-1", "user-1",
                Set.of("ROLE"), Set.of("scope"), TENANT_ID,
                "127.0.0.1", null, Instant.now(), Map.of()));

        properties = new NotificationProperties();
        notificationService = new NotificationService(
                deliveryRepository,
                templateService,
                webhookSubscriptionService,
                messageProducer,
                eventPublisher,
                properties,
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testSubmitEmailWithTemplate() {
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "welcome", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        when(templateService.getTemplate("tmpl-1")).thenReturn(template);
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery d = invocation.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID().toString());
                    }
                    return d;
                });
        when(messageProducer.send(any(MessageEnvelope.class)))
                .thenReturn(new SendResult.Delivered("msg-1", "notifications", Instant.now()));

        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", "user@example.com", null,
                "tmpl-1", Map.of("name", "Alice"),
                null, null, null,
                null, null, null, null, null);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(result.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(result.getRecipientAddress()).isEqualTo("user@example.com");
        assertThat(result.getTemplate()).isEqualTo(template);

        verify(deliveryRepository).save(any(NotificationDelivery.class));
        verify(messageProducer).send(any(MessageEnvelope.class));
        verify(eventPublisher).publish(any(DefaultDomainEvent.class));
    }

    @Test
    void testSubmitEmailWithRawBody() {
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery d = invocation.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID().toString());
                    }
                    return d;
                });
        when(messageProducer.send(any(MessageEnvelope.class)))
                .thenReturn(new SendResult.Delivered("msg-2", "notifications", Instant.now()));

        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", "user@example.com", null,
                null, null,
                "Test Subject", "<p>Raw body</p>", "text/html",
                null, null, null, null, null);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(result.getPayload()).contains("Test Subject");
        assertThat(result.getPayload()).contains("Raw body");
        assertThat(result.getTemplate()).isNull();

        verify(messageProducer).send(any(MessageEnvelope.class));
    }

    @Test
    void testSubmitWebhook() {
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "my-hook", "https://example.com/hook", "secret", null);

        when(webhookSubscriptionService.getSubscription("sub-1")).thenReturn(subscription);
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery d = invocation.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID().toString());
                    }
                    return d;
                });
        when(messageProducer.send(any(MessageEnvelope.class)))
                .thenReturn(new SendResult.Delivered("msg-3", "notifications", Instant.now()));

        NotificationDelivery result = notificationService.submitNotification(
                "WEBHOOK", null, null,
                null, null,
                null, null, null,
                "sub-1", Map.of("event", "order.created"),
                null, null, null);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(result.getChannel()).isEqualTo(Channel.WEBHOOK);
        assertThat(result.getWebhookSubscription()).isEqualTo(subscription);
        assertThat(result.getRecipientAddress()).isEqualTo("https://example.com/hook");

        verify(messageProducer).send(any(MessageEnvelope.class));
    }

    @Test
    void testSubmitWebhookMissingSubscription() {
        assertThatThrownBy(() -> notificationService.submitNotification(
                "WEBHOOK", null, null,
                null, null,
                null, null, null,
                null, Map.of("event", "test"),
                null, null, null))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("webhookSubscriptionId is required");
    }

    @Test
    void testSubmitWebhookInactiveSubscription() {
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "my-hook", "https://example.com/hook", "secret", null);
        subscription.setActive(false);

        when(webhookSubscriptionService.getSubscription("sub-1")).thenReturn(subscription);

        assertThatThrownBy(() -> notificationService.submitNotification(
                "WEBHOOK", null, null,
                null, null,
                null, null, null,
                "sub-1", Map.of("event", "test"),
                null, null, null))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void testIdempotentResubmission() {
        NotificationDelivery existing = new NotificationDelivery(
                TENANT_ID, Channel.EMAIL, "user@example.com");
        existing.setIdempotencyKey("idem-key-1");

        when(deliveryRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, "idem-key-1"))
                .thenReturn(Optional.of(existing));

        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", "user@example.com", null,
                null, null,
                "Subject", "Body", "text/html",
                null, null, null, null, "idem-key-1");

        assertThat(result).isSameAs(existing);
        verify(deliveryRepository, never()).save(any());
        verify(messageProducer, never()).send(any());
    }

    @Test
    void testDryRunMode() {
        properties.getFeature().setDryRunMode(true);

        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", "user@example.com", null,
                null, null,
                "Subject", "Body", "text/html",
                null, null, null, null, null);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.DRY_RUN);
        verify(messageProducer, never()).send(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void testSubmitWebhookMissingSubscriptionId() {
        assertThatThrownBy(() -> notificationService.submitNotification(
                "WEBHOOK", null, null,
                null, null,
                null, null, null,
                null, null,
                null, null, null))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("webhookSubscriptionId is required");
    }

    @Test
    void testSubmitWithMetadata() {
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery d = invocation.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID().toString());
                    }
                    return d;
                });
        when(messageProducer.send(any(MessageEnvelope.class)))
                .thenReturn(new SendResult.Delivered("msg-4", "notifications", Instant.now()));

        Map<String, String> metadata = Map.of("source", "api", "priority", "high");

        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", "user@example.com", null,
                null, null,
                "Subject", "Body", "text/html",
                null, null, null, metadata, null);

        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata()).contains("source");
        assertThat(result.getMetadata()).contains("api");
        assertThat(result.getMetadata()).contains("priority");
        assertThat(result.getMetadata()).contains("high");
    }

    @Test
    void testSubmitEmailRawContentWithNulls() {
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery d = invocation.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID().toString());
                    }
                    return d;
                });
        when(messageProducer.send(any(MessageEnvelope.class)))
                .thenReturn(new SendResult.Delivered("msg-5", "notifications", Instant.now()));

        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", "user@example.com", null,
                null, null,
                null, null, null,
                null, null, null, null, null);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(result.getPayload()).contains("subject");
        assertThat(result.getPayload()).contains("body");
        assertThat(result.getPayload()).contains("text/html");
    }

    @Test
    void testPublishSentEventWithNullRecipientAddress() {
        // Cover the branch where recipientAddress is null in publishSentEvent
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery d = invocation.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID().toString());
                    }
                    return d;
                });
        when(messageProducer.send(any(MessageEnvelope.class)))
                .thenReturn(new SendResult.Delivered("msg-6", "notifications", Instant.now()));

        // Submit with null recipientAddress — WEBHOOK channel without a subscription
        // will fail, so use EMAIL with null recipientAddress and raw content
        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", null, null,
                null, null,
                "Subject", "Body", "text/html",
                null, null, null, null, null);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        // Verify the event was published (publishSentEvent with null recipientAddress)
        ArgumentCaptor<DefaultDomainEvent> eventCaptor =
                ArgumentCaptor.forClass(DefaultDomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        DefaultDomainEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("notification.sent");
        // recipientAddress should default to empty string
        assertThat(event.payload().get("recipientAddress")).isEqualTo("");
    }

    @Test
    void testSubmitEmailWithTemplateAndNoVariables() {
        // Cover the branch where templateVariables is null in configureEmailDelivery
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "simple", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        when(templateService.getTemplate("tmpl-2")).thenReturn(template);
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery d = invocation.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID().toString());
                    }
                    return d;
                });
        when(messageProducer.send(any(MessageEnvelope.class)))
                .thenReturn(new SendResult.Delivered("msg-7", "notifications", Instant.now()));

        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", "user@example.com", null,
                "tmpl-2", null,  // templateVariables is null
                null, null, null,
                null, null, null, null, null);

        assertThat(result.getTemplate()).isEqualTo(template);
        // Payload should be null since no templateVariables were provided
        assertThat(result.getPayload()).isNull();
    }

    @Test
    void testSubmitEmailRawContentWithSomeNullValues() {
        // Cover the branches where subject is non-null but body and contentType are null
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> {
                    NotificationDelivery d = invocation.getArgument(0);
                    if (d.getId() == null) {
                        d.setId(UUID.randomUUID().toString());
                    }
                    return d;
                });
        when(messageProducer.send(any(MessageEnvelope.class)))
                .thenReturn(new SendResult.Delivered("msg-8", "notifications", Instant.now()));

        NotificationDelivery result = notificationService.submitNotification(
                "EMAIL", "user@example.com", null,
                null, null,
                "My Subject", null, null,
                null, null, null, null, null);

        assertThat(result.getPayload()).contains("My Subject");
        // body defaults to empty, contentType defaults to text/html
        assertThat(result.getPayload()).contains("text/html");
    }
}
