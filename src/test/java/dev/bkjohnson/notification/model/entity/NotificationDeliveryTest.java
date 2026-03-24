package dev.bkjohnson.notification.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationDeliveryTest {

    @Test
    void testConstructorSetsDefaults() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        assertThat(delivery.getTenantId()).isEqualTo("tenant-1");
        assertThat(delivery.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(delivery.getRecipientAddress()).isEqualTo("user@example.com");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(delivery.getAttempts()).isZero();
    }

    @Test
    void testConstructorAllowsNullRecipientAddress() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.WEBHOOK, null);

        assertThat(delivery.getRecipientAddress()).isNull();
    }

    @Test
    void testConstructorRejectsNullTenantId() {
        assertThatThrownBy(() -> new NotificationDelivery(null, Channel.EMAIL, "user@example.com"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void testConstructorRejectsNullChannel() {
        assertThatThrownBy(() -> new NotificationDelivery("tenant-1", null, "user@example.com"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("channel");
    }

    @Test
    void testMarkProcessing() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.markProcessing();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PROCESSING);
    }

    @Test
    void testMarkDelivered() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.markDelivered();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isNotNull();
        assertThat(delivery.getDeliveredAt()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void testMarkFailed() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.markFailed("Connection timeout");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getErrorDetail()).isEqualTo("Connection timeout");
    }

    @Test
    void testMarkDryRun() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.markDryRun();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DRY_RUN);
    }

    @Test
    void testRecordAttempt() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        assertThat(delivery.getAttempts()).isZero();
        assertThat(delivery.getLastAttemptedAt()).isNull();

        delivery.recordAttempt();

        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastAttemptedAt()).isNotNull();

        Instant firstAttempt = delivery.getLastAttemptedAt();
        delivery.recordAttempt();

        assertThat(delivery.getAttempts()).isEqualTo(2);
        assertThat(delivery.getLastAttemptedAt()).isAfterOrEqualTo(firstAttempt);
    }

    @Test
    void testSetTenantId() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setTenantId("tenant-2");

        assertThat(delivery.getTenantId()).isEqualTo("tenant-2");
    }

    @Test
    void testSetChannel() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setChannel(Channel.WEBHOOK);

        assertThat(delivery.getChannel()).isEqualTo(Channel.WEBHOOK);
    }

    @Test
    void testSetRecipientAddress() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setRecipientAddress("other@example.com");

        assertThat(delivery.getRecipientAddress()).isEqualTo("other@example.com");
    }

    @Test
    void testSetTemplate() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "welcome", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        delivery.setTemplate(template);

        assertThat(delivery.getTemplate()).isSameAs(template);
    }

    @Test
    void testSetWebhookSubscription() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.WEBHOOK, null);
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "hook", "https://example.com/hook", "secret", null);

        delivery.setWebhookSubscription(subscription);

        assertThat(delivery.getWebhookSubscription()).isSameAs(subscription);
    }

    @Test
    void testSetStatus() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setStatus(DeliveryStatus.FAILED);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void testSetPayload() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setPayload("{\"key\": \"value\"}");

        assertThat(delivery.getPayload()).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void testSetErrorDetail() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setErrorDetail("Something went wrong");

        assertThat(delivery.getErrorDetail()).isEqualTo("Something went wrong");
    }

    @Test
    void testSetAttempts() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setAttempts(5);

        assertThat(delivery.getAttempts()).isEqualTo(5);
    }

    @Test
    void testSetLastAttemptedAt() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");
        Instant now = Instant.now();

        delivery.setLastAttemptedAt(now);

        assertThat(delivery.getLastAttemptedAt()).isEqualTo(now);
    }

    @Test
    void testSetDeliveredAt() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");
        Instant now = Instant.now();

        delivery.setDeliveredAt(now);

        assertThat(delivery.getDeliveredAt()).isEqualTo(now);
    }

    @Test
    void testSetIdempotencyKey() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setIdempotencyKey("idem-key-123");

        assertThat(delivery.getIdempotencyKey()).isEqualTo("idem-key-123");
    }

    @Test
    void testSetMetadata() {
        NotificationDelivery delivery = new NotificationDelivery("tenant-1", Channel.EMAIL, "user@example.com");

        delivery.setMetadata("{\"source\": \"api\"}");

        assertThat(delivery.getMetadata()).isEqualTo("{\"source\": \"api\"}");
    }
}
