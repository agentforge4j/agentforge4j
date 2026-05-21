package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BedrockTokenUsageParsingTest {

  private static final String TEST_MODEL_ID = "anthropic.claude-3-5-sonnet-20241022-v2:0";

  private final ObjectMapper mapper = new ObjectMapper();
  private final BedrockAnthropicResponseParser parser = new BedrockAnthropicResponseParser();

  @Test
  void tokenUsagePresentFromFixture() throws Exception {
    LlmExecutionResponse response =
        parser.parse(fixture("invoke-with-usage.json"), mapper, TEST_MODEL_ID);
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(14);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(6);
    assertThat(response.tokenUsage().cachedInputTokens()).isEqualTo(4);
    assertThat(response.tokenUsage().cacheWriteTokens()).isEqualTo(2);
    assertThat(response.modelUsed()).isEqualTo(TEST_MODEL_ID);
  }

  @Test
  void tokenUsageAbsentWhenUsageBlockMissing() throws Exception {
    LlmExecutionResponse response =
        parser.parse(fixture("invoke-no-usage.json"), mapper, TEST_MODEL_ID);
    assertThat(response.tokenUsage()).isNull();
    assertThat(response.modelUsed()).isEqualTo(TEST_MODEL_ID);
  }

  @Test
  void modelUsedPassesThroughRequestModelId() throws Exception {
    String modelId = "anthropic.claude-3-haiku-20240307-v1:0";
    LlmExecutionResponse response =
        parser.parse(fixture("invoke-no-usage.json"), mapper, modelId);
    assertThat(response.modelUsed()).isEqualTo(modelId);
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
