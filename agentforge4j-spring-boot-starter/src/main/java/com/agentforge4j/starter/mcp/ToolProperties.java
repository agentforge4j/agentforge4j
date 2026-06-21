// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.mcp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.tools.*} configuration: tunables for tool invocation, including the authoritative timeout
 * enforced by the execution service and the outbound-egress policy.
 *
 * @param timeout      hard per-invocation timeout; defaults to 30s when {@code null}
 * @param maxRetries   maximum retry attempts; defaults to 0 when {@code null}
 * @param retryBackoff delay between retries; defaults to zero when {@code null}
 * @param egress       outbound-egress tunables under {@code agentforge4j.tools.egress.*}; may be {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.tools")
public record ToolProperties(Duration timeout, Integer maxRetries, Duration retryBackoff, Egress egress) {

  /**
   * Outbound-egress tunables.
   *
   * @param allowPrivateNetworks development-only escape hatch lifting the private/loopback/link-local/cloud-metadata
   *                             egress blocks; defaults to {@code false} (fail-closed) when {@code null}
   */
  public record Egress(Boolean allowPrivateNetworks) {

  }

  /**
   * @return whether private-network egress is allowed; {@code false} (fail-closed) when unset
   */
  public boolean allowPrivateNetworks() {
    return egress != null && Boolean.TRUE.equals(egress.allowPrivateNetworks());
  }
}
