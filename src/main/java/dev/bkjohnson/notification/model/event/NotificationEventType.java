package dev.bkjohnson.notification.model.event;

/**
 * Domain event types for notification lifecycle.
 */
public final class NotificationEventType {

  public static final String NOTIFICATION_SENT = "notification.sent";
  public static final String NOTIFICATION_DELIVERED = "notification.delivered";
  public static final String NOTIFICATION_FAILED = "notification.failed";

  private NotificationEventType() {
  }
}
