// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.fake;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.fake.*} for the deterministic scripted provider.
 *
 * @param enabled opt-in toggle evaluated by {@linkplain FakeProviderAutoConfiguration}
 * @param ttl     stale-run time-to-live for the leak guard; {@code null} disables TTL eviction (the default) — a
 *                registered run is held until {@code deregister}
 * @param maxRuns optional dev/demo cap on concurrent tracked runs; {@code 0} (the default) disables the cap. Leave off
 *                (or high) for verification so an in-flight run is never evicted; when exceeded the least-recently-used
 *                run is evicted with a {@code WARN} log
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.fake")
public record FakeLlmClientProperties(Boolean enabled, Duration ttl, int maxRuns) {

}
