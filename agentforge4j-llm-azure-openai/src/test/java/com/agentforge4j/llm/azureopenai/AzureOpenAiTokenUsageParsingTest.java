package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiTokenUsageParsingTest {

  private final AzureOpenAiLlmClient client =
      new AzureOpenAiLlmClient(new ObjectMapper(), FixedAzureOpenAiConfiguration.defaults());

  @Test
  void tokenUsagePresentFromFixture() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("chat-with-usage.json"));
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(70);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(18);
    assertThat(response.tokenUsage().cachedInputTokens()).isEqualTo(5);
    assertThat(response.tokenUsage().cacheWriteTokens()).isNull();
    assertThat(response.modelUsed()).isEqualTo("gpt-4o");
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
    try (InputStream in = AzureOpenAiTokenUsageParsingTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
