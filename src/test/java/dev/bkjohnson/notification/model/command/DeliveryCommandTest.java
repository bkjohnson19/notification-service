package dev.bkjohnson.notification.model.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeliveryCommandTest {

    @Test
    void testRecordCreation() {
        DeliveryCommand command = new DeliveryCommand("delivery-1", "tenant-1");

        assertThat(command.deliveryId()).isEqualTo("delivery-1");
        assertThat(command.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void testRejectsNullDeliveryId() {
        assertThatThrownBy(() -> new DeliveryCommand(null, "tenant-1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("deliveryId");
    }

    @Test
    void testRejectsNullTenantId() {
        assertThatThrownBy(() -> new DeliveryCommand("delivery-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void testEquality() {
        DeliveryCommand command1 = new DeliveryCommand("d-1", "t-1");
        DeliveryCommand command2 = new DeliveryCommand("d-1", "t-1");

        assertThat(command1).isEqualTo(command2);
        assertThat(command1.hashCode()).isEqualTo(command2.hashCode());
    }

    @Test
    void testInequality() {
        DeliveryCommand command1 = new DeliveryCommand("d-1", "t-1");
        DeliveryCommand command2 = new DeliveryCommand("d-2", "t-1");

        assertThat(command1).isNotEqualTo(command2);
    }

    @Test
    void testToString() {
        DeliveryCommand command = new DeliveryCommand("d-1", "t-1");

        assertThat(command.toString()).contains("d-1").contains("t-1");
    }
}
