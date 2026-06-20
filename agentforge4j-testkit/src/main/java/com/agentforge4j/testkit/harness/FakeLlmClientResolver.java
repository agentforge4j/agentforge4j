// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * An {@link LlmClientResolver} that serves a single {@link LlmClient} (the deterministic fake) for
 * every provider id, so the harness can drive any agent regardless of its {@code providerPreferences}.
 * The masquerade is sound because fake responses are keyed by workflow/step/agent/ordinal, never by
 * provider.
 *
 * <p>This resolver is <em>wildcard</em>: it resolves and reports availability for any provider rather
 * than enumerating a fixed set. The {@link LlmClientResolver} SPI cannot express unbounded
 * availability through {@link #listAvailableClients()} (a finite enumeration), so that method returns
 * an empty list. To avoid the contradiction of an empty enumeration starving provider selection, this
 * resolver is always paired with {@link FakeProviderSelectionStrategy}, which picks an agent's
 * declared preference without consulting the enumeration. This deliberately replaces the former
 * hardcoded provider catalog, which silently rejected any agent preferring a provider it omitted.
 */
final class FakeLlmClientResolver implements LlmClientResolver {

  private final LlmClient fake;

  FakeLlmClientResolver(LlmClient fake) {
    this.fake = Validate.notNull(fake, "fake client must not be null");
  }

  @Override
  public LlmClient resolve(String provider) {
    return fake;
  }

  @Override
  public boolean isProviderAvailable(String provider) {
    return true;
  }

  @Override
  public List<String> listAvailableClients() {
    return List.of();
  }
}
