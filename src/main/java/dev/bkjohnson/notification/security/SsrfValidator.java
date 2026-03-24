package dev.bkjohnson.notification.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates callback URLs to prevent SSRF attacks.
 * Rejects private/internal IP ranges, non-HTTPS URLs, and IP-based URLs.
 */
@Component
public class SsrfValidator {

  private static final Pattern IP_ADDRESS_PATTERN =
      Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

  private static final Pattern IPV6_PATTERN =
      Pattern.compile("^\\[?[0-9a-fA-F:]+]?$");

  private static final List<String> BLOCKED_HOSTNAMES = List.of(
      "localhost",
      "metadata.google.internal",
      "169.254.169.254"
  );

  private static final List<String> BLOCKED_SUFFIXES = List.of(
      ".svc.cluster.local",
      ".pod.cluster.local",
      ".internal"
  );

  /**
   * Validates a callback URL for safety against SSRF.
   *
   * @param url the URL to validate
   * @return a list of validation error messages; empty if valid
   */
  public List<String> validate(String url) {
    if (url == null || url.isBlank()) {
      return List.of("Callback URL must not be empty");
    }

    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return List.of("Callback URL is malformed: " + e.getMessage());
    }

    if (uri.getScheme() == null || !uri.getScheme().equals("https")) {
      return List.of("Callback URL must use HTTPS");
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return List.of("Callback URL must have a valid hostname");
    }

    if (IP_ADDRESS_PATTERN.matcher(host).matches() || IPV6_PATTERN.matcher(host).matches()) {
      return List.of("Callback URL must use a hostname, not an IP address");
    }

    if (BLOCKED_HOSTNAMES.contains(host.toLowerCase())) {
      return List.of("Callback URL hostname is blocked: " + host);
    }

    String lowerHost = host.toLowerCase();
    for (String suffix : BLOCKED_SUFFIXES) {
      if (lowerHost.endsWith(suffix)) {
        return List.of("Callback URL hostname is blocked: " + host);
      }
    }

    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      for (InetAddress addr : addresses) {
        if (isPrivateAddress(addr)) {
          return List.of(
              "Callback URL resolves to a private/internal IP address: " + host);
        }
      }
    } catch (UnknownHostException e) {
      return List.of("Callback URL hostname cannot be resolved: " + host);
    }

    return List.of();
  }

  private boolean isPrivateAddress(InetAddress address) {
    return address.isLoopbackAddress()
        || address.isSiteLocalAddress()
        || address.isLinkLocalAddress()
        || address.isAnyLocalAddress();
  }
}
