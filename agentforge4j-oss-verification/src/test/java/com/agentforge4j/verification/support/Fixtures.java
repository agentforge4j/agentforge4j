// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.support;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for the config-surface verification tiers: resolving a test-resource fixture tree to
 * a filesystem {@link Path} (the checkout path contains spaces, so resolution goes through
 * {@link URL#toURI()}), and building a no-op fake LLM resolver for facade builds that never run an
 * agent step.
 */
public final class Fixtures {

  private Fixtures() {
  }

  /**
   * Resolves a classpath fixture directory (e.g. {@code /fixtures/loader/dup-agent/workflows}) to a
   * filesystem {@link Path}.
   *
   * @param classpathDir absolute classpath resource path of the fixture directory
   *
   * @return the resolved filesystem path
   */
  public static Path dir(String classpathDir) {
    URL url = Fixtures.class.getResource(classpathDir);
    if (url == null) {
      throw new IllegalStateException("Missing fixture directory on the classpath: " + classpathDir);
    }
    try {
      return Paths.get(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Unresolvable fixture URL: " + url, e);
    }
  }

  /**
   * Builds a resolver that serves a single empty fake client. Suitable for facade builds that load
   * config/agents/workflows but never invoke an agent (no scripted responses are needed).
   *
   * @return a no-op fake LLM client resolver
   */
  public static LlmClientResolver noOpLlmResolver() {
    FakeScript empty = new FakeScript(1, Map.of());
    FakeLlmClient client = new FakeLlmClient(new StaticFakeResponseSource(empty));
    return new DefaultLlmClientResolver(List.of(client));
  }
}
