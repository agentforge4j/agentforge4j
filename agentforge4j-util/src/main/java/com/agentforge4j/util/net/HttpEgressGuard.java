// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.net;

import com.agentforge4j.util.Validate;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Default {@link OutboundEgressGuard}: classifies a target {@link URI} as eligible only when its scheme is
 * {@code http}/{@code https} and its host resolves entirely to public addresses. Any private, loopback, link-local,
 * cloud-metadata ({@code 169.254.169.254}), carrier-grade-NAT, unique-local, or wildcard address — or a host that
 * cannot be resolved — yields a denied {@link EgressCheckResult}.
 *
 * <p>This is the network-safety sibling of {@code Validate.requireWithinBase} (path-traversal): a
 * small, dependency-light primitive shared by every module that makes tool-driven outbound calls. It
 * <em>classifies and never enforces</em> — it does not throw a policy exception; the denied result
 * carries a non-secret host/reason description, never a request body, header, or secret. Callers own the reaction.
 *
 * <p><strong>DNS-rebinding:</strong> the guard resolves the host and re-checks every resolved
 * address immediately before the caller issues the request (resolve-and-recheck). It does not pin the resolved IP for
 * the connection, so a host that re-resolves to a non-public address between this check and the actual connect is a
 * known residual time-of-check/time-of-use gap; IP-pinning is a future hardening step.
 *
 * <p><strong>allowPrivateNetworks:</strong> when constructed with {@code true} the private,
 * loopback, link-local, and metadata classifications are lifted wholesale — a development-only escape hatch that
 * disables the cloud-metadata-IP protection. The {@code http}/{@code https} scheme allowlist is <em>never</em> lifted.
 */
public final class HttpEgressGuard implements OutboundEgressGuard {

  private final boolean allowPrivateNetworks;

  /**
   * @param allowPrivateNetworks when {@code true}, lifts the private/loopback/link-local/metadata blocks (development
   *                             only); the scheme allowlist still applies
   */
  public HttpEgressGuard(boolean allowPrivateNetworks) {
    this.allowPrivateNetworks = allowPrivateNetworks;
  }

  @Override
  public EgressCheckResult check(URI uri) {
    Validate.notNull(uri, "uri must not be null");
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      return EgressCheckResult.deny("URL scheme '%s' is not http or https".formatted(scheme));
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return EgressCheckResult.deny("URL has no host to classify");
    }
    if (allowPrivateNetworks) {
      return EgressCheckResult.allow();
    }
    final InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      return EgressCheckResult.deny("host '%s' could not be resolved".formatted(host));
    }
    for (InetAddress address : addresses) {
      if (isBlocked(address)) {
        return EgressCheckResult.deny(
            "host '%s' resolves to a non-public address (%s)".formatted(host, address.getHostAddress()));
      }
    }
    return EgressCheckResult.allow();
  }

  private static boolean isBlocked(InetAddress address) {
    // Classify an IPv4-mapped IPv6 literal (::ffff:a.b.c.d) by its embedded IPv4 address, so a
    // mapped form cannot slip past the IPv4 predicates below.
    InetAddress effective = unwrapIpv4Mapped(address);
    if (effective.isLoopbackAddress()
        || effective.isAnyLocalAddress()
        || effective.isLinkLocalAddress()
        || effective.isSiteLocalAddress()
        || effective.isMulticastAddress()) {
      return true;
    }
    byte[] octets = effective.getAddress();
    if (octets.length == 4) {
      // Carrier-grade NAT 100.64.0.0/10 (RFC 6598) is not site-local per the JDK predicates.
      return (octets[0] & 0xff) == 100 && (octets[1] & 0xc0) == 0x40;
    }
    // IPv6 unique-local addresses (fc00::/7) are not covered by the JDK predicates above.
    return (octets[0] & 0xfe) == 0xfc;
  }

  /**
   * Returns the embedded IPv4 address of an IPv4-mapped IPv6 address ({@code ::ffff:a.b.c.d}), or the original address
   * otherwise. Only the mapped form (first ten bytes zero, then {@code 0xFFFF}) is unwrapped; IPv4-compatible addresses
   * are not, so genuine IPv6 specials such as {@code ::1} and {@code ::} keep their IPv6 classification.
   *
   * @param address the resolved address
   *
   * @return the embedded IPv4 address when {@code address} is IPv4-mapped, otherwise {@code address}
   */
  private static InetAddress unwrapIpv4Mapped(InetAddress address) {
    byte[] bytes = address.getAddress();
    if (bytes.length != 16 || (bytes[10] & 0xff) != 0xff || (bytes[11] & 0xff) != 0xff) {
      return address;
    }
    for (int i = 0; i < 10; i++) {
      if (bytes[i] != 0) {
        return address;
      }
    }
    try {
      return InetAddress.getByAddress(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
    } catch (UnknownHostException e) {
      // Unreachable for a 4-byte array, but fail closed by classifying the original address.
      return address;
    }
  }
}
