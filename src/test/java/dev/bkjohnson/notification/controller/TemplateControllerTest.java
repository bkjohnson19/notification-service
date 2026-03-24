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
import dev.bkjohnson.notification.model.entity.Channel;
import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import dev.bkjohnson.notification.repository.NotificationTemplateRepository;
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
class TemplateControllerTest extends MockMvcTestBase {

  private static final String TENANT_ID = "test-tenant-001";
  private static final String BASE_URL = "/api/v1/notification-templates";


  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private NotificationTemplateRepository templateRepository;

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
        Set.of("NotificationAdmin"),
        Set.of("notifications.admin"),
        TENANT_ID,
        "127.0.0.1",
        null,
        Instant.now(),
        Map.of()
    ));
  }

  @AfterEach
  protected void tearDown() {
    templateRepository.deleteAll();
    RequestContext.clear();
  }

  @Test
  void testCreateTemplate() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "welcome-email",
        "channel", "EMAIL",
        "subject", "Welcome!",
        "bodyTemplate", "<p>Hello [[${name}]]</p>",
        "variables", List.of(Map.of("name", "name", "type", "string"))
    ));

    mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value("welcome-email"))
        .andExpect(jsonPath("$.data.channel").value("EMAIL"))
        .andExpect(jsonPath("$.data.subject").value("Welcome!"))
        .andExpect(jsonPath("$.data.id").exists());
  }

  @Test
  void testGetTemplate() throws Exception {
    NotificationTemplate template = new NotificationTemplate(
        TENANT_ID, "order-confirm", Channel.EMAIL,
        "Order Confirmed", "<p>Your order is confirmed</p>", null);
    template = templateRepository.save(template);

    mockMvc.perform(get(BASE_URL + "/" + template.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(template.getId()))
        .andExpect(jsonPath("$.data.name").value("order-confirm"))
        .andExpect(jsonPath("$.data.channel").value("EMAIL"));
  }

  @Test
  void testListTemplates() throws Exception {
    templateRepository.save(new NotificationTemplate(
        TENANT_ID, "template-a", Channel.EMAIL,
        "Subject A", "<p>Body A</p>", null));
    templateRepository.save(new NotificationTemplate(
        TENANT_ID, "template-b", Channel.WEBHOOK,
        null, "{\"event\": \"test\"}", null));

    mockMvc.perform(get(BASE_URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  void testUpdateTemplate() throws Exception {
    NotificationTemplate template = new NotificationTemplate(
        TENANT_ID, "update-me", Channel.EMAIL,
        "Old Subject", "<p>Old body</p>", null);
    template = templateRepository.save(template);

    String updateBody = objectMapper.writeValueAsString(Map.of(
        "name", "updated-name",
        "subject", "New Subject",
        "bodyTemplate", "<p>New body</p>"
    ));

    mockMvc.perform(put(BASE_URL + "/" + template.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("updated-name"))
        .andExpect(jsonPath("$.data.subject").value("New Subject"));
  }

  @Test
  void testDeleteTemplate() throws Exception {
    NotificationTemplate template = new NotificationTemplate(
        TENANT_ID, "delete-me", Channel.EMAIL,
        "Subject", "<p>Body</p>", null);
    template = templateRepository.save(template);

    mockMvc.perform(delete(BASE_URL + "/" + template.getId()))
        .andExpect(status().isNoContent());

    // Verify soft-deleted (still in DB but deletedAt is set)
    NotificationTemplate deleted = templateRepository.findById(template.getId()).orElse(null);
    assertThat(deleted).isNotNull();
    assertThat(deleted.getDeletedAt()).isNotNull();
  }

  @Test
  void testCreateTemplateDuplicateName() throws Exception {
    templateRepository.save(new NotificationTemplate(
        TENANT_ID, "duplicate-name", Channel.EMAIL,
        "Subject", "<p>Body</p>", null));

    String body = objectMapper.writeValueAsString(Map.of(
        "name", "duplicate-name",
        "channel", "EMAIL",
        "subject", "Another Subject",
        "bodyTemplate", "<p>Another body</p>"
    ));

    mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void testGetTemplateNotFound() throws Exception {
    mockMvc.perform(get(BASE_URL + "/nonexistent-id"))
        .andExpect(status().isNotFound());
  }

  @Test
  void testCreateTemplateWithVariablesCoversToView() throws Exception {
    // Create via API with variables — the create response goes through toView()
    // which covers the non-null variables branch and objectMapper.readValue path
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "with-vars",
        "channel", "EMAIL",
        "subject", "Hello",
        "bodyTemplate", "<p>Hi [[${userName}]]</p>",
        "variables", List.of(Map.of("name", "userName", "type", "string"))
    ));

    mockMvc.perform(post(BASE_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.variables").isArray())
        .andExpect(jsonPath("$.data.variables.length()").value(1))
        .andExpect(jsonPath("$.data.variables[0].name").value("userName"));
  }

  @Test
  void testGetTemplateWithInvalidVariablesJson() throws Exception {
    // Template with malformed variables JSON to cover JsonProcessingException branch in toView
    NotificationTemplate template = new NotificationTemplate(
        TENANT_ID, "bad-vars", Channel.EMAIL,
        "Hello", "<p>Body</p>", "not-valid-json");
    template = templateRepository.save(template);

    mockMvc.perform(get(BASE_URL + "/" + template.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.variables").isArray())
        .andExpect(jsonPath("$.data.variables.length()").value(0));
  }

  @Test
  void testGetTemplateWithTimestamps() throws Exception {
    // Directly save a template with timestamps to cover non-null createdAt/updatedAt in toView
    NotificationTemplate template = new NotificationTemplate(
        TENANT_ID, "ts-template", Channel.EMAIL,
        "Subject", "<p>Body</p>", null);
    template.setCreatedAt(java.time.Instant.now());
    template.setUpdatedAt(java.time.Instant.now());
    template = templateRepository.save(template);

    mockMvc.perform(get(BASE_URL + "/" + template.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
  }
}
