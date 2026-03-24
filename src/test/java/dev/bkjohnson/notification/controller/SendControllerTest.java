package dev.bkjohnson.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.azure.communication.email.EmailClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.events.DomainEventPublisher;
import dev.bkjohnson.messaging.MessageProducer;
import dev.bkjohnson.messaging.SendResult;
import dev.bkjohnson.notification.config.NotificationProperties;
import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import dev.bkjohnson.notification.repository.NotificationDeliveryRepository;
import dev.bkjohnson.notification.repository.WebhookSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import dev.bkjohnson.api.test.mock.MockMvcTestBase;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest

@ActiveProfiles("test")
class SendControllerTest extends MockMvcTestBase {

  private static final String TENANT_ID = "test-tenant-001";
  private static final String BASE_URL = "/api/v1/notifications";


  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private NotificationDeliveryRepository deliveryRepository;

  @Autowired
  private WebhookSubscriptionRepository webhookSubscriptionRepository;

  @MockitoBean
  private MessageProducer<?> messageProducer;

  @MockitoBean
  private DomainEventPublisher domainEventPublisher;

  @MockitoBean
  private EmailClient emailClient;

  @Autowired
  private NotificationProperties notificationProperties;

  @BeforeEach
  void setUp() {
    RequestContext.set(new RequestContext(
        "req-test-001",
        "trace-001",
        "v1",
        "test-client",
        "test-user",
        Set.of("NotificationAdmin"),
        Set.of("notifications.send"),
        TENANT_ID,
        "127.0.0.1",
        null,
        Instant.now(),
        Map.of()
    ));

    when(messageProducer.send(any())).thenReturn(
        new SendResult.Delivered("msg-001", "notification-delivery", Instant.now()));
  }

  @AfterEach
  protected void tearDown() {
    deliveryRepository.deleteAll();
    webhookSubscriptionRepository.deleteAll();
    notificationProperties.getFeature().setBulkSendEnabled(false);
    RequestContext.clear();
  }

  @Test
  void testSendEmailNotification() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "channel", "EMAIL",
        "recipient", Map.of(
            "address", "user@example.com",
            "name", "Test User"
        ),
        "subject", "Test Notification",
        "body", "<p>Hello, this is a test.</p>",
        "contentType", "text/html"
    ));

    mockMvc.perform(post(BASE_URL + "/send")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.deliveryId").exists())
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andExpect(jsonPath("$.statusUrl").exists());
  }

  @Test
  void testSendWebhookNotification() throws Exception {
    WebhookSubscription subscription = new WebhookSubscription(
        TENANT_ID, "test-webhook", "https://example.com/callback",
        "test-secret-encrypted", "[\"notification.sent\"]");
    subscription = webhookSubscriptionRepository.save(subscription);

    String body = objectMapper.writeValueAsString(Map.of(
        "channel", "WEBHOOK",
        "webhookSubscriptionId", subscription.getId(),
        "payload", Map.of("event", "order.created", "orderId", "12345")
    ));

    mockMvc.perform(post(BASE_URL + "/send")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.deliveryId").exists())
        .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @Test
  void testBulkSendDisabled() throws Exception {
    // bulk-send-enabled=false in application-test.yml
    String body = objectMapper.writeValueAsString(Map.of(
        "items", java.util.List.of(Map.of(
            "clientItemId", "item-1",
            "data", Map.of(
                "channel", "EMAIL",
                "recipient", Map.of("address", "user@example.com"),
                "subject", "Bulk Test",
                "body", "<p>Bulk</p>"
            )
        ))
    ));

    mockMvc.perform(post(BASE_URL + "/send-bulk")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isNotFound());
  }

  @Test
  void testSendBulkEnabled() throws Exception {
    notificationProperties.getFeature().setBulkSendEnabled(true);

    WebhookSubscription subscription = new WebhookSubscription(
        TENANT_ID, "bulk-webhook", "https://example.com/callback",
        "test-secret-encrypted", "[\"notification.sent\"]");
    subscription = webhookSubscriptionRepository.save(subscription);

    String body = objectMapper.writeValueAsString(Map.of(
        "items", List.of(
            Map.of(
                "clientItemId", "item-1",
                "data", Map.of(
                    "channel", "EMAIL",
                    "recipient", Map.of("address", "user@example.com", "name", "User"),
                    "subject", "Bulk Test",
                    "body", "<p>Bulk email</p>",
                    "contentType", "text/html"
                )
            ),
            Map.of(
                "clientItemId", "item-2",
                "data", Map.of(
                    "channel", "WEBHOOK",
                    "webhookSubscriptionId", subscription.getId(),
                    "payload", Map.of("event", "test.event")
                )
            ),
            Map.of(
                "clientItemId", "item-3",
                "data", Map.of(
                    "channel", "WEBHOOK",
                    "payload", Map.of("event", "test.event")
                )
            )
        )
    ));

    mockMvc.perform(post(BASE_URL + "/send-bulk")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isMultiStatus())
        .andExpect(jsonPath("$.summary.total").value(3))
        .andExpect(jsonPath("$.summary.succeeded").value(2))
        .andExpect(jsonPath("$.summary.failed").value(1))
        .andExpect(jsonPath("$.results[0].clientItemId").value("item-1"))
        .andExpect(jsonPath("$.results[0].success").value(true))
        .andExpect(jsonPath("$.results[0].data.deliveryId").exists())
        .andExpect(jsonPath("$.results[1].clientItemId").value("item-2"))
        .andExpect(jsonPath("$.results[1].success").value(true))
        .andExpect(jsonPath("$.results[2].clientItemId").value("item-3"))
        .andExpect(jsonPath("$.results[2].success").value(false))
        .andExpect(jsonPath("$.results[2].error").exists());
  }
}
