package dev.bkjohnson.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationPropertiesTest {

    @Test
    void testDefaultValues() {
        NotificationProperties properties = new NotificationProperties();

        assertThat(properties.getFeature()).isNotNull();
        assertThat(properties.getEmail()).isNotNull();
        assertThat(properties.getWebhook()).isNotNull();
    }

    @Test
    void testFeatureDefaults() {
        NotificationProperties.Feature feature = new NotificationProperties().getFeature();

        assertThat(feature.isBulkSendEnabled()).isFalse();
        assertThat(feature.isDryRunMode()).isFalse();
    }

    @Test
    void testFeatureSetBulkSendEnabled() {
        NotificationProperties.Feature feature = new NotificationProperties().getFeature();

        feature.setBulkSendEnabled(true);

        assertThat(feature.isBulkSendEnabled()).isTrue();
    }

    @Test
    void testFeatureSetDryRunMode() {
        NotificationProperties.Feature feature = new NotificationProperties().getFeature();

        feature.setDryRunMode(true);

        assertThat(feature.isDryRunMode()).isTrue();
    }

    @Test
    void testEmailDefaults() {
        NotificationProperties.Email email = new NotificationProperties().getEmail();

        assertThat(email.getConnectionString()).isNull();
        assertThat(email.getSenderAddress()).isEqualTo("noreply@notifications.example.com");
    }

    @Test
    void testEmailSetConnectionString() {
        NotificationProperties.Email email = new NotificationProperties().getEmail();

        email.setConnectionString("endpoint=https://acs.example.com;key=abc");

        assertThat(email.getConnectionString()).isEqualTo("endpoint=https://acs.example.com;key=abc");
    }

    @Test
    void testEmailSetSenderAddress() {
        NotificationProperties.Email email = new NotificationProperties().getEmail();

        email.setSenderAddress("custom@example.com");

        assertThat(email.getSenderAddress()).isEqualTo("custom@example.com");
    }

    @Test
    void testWebhookDefaults() {
        NotificationProperties.Webhook webhook = new NotificationProperties().getWebhook();

        assertThat(webhook.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(webhook.getReadTimeoutMs()).isEqualTo(10000);
        assertThat(webhook.getMaxRetries()).isEqualTo(5);
    }

    @Test
    void testWebhookSetConnectTimeoutMs() {
        NotificationProperties.Webhook webhook = new NotificationProperties().getWebhook();

        webhook.setConnectTimeoutMs(3000);

        assertThat(webhook.getConnectTimeoutMs()).isEqualTo(3000);
    }

    @Test
    void testWebhookSetReadTimeoutMs() {
        NotificationProperties.Webhook webhook = new NotificationProperties().getWebhook();

        webhook.setReadTimeoutMs(15000);

        assertThat(webhook.getReadTimeoutMs()).isEqualTo(15000);
    }

    @Test
    void testWebhookSetMaxRetries() {
        NotificationProperties.Webhook webhook = new NotificationProperties().getWebhook();

        webhook.setMaxRetries(10);

        assertThat(webhook.getMaxRetries()).isEqualTo(10);
    }
}
