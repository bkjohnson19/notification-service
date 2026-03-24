package dev.bkjohnson.notification.controller;

import dev.bkjohnson.notification.config.NotificationProperties;
import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.service.NotificationService;
import dev.bkjohnson.api.auth.RequireScope;
import dev.bkjohnson.api.bulk.BulkOperation;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.api.idempotency.Idempotent;
import dev.bkjohnson.api.ratelimit.RateLimit;
import dev.bkjohnson.api.response.RawResponse;
import dev.bkjohnson.api.version.ApiVersion;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for sending notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@ApiVersion("v1")
@RequireScope("notifications.send")
public class SendController {

  private final NotificationService notificationService;
  private final NotificationProperties properties;

  public SendController(NotificationService notificationService,
      NotificationProperties properties) {
    this.notificationService = notificationService;
    this.properties = properties;
  }

  @PostMapping("/send")
  @RawResponse
  @Idempotent
  @RateLimit(permits = 60, windowSeconds = 60)
  public ResponseEntity<SendResponse> sendNotification(
      @RequestBody SendRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false)
      String idempotencyKey) {

    String recipientAddress = null;
    String recipientName = null;
    if (request.recipient() != null) {
      recipientAddress = request.recipient().address();
      recipientName = request.recipient().name();
    }

    NotificationDelivery delivery = notificationService.submitNotification(
        request.channel(),
        recipientAddress, recipientName,
        request.templateId(), request.templateVariables(),
        request.subject(), request.body(), request.contentType(),
        request.webhookSubscriptionId(), request.payload(),
        request.callbackUrl(), request.metadata(),
        idempotencyKey);

    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(toResponse(delivery));
  }

  @PostMapping("/send-bulk")
  @RawResponse
  @BulkOperation
  @RateLimit(permits = 10, windowSeconds = 60)
  public ResponseEntity<Object> sendBulkNotifications(
      @RequestBody BulkSendRequest request) {

    if (!properties.getFeature().isBulkSendEnabled()) {
      throw new NotFoundException("Bulk send endpoint is not available");
    }

    List<BulkItemResult> results = request.items().stream()
        .map(item -> processBulkItem(item))
        .toList();

    long succeeded = results.stream().filter(BulkItemResult::success).count();
    long failed = results.size() - succeeded;

    BulkSendResponse response = new BulkSendResponse(
        new BulkSummary(results.size(), (int) succeeded, (int) failed),
        results);

    return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
  }

  private BulkItemResult processBulkItem(BulkSendItem item) {
    try {
      SendRequest data = item.data();
      String recipientAddress = null;
      String recipientName = null;
      if (data.recipient() != null) {
        recipientAddress = data.recipient().address();
        recipientName = data.recipient().name();
      }

      NotificationDelivery delivery = notificationService.submitNotification(
          data.channel(),
          recipientAddress, recipientName,
          data.templateId(), data.templateVariables(),
          data.subject(), data.body(), data.contentType(),
          data.webhookSubscriptionId(), data.payload(),
          data.callbackUrl(), data.metadata(),
          null);

      return new BulkItemResult(item.clientItemId(), true,
          toResponse(delivery), null);
    } catch (Exception e) {
      return new BulkItemResult(item.clientItemId(), false, null,
          e.getMessage());
    }
  }

  private SendResponse toResponse(NotificationDelivery delivery) {
    return new SendResponse(
        delivery.getId(),
        delivery.getStatus().name(),
        "/api/v1/notification-deliveries/" + delivery.getId(),
        delivery.getCreatedAt() != null
            ? delivery.getCreatedAt().atOffset(ZoneOffset.UTC) : null
    );
  }

  record SendRequest(
      String channel,
      Recipient recipient,
      String templateId,
      Map<String, Object> templateVariables,
      String subject,
      String body,
      String contentType,
      String webhookSubscriptionId,
      Map<String, Object> payload,
      String callbackUrl,
      Map<String, String> metadata
  ) {
  }

  record Recipient(String address, String name) {
  }

  record SendResponse(
      String deliveryId,
      String status,
      String statusUrl,
      OffsetDateTime queuedAt
  ) {
  }

  record BulkSendRequest(List<BulkSendItem> items) {
  }

  record BulkSendItem(String clientItemId, SendRequest data) {
  }

  record BulkSendResponse(BulkSummary summary, List<BulkItemResult> results) {
  }

  record BulkSummary(int total, int succeeded, int failed) {
  }

  record BulkItemResult(
      String clientItemId,
      boolean success,
      SendResponse data,
      String error
  ) {
  }
}
