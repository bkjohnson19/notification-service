package dev.bkjohnson.notification.config;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Azure Communication Services email client.
 */
@Configuration
@ConditionalOnProperty(prefix = "notification.email", name = "connection-string")
public class AcsEmailConfig {

  @Bean
  @ConditionalOnMissingBean
  public EmailClient emailClient(NotificationProperties properties) {
    return new EmailClientBuilder()
        .connectionString(properties.getEmail().getConnectionString())
        .buildClient();
  }
}
