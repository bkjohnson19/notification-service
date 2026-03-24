package dev.bkjohnson.notification.model.entity;

/**
 * Notification delivery lifecycle status.
 */
public enum DeliveryStatus {
  QUEUED,
  PROCESSING,
  DELIVERED,
  FAILED,
  DRY_RUN
}
