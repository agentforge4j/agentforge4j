// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

/** Test captor factory for the hyphenated bearer provider id {@code openai-compatible}. */
public final class OpenAiCompatibleCaptorLlmClientFactory
    extends CaptorLlmClientFactorySupport.Base {

  public OpenAiCompatibleCaptorLlmClientFactory() {
    super("openai-compatible", true);
  }
}
