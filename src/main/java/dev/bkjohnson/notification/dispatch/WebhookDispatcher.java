package dev.bkjohnson.notification.dispatch;

import dev.bkjohnson.notification.config.NotificationProperties;
import dev.bkjohnson.notification.security.HmacSigner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dispatches webhook notifications via HTTP POST with HMAC signing.
 */
@Component
public class WebhookDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(WebhookDispatcher.class);

  private final HmacSigner hmacSigner;
  private final NotificationProperties properties;
  private final HttpClient httpClient;

  @org.springframework.beans.factory.annotation.Autowired
  public WebhookDispatcher(HmacSigner hmacSigner,
      NotificationProperties properties) {
    this.hmacSigner = hmacSigner;
    this.properties = properties;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(
            Duration.ofMillis(properties.getWebhook().getConnectTimeoutMs()))
        .build();
  }

  // Visible for testing
  WebhookDispatcher(HmacSigner hmacSigner,
      NotificationProperties properties, HttpClient httpClient) {
    this.hmacSigner = hmacSigner;
    this.properties = properties;
    this.httpClient = httpClient;
  }

  /**
   * Sends a webhook POST with HMAC signature.
   *
   * @param callbackUrl the target URL
   * @param secret      the shared HMAC secret
   * @param deliveryId  the delivery ID for tracing
   * @param payloadJson the JSON payload
   * @throws WebhookDeliveryException if the target returns a non-2xx response or times out
   */
  public void send(String callbackUrl, String secret, String deliveryId,
      String payloadJson) {
    long timestamp = Instant.now().getEpochSecond();
    String signature = hmacSigner.sign(secret, timestamp, payloadJson);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(callbackUrl))
        .timeout(Duration.ofMillis(properties.getWebhook().getReadTimeoutMs()))
        .header("Content-Type", "application/json")
        .header("X-Signature-256", "sha256=" + signature)
        .header("X-Webhook-Timestamp", String.valueOf(timestamp))
        .header("X-Webhook-Delivery-Id", deliveryId)
        .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
        .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Webhook delivered successfully to {} (status {})",
            callbackUrl, response.statusCode());
      } else {
        throw new WebhookDeliveryException(
            "Webhook target returned HTTP " + response.statusCode()
                + ": " + response.body());
      }
    } catch (WebhookDeliveryException e) {
      throw e;
    } catch (Exception e) {
      throw new WebhookDeliveryException(
          "Failed to deliver webhook to " + callbackUrl + ": " + e.getMessage(),
          e);
    }
  }

  /**
   * Exception indicating a webhook delivery failure.
   */
  public static class WebhookDeliveryException extends RuntimeException {
    public WebhookDeliveryException(String message) {
      super(message);
    }

    public WebhookDeliveryException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
