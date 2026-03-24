package dev.bkjohnson.notification.model.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

class NotificationEventTypeTest {

    @Test
    void testNotificationSentConstant() {
        assertThat(NotificationEventType.NOTIFICATION_SENT).isEqualTo("notification.sent");
    }

    @Test
    void testNotificationDeliveredConstant() {
        assertThat(NotificationEventType.NOTIFICATION_DELIVERED).isEqualTo("notification.delivered");
    }

    @Test
    void testNotificationFailedConstant() {
        assertThat(NotificationEventType.NOTIFICATION_FAILED).isEqualTo("notification.failed");
    }

    @Test
    void testPrivateConstructor() throws Exception {
        Constructor<NotificationEventType> constructor =
                NotificationEventType.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();

        constructor.setAccessible(true);
        NotificationEventType instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
