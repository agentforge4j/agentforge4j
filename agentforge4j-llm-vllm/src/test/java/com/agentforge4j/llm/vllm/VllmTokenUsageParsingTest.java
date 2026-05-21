package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VllmTokenUsageParsingTest {

  private final VllmLlmClient client =
      new VllmLlmClient(new ObjectMapper(), FixedVllmConfiguration.defaults());

  @Test
  void tokenUsagePresentFromFixture() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("chat-with-usage.json"));
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(80);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(16);
    assertThat(response.tokenUsage().cachedInputTokens()).isEqualTo(10);
    assertThat(response.tokenUsage().cacheWriteTokens()).isNull();
    assertThat(response.modelUsed()).isEqualTo("meta-llama/Llama-3-8B");
  }

  @Test
  void tokenUsageAbsentWhenUsageBlockMissing() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("chat-no-usage.json"));
    assertThat(response.tokenUsage()).isNull();
    assertThat(response.modelUsed()).isNull();
  }

  private static String fixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = VllmTokenUsageParsingTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
