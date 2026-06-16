// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ensures {@code spring-boot-configuration-processor} emitted metadata for LLM client property
 * types. Several artifacts contribute {@code META-INF/spring-configuration-metadata.json}, so we
 * merge all copies present on the test classpath.
 */
class SpringBootLlmClientConfigurationMetadataTest {

  @Test
  void generatedMetadataDocumentsExpectedLlmKeys() throws Exception {
    Enumeration<URL> urls = Thread.currentThread()
        .getContextClassLoader()
        .getResources("META-INF/spring-configuration-metadata.json");
    List<URL> list = Collections.list(urls);
    assertThat(list).as("At least one spring-configuration-metadata.json on classpath").isNotEmpty();

    List<String> fragments = new ArrayList<>();
    for (URL url : list) {
      try (InputStream in = url.openStream()) {
        fragments.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    String merged = String.join("\n", fragments);

    assertThat(merged).contains("agentforge4j.llm.openai.api-key");
    assertThat(merged).contains("agentforge4j.llm.openai.default-model");
    assertThat(merged).contains("agentforge4j.llm.claude.api-key");
    assertThat(merged).contains("agentforge4j.llm.claude.max-token-size");
    assertThat(merged).contains("agentforge4j.llm.ollama.enabled");
    assertThat(merged).contains("agentforge4j.llm.vllm.url");
    assertThat(merged).contains("agentforge4j.llm.openai-compatible.base-url");
    assertThat(merged).contains("agentforge4j.llm.gemini.api-key");
    assertThat(merged).contains("agentforge4j.llm.azure-openai.api-key");
    assertThat(merged).contains("agentforge4j.llm.bedrock.enabled");
    assertThat(merged).contains("agentforge4j.llm.mistral.api-key");
  }
}
