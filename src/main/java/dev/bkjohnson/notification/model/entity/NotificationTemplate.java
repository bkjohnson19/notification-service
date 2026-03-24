package dev.bkjohnson.notification.model.entity;

import dev.bkjohnson.api.data.entity.SoftDeletableEntity;
import dev.bkjohnson.api.data.tenant.TenantColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Notification template entity supporting email and webhook channels.
 */
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate extends SoftDeletableEntity {

  @TenantColumn
  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false)
  private Channel channel;

  @Column(name = "subject")
  private String subject;

  @Column(name = "body_template", nullable = false, columnDefinition = "text")
  private String bodyTemplate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "variables", columnDefinition = "jsonb")
  private String variables;

  protected NotificationTemplate() {
  }

  public NotificationTemplate(String tenantId, String name, Channel channel,
      String subject, String bodyTemplate, String variables) {
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.channel = Objects.requireNonNull(channel, "channel must not be null");
    this.subject = subject;
    this.bodyTemplate = Objects.requireNonNull(bodyTemplate, "bodyTemplate must not be null");
    this.variables = variables;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getBodyTemplate() {
    return bodyTemplate;
  }

  public void setBodyTemplate(String bodyTemplate) {
    this.bodyTemplate = bodyTemplate;
  }

  public String getVariables() {
    return variables;
  }

  public void setVariables(String variables) {
    this.variables = variables;
  }
}
