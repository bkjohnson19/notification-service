package dev.bkjohnson.notification.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HmacSignerTest {

  private HmacSigner signer;

  @BeforeEach
  void setUp() {
    signer = new HmacSigner();
  }

  @Test
  void testSignKnownAnswer() throws Exception {
    String secret = "test-secret";
    long timestamp = 1700000000L;
    String body = "{\"event\":\"order.created\"}";

    // Compute expected HMAC independently
    String message = timestamp + "." + body;
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec keySpec = new SecretKeySpec(
        secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(keySpec);
    byte[] expectedHash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    String expectedSignature = HexFormat.of().formatHex(expectedHash);

    String actualSignature = signer.sign(secret, timestamp, body);

    assertThat(actualSignature).isEqualTo(expectedSignature);
  }

  @Test
  void testSignEmptyBody() {
    String secret = "my-secret";
    long timestamp = 1700000000L;

    String signature = signer.sign(secret, timestamp, "");

    assertThat(signature).isNotEmpty();
    assertThat(signature).matches("[0-9a-f]{64}");
  }

  @Test
  void testSignUnicodeBody() {
    String secret = "secret-key";
    long timestamp = 1700000000L;
    String body = "{\"message\": \"Hej v\u00e4rlden! \u2603 \ud83c\udf1f\"}";

    String signature = signer.sign(secret, timestamp, body);

    assertThat(signature).isNotEmpty();
    assertThat(signature).matches("[0-9a-f]{64}");

    // Same input must produce the same output
    String signature2 = signer.sign(secret, timestamp, body);
    assertThat(signature).isEqualTo(signature2);
  }

  @Test
  void testGenerateSecret() {
    String secret = signer.generateSecret();

    assertThat(secret).hasSize(64);
    assertThat(secret).matches("[0-9a-f]{64}");
  }

  @Test
  void testGenerateSecretUniqueness() {
    String secret1 = signer.generateSecret();
    String secret2 = signer.generateSecret();

    assertThat(secret1).isNotEqualTo(secret2);
  }
}
