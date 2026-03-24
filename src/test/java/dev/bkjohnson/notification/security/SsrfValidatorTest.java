package dev.bkjohnson.notification.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SsrfValidatorTest {

  private SsrfValidator validator;

  @BeforeEach
  void setUp() {
    validator = new SsrfValidator();
  }

  @Test
  void testValidPublicHttpsUrl() {
    // google.com resolves to a public IP
    List<String> errors = validator.validate("https://www.google.com/callback");

    assertThat(errors).isEmpty();
  }

  @Test
  void testHttpRejected() {
    List<String> errors = validator.validate("http://example.com/callback");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("HTTPS");
  }

  @Test
  void testLocalhostRejected() {
    List<String> errors = validator.validate("https://localhost/callback");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("blocked");
  }

  @Test
  void testPrivateIpRejected() {
    // Direct IP addresses are rejected before DNS resolution
    List<String> errors10 = validator.validate("https://10.0.0.1/callback");
    assertThat(errors10).hasSize(1);
    assertThat(errors10.get(0)).contains("IP address");

    List<String> errors172 = validator.validate("https://172.16.0.1/callback");
    assertThat(errors172).hasSize(1);
    assertThat(errors172.get(0)).contains("IP address");

    List<String> errors192 = validator.validate("https://192.168.1.1/callback");
    assertThat(errors192).hasSize(1);
    assertThat(errors192.get(0)).contains("IP address");

    List<String> errors127 = validator.validate("https://127.0.0.1/callback");
    assertThat(errors127).hasSize(1);
    assertThat(errors127.get(0)).contains("IP address");
  }

  @Test
  void testIpAddressRejected() {
    List<String> errors = validator.validate("https://93.184.216.34/callback");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("IP address");
  }

  @Test
  void testKubernetesInternalDns() {
    List<String> errorsSvc = validator.validate(
        "https://my-service.default.svc.cluster.local/webhook");
    assertThat(errorsSvc).hasSize(1);
    assertThat(errorsSvc.get(0)).contains("blocked");

    List<String> errorsPod = validator.validate(
        "https://my-pod.default.pod.cluster.local/webhook");
    assertThat(errorsPod).hasSize(1);
    assertThat(errorsPod.get(0)).contains("blocked");
  }

  @Test
  void testLinkLocalBlocked() {
    // 169.254.x.x is a direct IP, caught by IP pattern check
    List<String> errors = validator.validate("https://169.254.169.254/latest/meta-data");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("IP address");
  }

  @Test
  void testUrlWithPort() {
    List<String> errors = validator.validate("https://www.google.com:8443/callback");

    assertThat(errors).isEmpty();
  }

  @Test
  void testUrlWithPath() {
    List<String> errors = validator.validate(
        "https://www.google.com/webhooks/v1/notify?token=abc123");

    assertThat(errors).isEmpty();
  }

  @Test
  void testMalformedUrl() {
    List<String> errors = validator.validate("not a url at all");

    assertThat(errors).hasSize(1);
    // Malformed URLs are caught by URI.create() and return a malformed error
    assertThat(errors.get(0)).containsIgnoringCase("malformed");
  }

  @Test
  void testEmptyUrl() {
    List<String> errors = validator.validate("");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("empty");
  }

  @Test
  void testNullUrl() {
    List<String> errors = validator.validate(null);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("empty");
  }

  @Test
  void testIpv6AddressRejected() {
    // IPv6 loopback in bracket notation — caught by IPV6_PATTERN
    List<String> errors = validator.validate("https://[::1]/callback");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("IP address");
  }

  @Test
  void testInternalSuffixBlocked() {
    List<String> errors = validator.validate(
        "https://some-service.internal/callback");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("blocked");
  }

  @Test
  void testPodClusterLocalBlocked() {
    List<String> errors = validator.validate(
        "https://app.ns.pod.cluster.local/webhook");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("blocked");
  }

  @Test
  void testSvcClusterLocalAlsoBlocked() {
    // Ensures the for-loop iterates past the first suffix entry
    List<String> errors = validator.validate(
        "https://api.default.svc.cluster.local/hook");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("blocked");
  }

  @Test
  void testUnresolvableHostnameRejected() {
    // A hostname that cannot be resolved via DNS
    List<String> errors = validator.validate(
        "https://this-host-does-not-exist-xyzzy-12345.example/callback");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("cannot be resolved");
  }

  @Test
  void testMetadataGoogleInternalBlocked() {
    List<String> errors = validator.validate(
        "https://metadata.google.internal/computeMetadata/v1/");

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("blocked");
  }

  @Test
  void testSchemeNullRejected() {
    // URI with no scheme (just a path) — scheme is null
    List<String> errors = validator.validate("https:///no-host");

    assertThat(errors).hasSize(1);
    // Host will be null/blank
    assertThat(errors.get(0)).containsIgnoringCase("hostname");
  }
}
