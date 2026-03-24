package dev.bkjohnson.notification.dispatch;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.util.polling.SyncPoller;
import dev.bkjohnson.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dispatches email notifications via Azure Communication Services.
 */
@Component
public class EmailDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(EmailDispatcher.class);

  private final EmailClient emailClient;
  private final NotificationProperties properties;

  public EmailDispatcher(EmailClient emailClient,
      NotificationProperties properties) {
    this.emailClient = emailClient;
    this.properties = properties;
  }

  /**
   * Sends an email via Azure Communication Services.
   *
   * @param recipientAddress the recipient email address
   * @param subject          the email subject
   * @param htmlBody         the rendered HTML body
   */
  public void send(String recipientAddress, String subject, String htmlBody) {
    LOG.info("Sending email to {}", recipientAddress);

    EmailMessage emailMessage = new EmailMessage()
        .setSenderAddress(properties.getEmail().getSenderAddress())
        .setToRecipients(recipientAddress)
        .setSubject(subject)
        .setBodyHtml(htmlBody);

    SyncPoller<EmailSendResult, EmailSendResult> poller =
        emailClient.beginSend(emailMessage);
    poller.waitForCompletion();

    LOG.info("Email sent successfully to {}", recipientAddress);
  }
}
