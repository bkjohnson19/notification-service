package dev.bkjohnson.notification.model.entity;

import dev.bkjohnson.api.data.entity.BaseEntity;
import dev.bkjohnson.api.data.tenant.TenantColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Notification delivery record tracking async dispatch lifecycle.
 */
@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery extends BaseEntity {

  @TenantColumn
  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false)
  private Channel channel;

  @Column(name = "recipient_address", length = 1024)
  private String recipientAddress;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id")
  private NotificationTemplate template;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "webhook_subscription_id")
  private WebhookSubscription webhookSubscription;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private DeliveryStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  @Column(name = "error_detail", columnDefinition = "text")
  private String errorDetail;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_attempted_at")
  private Instant lastAttemptedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private String metadata;

  protected NotificationDelivery() {
  }

  public NotificationDelivery(String tenantId, Channel channel, String recipientAddress) {
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.channel = Objects.requireNonNull(channel, "channel must not be null");
    this.recipientAddress = recipientAddress;
    this.status = DeliveryStatus.QUEUED;
    this.attempts = 0;
  }

  public void markProcessing() {
    this.status = DeliveryStatus.PROCESSING;
  }

  public void markDelivered() {
    this.status = DeliveryStatus.DELIVERED;
    this.deliveredAt = Instant.now();
  }

  public void markFailed(String error) {
    this.status = DeliveryStatus.FAILED;
    this.errorDetail = error;
  }

  public void markDryRun() {
    this.status = DeliveryStatus.DRY_RUN;
  }

  public void recordAttempt() {
    this.attempts++;
    this.lastAttemptedAt = Instant.now();
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public String getRecipientAddress() {
    return recipientAddress;
  }

  public void setRecipientAddress(String recipientAddress) {
    this.recipientAddress = recipientAddress;
  }

  public NotificationTemplate getTemplate() {
    return template;
  }

  public void setTemplate(NotificationTemplate template) {
    this.template = template;
  }

  public WebhookSubscription getWebhookSubscription() {
    return webhookSubscription;
  }

  public void setWebhookSubscription(WebhookSubscription webhookSubscription) {
    this.webhookSubscription = webhookSubscription;
  }

  public DeliveryStatus getStatus() {
    return status;
  }

  public void setStatus(DeliveryStatus status) {
    this.status = status;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public void setErrorDetail(String errorDetail) {
    this.errorDetail = errorDetail;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public Instant getLastAttemptedAt() {
    return lastAttemptedAt;
  }

  public void setLastAttemptedAt(Instant lastAttemptedAt) {
    this.lastAttemptedAt = lastAttemptedAt;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Instant deliveredAt) {
    this.deliveredAt = deliveredAt;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }
}
