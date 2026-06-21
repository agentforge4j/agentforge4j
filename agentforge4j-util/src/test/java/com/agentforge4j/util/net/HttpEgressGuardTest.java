// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.net;

import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpEgressGuardTest {

  private final HttpEgressGuard guard = new HttpEgressGuard(false);
  private final HttpEgressGuard permissive = new HttpEgressGuard(true);

  @Test
  void allowsAPublicAddress() {
    assertThat(guard.check(URI.create("https://8.8.8.8/")).allowed()).isTrue();
  }

  @Test
  void deniesTheCloudMetadataAddressNamingTheHost() {
    EgressCheckResult result = guard.check(URI.create("http://169.254.169.254/latest/meta-data"));

    assertThat(result.allowed()).isFalse();
    assertThat(result.reason()).contains("169.254.169.254");
  }

  @Test
  void deniesIpv4Loopback() {
    assertThat(guard.check(URI.create("http://127.0.0.1/")).allowed()).isFalse();
  }

  @Test
  void deniesIpv6Loopback() {
    assertThat(guard.check(URI.create("http://[::1]/")).allowed()).isFalse();
  }

  @Test
  void deniesRfc1918PrivateRanges() {
    assertThat(guard.check(URI.create("http://10.0.0.1/")).allowed()).isFalse();
    assertThat(guard.check(URI.create("http://172.16.0.1/")).allowed()).isFalse();
    assertThat(guard.check(URI.create("http://192.168.1.1/")).allowed()).isFalse();
  }

  @Test
  void deniesIpv4LinkLocal() {
    assertThat(guard.check(URI.create("http://169.254.1.1/")).allowed()).isFalse();
  }

  @Test
  void deniesIpv6UniqueLocalAndLinkLocal() {
    assertThat(guard.check(URI.create("http://[fc00::1]/")).allowed()).isFalse();
    assertThat(guard.check(URI.create("http://[fe80::1]/")).allowed()).isFalse();
  }

  @Test
  void deniesCarrierGradeNatRange() {
    // RFC 6598 100.64.0.0/10 is not site-local per the JDK predicates.
    assertThat(guard.check(URI.create("http://100.64.0.1/")).allowed()).isFalse();
    assertThat(guard.check(URI.create("http://100.127.255.254/")).allowed()).isFalse();
  }

  @Test
  void allowsAddressesJustOutsideTheCarrierGradeNatRange() {
    // 100.63.x and 100.128.x are public — the /10 boundary must not over-deny.
    assertThat(guard.check(URI.create("http://100.63.255.255/")).allowed()).isTrue();
    assertThat(guard.check(URI.create("http://100.128.0.1/")).allowed()).isTrue();
  }

  @Test
  void deniesIpv4MappedIpv6LiteralsForNonPublicTargets() {
    assertThat(guard.check(URI.create("http://[::ffff:169.254.169.254]/")).allowed()).isFalse();
    assertThat(guard.check(URI.create("http://[::ffff:127.0.0.1]/")).allowed()).isFalse();
    assertThat(guard.check(URI.create("http://[::ffff:10.0.0.1]/")).allowed()).isFalse();
  }

  @Test
  void deniesTheWildcardAddress() {
    assertThat(guard.check(URI.create("http://0.0.0.0/")).allowed()).isFalse();
  }

  @Test
  void deniesNonHttpSchemesNamingTheScheme() {
    EgressCheckResult ftp = guard.check(URI.create("ftp://example.com/"));
    assertThat(ftp.allowed()).isFalse();
    assertThat(ftp.reason()).contains("scheme");

    assertThat(guard.check(URI.create("file:///etc/passwd")).allowed()).isFalse();
  }

  @Test
  void resolvesHostnameAndDeniesWhenEveryResolvedAddressIsNonPublic() {
    // localhost resolves to loopback (127.0.0.1 and/or ::1) — exercises the resolve-and-check-all
    // path with every resolved address non-public.
    assertThat(guard.check(URI.create("http://localhost/")).allowed()).isFalse();
  }

  @Test
  void deniesAnUnresolvableHostFailClosed() {
    // The reserved .invalid TLD (RFC 2606) never resolves, so the guard fails closed.
    EgressCheckResult result = guard.check(URI.create("http://host.agentforge4j-egress.invalid/"));

    assertThat(result.allowed()).isFalse();
    assertThat(result.reason()).contains("could not be resolved");
  }

  @Test
  void allowPrivateNetworksLiftsPrivateClassificationButNotTheSchemeAllowlist() {
    assertThat(permissive.check(URI.create("http://127.0.0.1/")).allowed()).isTrue();
    assertThat(permissive.check(URI.create("http://169.254.169.254/")).allowed()).isTrue();

    EgressCheckResult ftp = permissive.check(URI.create("ftp://127.0.0.1/"));
    assertThat(ftp.allowed()).isFalse();
    assertThat(ftp.reason()).contains("scheme");
  }
}
