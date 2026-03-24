package dev.bkjohnson.notification.config;

import com.azure.communication.email.EmailClient;
import dev.bkjohnson.api.events.DomainEventPublisher;
import dev.bkjohnson.api.events.test.InMemoryDomainEventPublisher;
import dev.bkjohnson.messaging.MessageProducer;
import dev.bkjohnson.messaging.test.fake.InMemoryMessageProducer;
import dev.bkjohnson.notification.model.command.DeliveryCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration providing in-memory implementations for integration tests.
 */
@org.springframework.context.annotation.Configuration
@org.springframework.context.annotation.Profile("test")
public class TestConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MessageProducer<DeliveryCommand> messageProducer() {
    return new InMemoryMessageProducer<>("notification-delivery-test");
  }

  @Bean
  @ConditionalOnMissingBean
  public DomainEventPublisher domainEventPublisher() {
    return new InMemoryDomainEventPublisher();
  }

  @Bean
  @ConditionalOnMissingBean
  public EmailClient emailClient() {
    return org.mockito.Mockito.mock(EmailClient.class);
  }
}
