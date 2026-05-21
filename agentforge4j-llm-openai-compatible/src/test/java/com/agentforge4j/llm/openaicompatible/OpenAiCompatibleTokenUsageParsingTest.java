package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleTokenUsageParsingTest {

  private final OpenAiCompatibleLlmClient client =
      new OpenAiCompatibleLlmClient(new ObjectMapper(),
          FixedOpenAiCompatibleConfiguration.defaults());

  @Test
  void tokenUsagePresentFromFixture() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("responses-with-usage.json"));
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(55);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(22);
    assertThat(response.tokenUsage().cachedInputTokens()).isEqualTo(7);
    assertThat(response.tokenUsage().cacheWriteTokens()).isNull();
    assertThat(response.modelUsed()).isEqualTo("provider-model-1");
  }

  @Test
  void tokenUsageAbsentWhenUsageBlockMissing() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("responses-no-usage.json"));
    assertThat(response.tokenUsage()).isNull();
    assertThat(response.modelUsed()).isNull();
  }

  private static String fixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = OpenAiCompatibleTokenUsageParsingTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
