package dev.bkjohnson.notification.model.command;

import java.util.Objects;

/**
 * Command message published to Service Bus for async delivery processing.
 */
public record DeliveryCommand(
    String deliveryId,
    String tenantId
) {

  public DeliveryCommand {
    Objects.requireNonNull(deliveryId, "deliveryId must not be null");
    Objects.requireNonNull(tenantId, "tenantId must not be null");
  }
}
