package dev.bkjohnson.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.azure.communication.email.EmailClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.events.DomainEventPublisher;
import dev.bkjohnson.messaging.MessageProducer;
import dev.bkjohnson.notification.repository.WebhookSubscriptionRepository;
import dev.bkjohnson.notification.security.SsrfValidator;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest

@ActiveProfiles("test")
class WebhookSubscriptionControllerTest extends MockMvcTestBase {

  private static final String TENANT_ID = "test-tenant-001";
  private static final String BASE_URL = "/api/v1/webhook-subscriptions";


  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private WebhookSubscriptionRepository subscriptionRepository;

  @MockitoBean
  private MessageProducer<?> messageProducer;

  @MockitoBean
  private DomainEventPublisher domainEventPublisher;

  @MockitoBean
  private EmailClient emailClient;

  @MockitoBean
  private SsrfValidator ssrfValidator;

  @BeforeEach
  void setUp() {
    RequestContext.set(new RequestContext(
        "req-test-001",
        "trace-001",
        "v1",
        "test-client",
        "test-user",
        Set.of("NotificationAdmin"),
        Set.of("notifications.admin"),
        TENANT_ID,
        "127.0.0.1",
        null,
        Instant.now(),
        Map.of()
    ));

    // SsrfValidator is mocked to return no errors (valid URL)
    org.mockito.Mockito.when(ssrfValidator.validate(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(List.of());
  }

  @AfterEach
  protected void tearDown() {
    subscriptionRepository.deleteAll();
    RequestContext.clear();
  }

  @Test
  void testCreateSubscription() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "order-events",
        "callbackUrl", "https://example.com/webhooks/orders",
        "eventTypes", List.of("order.created", "order.updated")
    ));

    mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").exists())
        .andExpect(jsonPath("$.data.name").value("order-events"))
        .andExpect(jsonPath("$.data.callbackUrl").value("https://example.com/webhooks/orders"))
        .andExpect(jsonPath("$.data.active").value(true))
        .andExpect(jsonPath("$.data.hmacSecret").exists());
  }

  @Test
  void testGetSubscription() throws Exception {
    // Create via API to get a proper subscription
    String createBody = objectMapper.writeValueAsString(Map.of(
        "name", "fetch-me",
        "callbackUrl", "https://example.com/webhooks/fetch",
        "eventTypes", List.of("notification.sent")
    ));

    MvcResult createResult = mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String subscriptionId = objectMapper.readTree(
        createResult.getResponse().getContentAsString()).get("data").get("id").asText();

    mockMvc.perform(get(BASE_URL + "/" + subscriptionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(subscriptionId))
        .andExpect(jsonPath("$.data.name").value("fetch-me"));
  }

  @Test
  void testListSubscriptions() throws Exception {
    // Create two subscriptions
    for (String name : List.of("sub-one", "sub-two")) {
      String body = objectMapper.writeValueAsString(Map.of(
          "name", name,
          "callbackUrl", "https://example.com/webhooks/" + name,
          "eventTypes", List.of("test.event")
      ));
      mockMvc.perform(post(BASE_URL)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isCreated());
    }

    mockMvc.perform(get(BASE_URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  void testUpdateSubscription() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "name", "update-me",
        "callbackUrl", "https://example.com/webhooks/old",
        "eventTypes", List.of("old.event")
    ));

    MvcResult createResult = mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String subscriptionId = objectMapper.readTree(
        createResult.getResponse().getContentAsString()).get("data").get("id").asText();

    String updateBody = objectMapper.writeValueAsString(Map.of(
        "name", "updated-name",
        "callbackUrl", "https://example.com/webhooks/new",
        "eventTypes", List.of("new.event"),
        "active", false
    ));

    mockMvc.perform(put(BASE_URL + "/" + subscriptionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("updated-name"))
        .andExpect(jsonPath("$.data.callbackUrl").value("https://example.com/webhooks/new"))
        .andExpect(jsonPath("$.data.active").value(false));
  }

  @Test
  void testDeleteSubscription() throws Exception {
    String createBody = objectMapper.writeValueAsString(Map.of(
        "name", "delete-me",
        "callbackUrl", "https://example.com/webhooks/delete",
        "eventTypes", List.of("test.event")
    ));

    MvcResult createResult = mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody))
        .andExpect(status().isCreated())
        .andReturn();

    String subscriptionId = objectMapper.readTree(
        createResult.getResponse().getContentAsString()).get("data").get("id").asText();

    mockMvc.perform(delete(BASE_URL + "/" + subscriptionId))
        .andExpect(status().isNoContent());

    // Verify soft-deleted
    var deleted = subscriptionRepository.findById(subscriptionId).orElse(null);
    assertThat(deleted).isNotNull();
    assertThat(deleted.getDeletedAt()).isNotNull();
    assertThat(deleted.isActive()).isFalse();
  }

  @Test
  void testCreateAndGetSubscriptionWithEventTypes() throws Exception {
    // Create a subscription with eventTypes to cover deserializeEventTypes non-null path
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "events-sub",
        "callbackUrl", "https://example.com/webhooks/events",
        "eventTypes", List.of("order.created", "order.shipped")
    ));

    // The create response goes through toCreateView -> deserializeEventTypes(non-null JSON)
    // which covers the non-null branch of deserializeEventTypes
    mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.eventTypes").isArray())
        .andExpect(jsonPath("$.data.eventTypes.length()").value(2))
        .andExpect(jsonPath("$.data.eventTypes[0]").value("order.created"))
        .andExpect(jsonPath("$.data.eventTypes[1]").value("order.shipped"));
  }

  @Test
  void testGetSubscriptionWithTimestamps() throws Exception {
    // Directly save a subscription with timestamps set to cover non-null createdAt/updatedAt
    dev.bkjohnson.notification.model.entity.WebhookSubscription sub =
        new dev.bkjohnson.notification.model.entity.WebhookSubscription(
            TENANT_ID, "ts-sub", "https://example.com/webhooks/ts", "secret123",
            "[\"test.event\"]");
    sub.setCreatedAt(java.time.Instant.now());
    sub.setUpdatedAt(java.time.Instant.now());
    sub = subscriptionRepository.save(sub);

    mockMvc.perform(get(BASE_URL + "/" + sub.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
  }

  @Test
  void testCreateSubscriptionWithoutEventTypes() throws Exception {
    // Cover deserializeEventTypes with null json input
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "no-events-sub",
        "callbackUrl", "https://example.com/webhooks/noevents"
    ));

    mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.eventTypes").doesNotExist());
  }
}
