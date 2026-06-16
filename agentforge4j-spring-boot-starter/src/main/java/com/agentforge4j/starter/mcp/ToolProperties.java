// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.mcp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.tools.*} configuration: tunables for tool invocation, including the
 * authoritative timeout enforced by the execution service.
 *
 * @param timeout      hard per-invocation timeout; defaults to 30s when {@code null}
 * @param maxRetries   maximum retry attempts; defaults to 0 when {@code null}
 * @param retryBackoff delay between retries; defaults to zero when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.tools")
public record ToolProperties(Duration timeout, Integer maxRetries, Duration retryBackoff) {

}
