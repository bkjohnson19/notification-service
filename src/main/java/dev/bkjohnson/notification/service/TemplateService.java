package dev.bkjohnson.notification.service;

import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import dev.bkjohnson.notification.repository.NotificationTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.exception.ConflictException;
import dev.bkjohnson.api.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing notification templates.
 */
@Service
public class TemplateService {

  private final NotificationTemplateRepository repository;
  private final ObjectMapper objectMapper;

  public TemplateService(NotificationTemplateRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public Page<NotificationTemplate> listTemplates(Specification<NotificationTemplate> spec,
      Pageable pageable) {
    Specification<NotificationTemplate> tenantSpec = tenantScope()
        .and(notDeleted());
    if (spec != null) {
      tenantSpec = tenantSpec.and(spec);
    }
    return repository.findAll(tenantSpec, pageable);
  }

  @Transactional(readOnly = true)
  public NotificationTemplate getTemplate(String templateId) {
    return repository.findById(templateId)
        .filter(t -> t.getDeletedAt() == null)
        .filter(t -> t.getTenantId().equals(currentTenantId()))
        .orElseThrow(() -> new NotFoundException(
            "Template not found: " + templateId));
  }

  @Transactional
  public NotificationTemplate createTemplate(String name, String channel,
      String subject, String bodyTemplate, List<Map<String, Object>> variables) {
    String tenantId = currentTenantId();

    if (repository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, name)) {
      throw new ConflictException(
          "Template with name '" + name + "' already exists");
    }

    String variablesJson = serializeVariables(variables);
    var channelEnum =
        dev.bkjohnson.notification.model.entity.Channel.valueOf(channel);

    NotificationTemplate template = new NotificationTemplate(
        tenantId, name, channelEnum, subject, bodyTemplate, variablesJson);
    return repository.save(template);
  }

  @Transactional
  public NotificationTemplate updateTemplate(String templateId, String name,
      String subject, String bodyTemplate, List<Map<String, Object>> variables) {
    NotificationTemplate template = getTemplate(templateId);

    if (name != null && !name.equals(template.getName())) {
      String tenantId = currentTenantId();
      if (repository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, name)) {
        throw new ConflictException(
            "Template with name '" + name + "' already exists");
      }
      template.setName(name);
    }

    if (subject != null) {
      template.setSubject(subject);
    }
    if (bodyTemplate != null) {
      template.setBodyTemplate(bodyTemplate);
    }
    if (variables != null) {
      template.setVariables(serializeVariables(variables));
    }

    return repository.save(template);
  }

  @Transactional
  public void deleteTemplate(String templateId) {
    NotificationTemplate template = getTemplate(templateId);
    template.setDeletedAt(java.time.Instant.now());
    repository.save(template);
  }

  @Transactional(readOnly = true)
  public NotificationTemplate findByName(String name) {
    return repository.findByTenantIdAndNameAndDeletedAtIsNull(
            currentTenantId(), name)
        .orElseThrow(() -> new NotFoundException(
            "Template not found with name: " + name));
  }

  private String currentTenantId() {
    return RequestContext.current().tenantId();
  }

  private Specification<NotificationTemplate> tenantScope() {
    return (root, query, cb) ->
        cb.equal(root.get("tenantId"), currentTenantId());
  }

  private Specification<NotificationTemplate> notDeleted() {
    return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
  }

  private String serializeVariables(List<Map<String, Object>> variables) {
    if (variables == null || variables.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(variables);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize template variables", e);
    }
  }
}
