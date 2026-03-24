package dev.bkjohnson.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.communication.email.EmailClient;
import org.junit.jupiter.api.Test;

class AcsEmailConfigTest {

  @Test
  void testEmailClientBeanCreation() {
    // The ACS SDK validates the connection string format.
    // Use a properly formatted (but fake) connection string to exercise the bean method.
    NotificationProperties properties = new NotificationProperties();
    properties.getEmail().setConnectionString(
        "endpoint=https://fake.communication.azure.com/;accesskey=fakeAccessKey123==");

    AcsEmailConfig config = new AcsEmailConfig();
    EmailClient client = config.emailClient(properties);

    assertThat(client).isNotNull();
  }
}
