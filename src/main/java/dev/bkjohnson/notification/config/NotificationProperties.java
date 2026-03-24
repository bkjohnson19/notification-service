package dev.bkjohnson.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the notification service.
 */
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

  private final Feature feature = new Feature();
  private final Email email = new Email();
  private final Webhook webhook = new Webhook();

  public Feature getFeature() {
    return feature;
  }

  public Email getEmail() {
    return email;
  }

  public Webhook getWebhook() {
    return webhook;
  }

  /**
   * Feature flag configuration.
   */
  public static class Feature {
    private boolean bulkSendEnabled = false;
    private boolean dryRunMode = false;

    public boolean isBulkSendEnabled() {
      return bulkSendEnabled;
    }

    public void setBulkSendEnabled(boolean bulkSendEnabled) {
      this.bulkSendEnabled = bulkSendEnabled;
    }

    public boolean isDryRunMode() {
      return dryRunMode;
    }

    public void setDryRunMode(boolean dryRunMode) {
      this.dryRunMode = dryRunMode;
    }
  }

  /**
   * Email dispatch configuration.
   */
  public static class Email {
    private String connectionString;
    private String senderAddress = "noreply@notifications.example.com";

    public String getConnectionString() {
      return connectionString;
    }

    public void setConnectionString(String connectionString) {
      this.connectionString = connectionString;
    }

    public String getSenderAddress() {
      return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
      this.senderAddress = senderAddress;
    }
  }

  /**
   * Webhook dispatch configuration.
   */
  public static class Webhook {
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxRetries = 5;

    public int getConnectTimeoutMs() {
      return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
      this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
      return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
      this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }
  }
}
