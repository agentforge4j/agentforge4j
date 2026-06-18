// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

/** Test captor factory for the bearer provider id {@code openai}. */
public final class OpenAiCaptorLlmClientFactory extends CaptorLlmClientFactorySupport.Base {

  public OpenAiCaptorLlmClientFactory() {
    super("openai", true);
  }
}
