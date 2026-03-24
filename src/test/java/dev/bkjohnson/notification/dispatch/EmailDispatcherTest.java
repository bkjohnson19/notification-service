package dev.bkjohnson.notification.dispatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.util.polling.SyncPoller;
import dev.bkjohnson.notification.config.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

  @Mock
  private EmailClient emailClient;

  private NotificationProperties properties;
  private EmailDispatcher emailDispatcher;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.getEmail().setSenderAddress("sender@example.com");
    emailDispatcher = new EmailDispatcher(emailClient, properties);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSendEmail() {
    SyncPoller<EmailSendResult, EmailSendResult> poller = mock(SyncPoller.class);
    when(emailClient.beginSend(any(EmailMessage.class))).thenReturn(poller);

    emailDispatcher.send("recipient@example.com", "Test Subject", "<p>Hello</p>");

    ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
    verify(emailClient).beginSend(captor.capture());
    verify(poller).waitForCompletion();

    EmailMessage sentMessage = captor.getValue();
    assertThat(sentMessage.getSenderAddress()).isEqualTo("sender@example.com");
    assertThat(sentMessage.getSubject()).isEqualTo("Test Subject");
    assertThat(sentMessage.getBodyHtml()).isEqualTo("<p>Hello</p>");
  }
}
