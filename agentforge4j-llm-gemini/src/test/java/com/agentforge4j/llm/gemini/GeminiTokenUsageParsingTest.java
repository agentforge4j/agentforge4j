// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiTokenUsageParsingTest {

  private final GeminiLlmClient client =
      new GeminiLlmClient(new ObjectMapper(), FixedGeminiConfiguration.defaults());

  @Test
  void tokenUsagePresentFromFixture() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("generate-with-usage.json"));
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(60);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(25);
    assertThat(response.tokenUsage().cachedInputTokens()).isEqualTo(8);
    assertThat(response.tokenUsage().cacheWriteTokens()).isNull();
    assertThat(response.modelUsed()).isEqualTo("gemini-2.0-flash-001");
  }

  @Test
  void tokenUsageAbsentWhenUsageMetadataMissing() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("generate-no-usage.json"));
    assertThat(response.tokenUsage()).isNull();
    assertThat(response.modelUsed()).isNull();
  }

  private static String fixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = GeminiTokenUsageParsingTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
