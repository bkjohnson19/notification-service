package dev.bkjohnson.notification.model.entity;

import dev.bkjohnson.api.data.entity.SoftDeletableEntity;
import dev.bkjohnson.api.data.tenant.TenantColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Webhook subscription entity for outbound callback delivery.
 */
@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription extends SoftDeletableEntity {

  @TenantColumn
  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "callback_url", nullable = false, length = 2048)
  private String callbackUrl;

  @Column(name = "secret_encrypted", nullable = false, length = 512)
  private String secretEncrypted;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "event_types", columnDefinition = "jsonb")
  private String eventTypes;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  protected WebhookSubscription() {
  }

  public WebhookSubscription(String tenantId, String name, String callbackUrl,
      String secretEncrypted, String eventTypes) {
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.callbackUrl = Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");
    this.secretEncrypted =
        Objects.requireNonNull(secretEncrypted, "secretEncrypted must not be null");
    this.eventTypes = eventTypes;
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

  public String getCallbackUrl() {
    return callbackUrl;
  }

  public void setCallbackUrl(String callbackUrl) {
    this.callbackUrl = callbackUrl;
  }

  public String getSecretEncrypted() {
    return secretEncrypted;
  }

  public void setSecretEncrypted(String secretEncrypted) {
    this.secretEncrypted = secretEncrypted;
  }

  public String getEventTypes() {
    return eventTypes;
  }

  public void setEventTypes(String eventTypes) {
    this.eventTypes = eventTypes;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
