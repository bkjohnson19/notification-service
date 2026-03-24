package dev.bkjohnson.notification.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 signing for webhook deliveries and secret generation.
 */
@Component
public class HmacSigner {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int SECRET_BYTE_LENGTH = 32;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /**
   * Computes HMAC-SHA256 signature for a webhook payload.
   *
   * @param secret    the shared secret
   * @param timestamp unix epoch seconds
   * @param body      the JSON body
   * @return hex-encoded signature
   */
  public String sign(String secret, long timestamp, String body) {
    String message = timestamp + "." + body;
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      SecretKeySpec keySpec = new SecretKeySpec(
          secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
      mac.init(keySpec);
      byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to compute HMAC signature", e);
    }
  }

  /**
   * Generates a cryptographically secure random HMAC secret.
   *
   * @return hex-encoded secret (64 hex chars = 32 bytes)
   */
  public String generateSecret() {
    byte[] secretBytes = new byte[SECRET_BYTE_LENGTH];
    SECURE_RANDOM.nextBytes(secretBytes);
    return HexFormat.of().formatHex(secretBytes);
  }
}
