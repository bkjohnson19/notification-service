package dev.bkjohnson.notification.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebhookSubscriptionTest {

    @Test
    void testConstructorSetsFields() {
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "my-hook", "https://example.com/hook", "encrypted-secret",
                "[\"order.created\"]");

        assertThat(subscription.getTenantId()).isEqualTo("tenant-1");
        assertThat(subscription.getName()).isEqualTo("my-hook");
        assertThat(subscription.getCallbackUrl()).isEqualTo("https://example.com/hook");
        assertThat(subscription.getSecretEncrypted()).isEqualTo("encrypted-secret");
        assertThat(subscription.getEventTypes()).isEqualTo("[\"order.created\"]");
        assertThat(subscription.isActive()).isTrue();
    }

    @Test
    void testConstructorAllowsNullEventTypes() {
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "hook", "https://example.com", "secret", null);

        assertThat(subscription.getEventTypes()).isNull();
    }

    @Test
    void testConstructorRejectsNullTenantId() {
        assertThatThrownBy(() -> new WebhookSubscription(null, "n", "u", "s", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void testConstructorRejectsNullName() {
        assertThatThrownBy(() -> new WebhookSubscription("t", null, "u", "s", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void testConstructorRejectsNullCallbackUrl() {
        assertThatThrownBy(() -> new WebhookSubscription("t", "n", null, "s", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("callbackUrl");
    }

    @Test
    void testConstructorRejectsNullSecretEncrypted() {
        assertThatThrownBy(() -> new WebhookSubscription("t", "n", "u", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("secretEncrypted");
    }

    @Test
    void testSetTenantId() {
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "hook", "https://example.com", "secret", null);

        subscription.setTenantId("tenant-2");

        assertThat(subscription.getTenantId()).isEqualTo("tenant-2");
    }

    @Test
    void testSetName() {
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "old-name", "https://example.com", "secret", null);

        subscription.setName("new-name");

        assertThat(subscription.getName()).isEqualTo("new-name");
    }

    @Test
    void testSetCallbackUrl() {
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "hook", "https://old.example.com", "secret", null);

        subscription.setCallbackUrl("https://new.example.com");

        assertThat(subscription.getCallbackUrl()).isEqualTo("https://new.example.com");
    }

    @Test
    void testSetSecretEncrypted() {
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "hook", "https://example.com", "old-secret", null);

        subscription.setSecretEncrypted("new-secret");

        assertThat(subscription.getSecretEncrypted()).isEqualTo("new-secret");
    }

    @Test
    void testSetEventTypes() {
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "hook", "https://example.com", "secret", null);

        subscription.setEventTypes("[\"user.created\"]");

        assertThat(subscription.getEventTypes()).isEqualTo("[\"user.created\"]");
    }

    @Test
    void testSetActive() {
        WebhookSubscription subscription = new WebhookSubscription(
                "tenant-1", "hook", "https://example.com", "secret", null);

        assertThat(subscription.isActive()).isTrue();

        subscription.setActive(false);

        assertThat(subscription.isActive()).isFalse();
    }
}
