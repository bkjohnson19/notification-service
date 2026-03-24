package dev.bkjohnson.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.azure.communication.email.EmailClient;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.events.DomainEventPublisher;
import dev.bkjohnson.messaging.MessageProducer;
import dev.bkjohnson.notification.model.entity.Channel;
import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.repository.NotificationDeliveryRepository;
import dev.bkjohnson.notification.repository.NotificationTemplateRepository;
import dev.bkjohnson.notification.repository.WebhookSubscriptionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import dev.bkjohnson.api.test.mock.MockMvcTestBase;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest

@ActiveProfiles("test")
class DeliveryControllerTest extends MockMvcTestBase {

  private static final String TENANT_ID = "test-tenant-001";
  private static final String BASE_URL = "/api/v1/notification-deliveries";


  @Autowired
  private NotificationDeliveryRepository deliveryRepository;

  @Autowired
  private NotificationTemplateRepository templateRepository;

  @Autowired
  private WebhookSubscriptionRepository webhookSubscriptionRepository;

  @MockitoBean
  private MessageProducer<?> messageProducer;

  @MockitoBean
  private DomainEventPublisher domainEventPublisher;

  @MockitoBean
  private EmailClient emailClient;

  @BeforeEach
  void setUp() {
    RequestContext.set(new RequestContext(
        "req-test-001",
        "trace-001",
        "v1",
        "test-client",
        "test-user",
        Set.of(),
        Set.of("notifications.read"),
        TENANT_ID,
        "127.0.0.1",
        null,
        Instant.now(),
        Map.of()
    ));
  }

  @AfterEach
  protected void tearDown() {
    deliveryRepository.deleteAll();
    webhookSubscriptionRepository.deleteAll();
    templateRepository.deleteAll();
    RequestContext.clear();
  }

  @Test
  void testGetDelivery() throws Exception {
    NotificationDelivery delivery = new NotificationDelivery(
        TENANT_ID, Channel.EMAIL, "user@example.com");
    delivery = deliveryRepository.save(delivery);

    mockMvc.perform(get(BASE_URL + "/" + delivery.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(delivery.getId()))
        .andExpect(jsonPath("$.data.channel").value("EMAIL"))
        .andExpect(jsonPath("$.data.recipientAddress").value("user@example.com"))
        .andExpect(jsonPath("$.data.status").value("QUEUED"));
  }

  @Test
  void testListDeliveries() throws Exception {
    deliveryRepository.save(new NotificationDelivery(
        TENANT_ID, Channel.EMAIL, "alice@example.com"));
    deliveryRepository.save(new NotificationDelivery(
        TENANT_ID, Channel.WEBHOOK, "https://example.com/hook"));

    mockMvc.perform(get(BASE_URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  void testGetDeliveryNotFound() throws Exception {
    mockMvc.perform(get(BASE_URL + "/nonexistent-id"))
        .andExpect(status().isNotFound());
  }

  @Test
  void testGetDeliveryWithAllFieldsPopulated() throws Exception {
    // Create a template first
    dev.bkjohnson.notification.model.entity.NotificationTemplate template =
        new dev.bkjohnson.notification.model.entity.NotificationTemplate(
            TENANT_ID, "test-template", Channel.EMAIL,
            "Subject", "<p>Body</p>", null);
    template = templateRepository.save(template);

    // Create a webhook subscription
    dev.bkjohnson.notification.model.entity.WebhookSubscription subscription =
        new dev.bkjohnson.notification.model.entity.WebhookSubscription(
            TENANT_ID, "test-hook", "https://example.com/hook", "secret123",
            "[\"test.event\"]");
    subscription = webhookSubscriptionRepository.save(subscription);

    // Create a delivery with ALL fields populated (non-null branches)
    NotificationDelivery delivery = new NotificationDelivery(
        TENANT_ID, Channel.EMAIL, "user@example.com");
    delivery.setTemplate(template);
    delivery.setWebhookSubscription(subscription);
    delivery.setLastAttemptedAt(Instant.now());
    delivery.setDeliveredAt(Instant.now());
    delivery.setCreatedAt(Instant.now());
    delivery = deliveryRepository.save(delivery);

    mockMvc.perform(get(BASE_URL + "/" + delivery.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(delivery.getId()))
        .andExpect(jsonPath("$.data.channel").value("EMAIL"))
        .andExpect(jsonPath("$.data.templateId").value(template.getId()))
        .andExpect(jsonPath("$.data.webhookSubscriptionId").value(subscription.getId()))
        .andExpect(jsonPath("$.data.lastAttemptedAt").isNotEmpty())
        .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty())
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
  }
}
