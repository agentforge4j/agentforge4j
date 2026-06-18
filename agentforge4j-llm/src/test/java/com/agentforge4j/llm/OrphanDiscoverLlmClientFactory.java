// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;

/**
 * SPI factory deliberately absent from resolver configuration in discover ITs. Must never be
 * instantiated via {@link LlmClientFactory#create} when that configuration is omitted.
 */
public final class OrphanDiscoverLlmClientFactory implements LlmClientFactory {

  public static final String PROVIDER = "discover-orphan-not-configured";

  public OrphanDiscoverLlmClientFactory() {
  }

  @Override
  public String getProviderName() {
    return PROVIDER;
  }

  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    throw new AssertionError("Orphan factory must not be instantiated without configuration");
  }
}
