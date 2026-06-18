// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

/** Test captor factory for the no-key provider id {@code ollama}. */
public final class OllamaCaptorLlmClientFactory extends CaptorLlmClientFactorySupport.Base {

  public OllamaCaptorLlmClientFactory() {
    super("ollama", false);
  }
}
