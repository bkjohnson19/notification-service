package dev.bkjohnson.notification.controller;

import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import dev.bkjohnson.notification.service.TemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.auth.RequireRole;
import dev.bkjohnson.api.auth.RequireScope;
import dev.bkjohnson.api.cache.ApiCacheEvict;
import dev.bkjohnson.api.cache.ApiCacheable;
import dev.bkjohnson.api.filter.FilterRequest;
import dev.bkjohnson.api.filter.FilterableFields;
import dev.bkjohnson.api.version.ApiVersion;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * REST controller for notification template management.
 */
@RestController
@RequestMapping("/api/v1/notification-templates")
@ApiVersion("v1")
@RequireScope("notifications.admin")
@FilterableFields({"name", "channel", "createdAt"})
public class TemplateController {

  private static final Set<String> ALLOWED_FILTER_FIELDS =
      Set.of("name", "channel", "createdAt");

  private final TemplateService templateService;
  private final ObjectMapper objectMapper;

  public TemplateController(TemplateService templateService,
      ObjectMapper objectMapper) {
    this.templateService = templateService;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public Page<TemplateView> listTemplates(FilterRequest filterRequest,
      Pageable pageable) {
    return templateService.listTemplates(
            filterRequest != null
                ? filterRequest.toSpecification(ALLOWED_FILTER_FIELDS)
                : null, pageable)
        .map(this::toView);
  }

  @GetMapping("/{templateId}")
  @ApiCacheable(cacheName = "templates", key = "#templateId", tenantIsolated = true)
  public TemplateView getTemplate(@PathVariable String templateId) {
    return toView(templateService.getTemplate(templateId));
  }

  @PostMapping
  @RequireRole("NotificationAdmin")
  @ApiCacheEvict(cacheName = "templates", allEntries = true)
  public ResponseEntity<TemplateView> createTemplate(
      @RequestBody CreateTemplateBody body) {
    List<Map<String, Object>> variables = body.variables();
    NotificationTemplate template = templateService.createTemplate(
        body.name(), body.channel(), body.subject(),
        body.bodyTemplate(), variables);
    return ResponseEntity.status(HttpStatus.CREATED).body(toView(template));
  }

  @PutMapping("/{templateId}")
  @RequireRole("NotificationAdmin")
  @ApiCacheEvict(cacheName = "templates", allEntries = true)
  public TemplateView updateTemplate(@PathVariable String templateId,
      @RequestBody UpdateTemplateBody body) {
    return toView(templateService.updateTemplate(
        templateId, body.name(), body.subject(),
        body.bodyTemplate(), body.variables()));
  }

  @DeleteMapping("/{templateId}")
  @RequireRole("NotificationAdmin")
  @ApiCacheEvict(cacheName = "templates", allEntries = true)
  public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId) {
    templateService.deleteTemplate(templateId);
    return ResponseEntity.noContent().build();
  }

  private TemplateView toView(NotificationTemplate template) {
    List<Map<String, Object>> variables = null;
    if (template.getVariables() != null) {
      try {
        variables = objectMapper.readValue(template.getVariables(),
            new TypeReference<>() {
            });
      } catch (JsonProcessingException e) {
        variables = List.of();
      }
    }
    return new TemplateView(
        template.getId(),
        template.getName(),
        template.getChannel().name(),
        template.getSubject(),
        template.getBodyTemplate(),
        variables,
        template.getCreatedAt() != null
            ? template.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
        template.getUpdatedAt() != null
            ? template.getUpdatedAt().atOffset(ZoneOffset.UTC) : null
    );
  }

  record TemplateView(
      String id,
      String name,
      String channel,
      String subject,
      String bodyTemplate,
      List<Map<String, Object>> variables,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
  ) {
  }

  record CreateTemplateBody(
      String name,
      String channel,
      String subject,
      String bodyTemplate,
      List<Map<String, Object>> variables
  ) {
  }

  record UpdateTemplateBody(
      String name,
      String subject,
      String bodyTemplate,
      List<Map<String, Object>> variables
  ) {
  }
}
