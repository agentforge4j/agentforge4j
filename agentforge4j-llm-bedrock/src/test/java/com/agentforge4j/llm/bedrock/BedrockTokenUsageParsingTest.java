package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BedrockTokenUsageParsingTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final BedrockAnthropicResponseParser parser = new BedrockAnthropicResponseParser();

  @Test
  void tokenUsagePresentFromFixture() throws Exception {
    LlmExecutionResponse response = parser.parse(fixture("invoke-with-usage.json"), mapper);
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(14);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(6);
    assertThat(response.tokenUsage().cachedInputTokens()).isEqualTo(4);
    assertThat(response.tokenUsage().cacheWriteTokens()).isEqualTo(2);
  }

  @Test
  void tokenUsageAbsentWhenUsageBlockMissing() throws Exception {
    LlmExecutionResponse response = parser.parse(fixture("invoke-no-usage.json"), mapper);
    assertThat(response.tokenUsage()).isNull();
  }

  private static String fixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = BedrockTokenUsageParsingTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
