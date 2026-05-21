package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeTokenUsageParsingTest {

  private final ClaudeLlmClient client =
      new ClaudeLlmClient(new ObjectMapper(), FixedClaudeConfiguration.defaults());

  @Test
  void tokenUsagePresentFromFixture() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("messages-with-usage.json"));
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(11);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(7);
    assertThat(response.tokenUsage().cachedInputTokens()).isNull();
    assertThat(response.tokenUsage().cacheWriteTokens()).isNull();
    assertThat(response.modelUsed()).isEqualTo("claude-3-5-sonnet-20241022");
  }

  @Test
  void tokenUsageAbsentWhenUsageBlockMissing() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("messages-no-usage.json"));
    assertThat(response.tokenUsage()).isNull();
    assertThat(response.modelUsed()).isNull();
  }

  @Test
  void cacheAwareUsageFieldsParsed() throws Exception {
    LlmExecutionResponse response =
        client.validateAndExtractResponse(fixture("messages-with-cache-usage.json"));
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(100);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(20);
    assertThat(response.tokenUsage().cachedInputTokens()).isEqualTo(40);
    assertThat(response.tokenUsage().cacheWriteTokens()).isEqualTo(15);
  }

  private static String fixture(String name) throws IOException {
    String path = "/fixtures/" + name;
    try (InputStream in = ClaudeTokenUsageParsingTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
