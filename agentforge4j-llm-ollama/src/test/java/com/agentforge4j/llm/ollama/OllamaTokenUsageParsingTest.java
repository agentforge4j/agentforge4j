package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaTokenUsageParsingTest {

  private final OllamaLlmClient client =
      new OllamaLlmClient(new ObjectMapper(), new OllamaLlmClientTest.TestOllamaConfiguration());

  @Test
  void tokenUsagePresentFromFixture() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("chat-with-usage.json"));
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(50);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(12);
    assertThat(response.tokenUsage().cachedInputTokens()).isNull();
    assertThat(response.tokenUsage().cacheWriteTokens()).isNull();
  }

  @Test
  void tokenUsageAbsentWhenEvalCountsMissing() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("chat-no-usage.json"));
    assertThat(response.tokenUsage()).isNull();
  }

  private static String fixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = OllamaTokenUsageParsingTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
