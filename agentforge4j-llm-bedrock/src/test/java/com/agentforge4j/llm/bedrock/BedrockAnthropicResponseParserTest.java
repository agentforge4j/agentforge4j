package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedrockAnthropicResponseParserTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final BedrockAnthropicResponseParser parser = new BedrockAnthropicResponseParser();

  @Test
  void extractsFirstTextBlock() throws IOException {
    String json = """
        {"id":"msg","type":"message","role":"assistant","content":[{"type":"text","text":"  hello  "}]}
        """;
    assertThat(parser.parse(json, mapper).text()).isEqualTo("hello");
  }

  @Test
  void stripsMarkdownFence() throws IOException {
    String json = """
        {"content":[{"type":"text","text":"```json\\n{\\"a\\":1}\\n```"}]}
        """;
    assertThat(parser.parse(json, mapper).text()).isEqualTo("{\"a\":1}");
  }

  @Test
  void rejectsMissingContent() {
    assertThatThrownBy(() -> parser.parse("{}", mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("content");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> parser.parse("{", mapper))
        .isInstanceOf(IOException.class);
  }

  @Test
  void rejectsEmptyTextBlocks() {
    String json = """
        {"content":[{"type":"text","text":"  "},{"type":"tool_use","name":"x","id":"1","input":{}}]}
        """;
    assertThatThrownBy(() -> parser.parse(json, mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("no text content");
  }

  @Test
  void rejectsBlankJson() {
    assertThatThrownBy(() -> parser.parse("   ", mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejectsJsonNullRoot() {
    assertThatThrownBy(() -> parser.parse("null", mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("null");
  }

  @Test
  void rejectsContentThatIsNotAnArray() {
    assertThatThrownBy(() -> parser.parse("{\"content\":\"x\"}", mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("content");
  }

  @Test
  void skipsNonTextBlocksThenReturnsFirstText() throws IOException {
    String json = """
        {"content":[
          {"type":"tool_use","name":"x","id":"1","input":{}},
          {"type":"TEXT","text":"from tool"}
        ]}
        """;
    assertThat(parser.parse(json, mapper).text()).isEqualTo("from tool");
  }

  @Test
  void returnsFirstNonEmptyTextWhenMultipleTextBlocksExist() throws IOException {
    String json = """
        {"content":[
          {"type":"text","text":"first"},
          {"type":"text","text":"second"}
        ]}
        """;
    assertThat(parser.parse(json, mapper).text()).isEqualTo("first");
  }

  @Test
  void tokenUsageAbsentWhenUsageBlockMissing() throws IOException {
    String json = """
        {"content":[{"type":"text","text":"ok"}]}
        """;
    LlmExecutionResponse response = parser.parse(json, mapper);
    assertThat(response.tokenUsage()).isNull();
  }

  @Test
  void tokenUsagePresentWithCacheFields() throws IOException {
    String json = """
        {"content":[{"type":"text","text":"ok"}],
         "usage":{"input_tokens":10,"output_tokens":5,
                  "cache_read_input_tokens":3,"cache_creation_input_tokens":2}}
        """;
    LlmExecutionResponse response = parser.parse(json, mapper);
    assertThat(response.tokenUsage()).isNotNull();
    assertThat(response.tokenUsage().inputTokens()).isEqualTo(10);
    assertThat(response.tokenUsage().outputTokens()).isEqualTo(5);
    assertThat(response.tokenUsage().cachedInputTokens()).isEqualTo(3);
    assertThat(response.tokenUsage().cacheWriteTokens()).isEqualTo(2);
  }

  @Test
  void rejectsNullObjectMapper() {
    assertThatThrownBy(
        () -> parser.parse("{\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
            null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ObjectMapper");
  }
}
