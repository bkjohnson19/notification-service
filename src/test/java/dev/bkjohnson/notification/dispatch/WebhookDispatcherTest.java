package dev.bkjohnson.notification.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bkjohnson.notification.config.NotificationProperties;
import dev.bkjohnson.notification.dispatch.WebhookDispatcher.WebhookDeliveryException;
import dev.bkjohnson.notification.security.HmacSigner;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

  @Mock
  private HmacSigner hmacSigner;
  @Mock
  private HttpClient httpClient;

  private NotificationProperties properties;
  private WebhookDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.getWebhook().setConnectTimeoutMs(5000);
    properties.getWebhook().setReadTimeoutMs(10000);
    dispatcher = new WebhookDispatcher(hmacSigner, properties, httpClient);
  }

  @SuppressWarnings("unchecked")
  @Test
  void testSuccessfulSend() throws Exception {
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);
    when(hmacSigner.sign(any(), any(long.class), any())).thenReturn("abc123signature");

    dispatcher.send(
        "https://example.com/webhook",
        "secret",
        "delivery-001",
        "{\"event\":\"test\"}"
    );

    verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  void testNon2xxResponse() throws Exception {
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(500);
    when(mockResponse.body()).thenReturn("Internal Server Error");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);
    when(hmacSigner.sign(any(), any(long.class), any())).thenReturn("sig");

    assertThatThrownBy(() -> dispatcher.send(
        "https://example.com/webhook",
        "secret",
        "delivery-001",
        "{\"event\":\"test\"}"
    ))
        .isInstanceOf(WebhookDeliveryException.class)
        .hasMessageContaining("HTTP 500");
  }

  @SuppressWarnings("unchecked")
  @Test
  void testConnectionFailure() throws Exception {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("Connection refused"));
    when(hmacSigner.sign(any(), any(long.class), any())).thenReturn("sig");

    assertThatThrownBy(() -> dispatcher.send(
        "https://example.com/webhook",
        "secret",
        "delivery-001",
        "{\"event\":\"test\"}"
    ))
        .isInstanceOf(WebhookDeliveryException.class)
        .hasMessageContaining("Failed to deliver webhook")
        .hasMessageContaining("Connection refused");
  }

  @SuppressWarnings("unchecked")
  @Test
  void testSignatureHeaders() throws Exception {
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);
    when(hmacSigner.sign(any(), any(long.class), any())).thenReturn("computed_signature");

    dispatcher.send(
        "https://example.com/webhook",
        "my-secret",
        "delivery-001",
        "{\"data\":\"value\"}"
    );

    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

    HttpRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.headers().firstValue("X-Signature-256"))
        .hasValue("sha256=computed_signature");
    assertThat(capturedRequest.headers().firstValue("X-Webhook-Delivery-Id"))
        .hasValue("delivery-001");
    assertThat(capturedRequest.headers().firstValue("X-Webhook-Timestamp"))
        .isPresent();
    assertThat(capturedRequest.headers().firstValue("Content-Type"))
        .hasValue("application/json");

    verify(hmacSigner).sign(any(), any(long.class), any());
  }

  @SuppressWarnings("unchecked")
  @Test
  void testSubTwoHundredResponse() throws Exception {
    // Covers the statusCode() >= 200 false branch (status < 200)
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(199);
    when(mockResponse.body()).thenReturn("Continue");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);
    when(hmacSigner.sign(any(), any(long.class), any())).thenReturn("sig");

    assertThatThrownBy(() -> dispatcher.send(
        "https://example.com/webhook",
        "secret",
        "delivery-001",
        "{\"event\":\"test\"}"
    ))
        .isInstanceOf(WebhookDeliveryException.class)
        .hasMessageContaining("HTTP 199");
  }
}
