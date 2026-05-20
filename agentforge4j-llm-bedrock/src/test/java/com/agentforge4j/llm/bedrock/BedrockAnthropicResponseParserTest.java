package com.agentforge4j.llm.bedrock;

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
    assertThat(parser.extractAssistantText(json, mapper)).isEqualTo("hello");
  }

  @Test
  void stripsMarkdownFence() throws IOException {
    String json = """
        {"content":[{"type":"text","text":"```json\\n{\\"a\\":1}\\n```"}]}
        """;
    assertThat(parser.extractAssistantText(json, mapper)).isEqualTo("{\"a\":1}");
  }

  @Test
  void rejectsMissingContent() {
    assertThatThrownBy(() -> parser.extractAssistantText("{}", mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("content");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> parser.extractAssistantText("{", mapper))
        .isInstanceOf(IOException.class);
  }

  @Test
  void rejectsEmptyTextBlocks() {
    String json = """
        {"content":[{"type":"text","text":"  "},{"type":"tool_use","name":"x","id":"1","input":{}}]}
        """;
    assertThatThrownBy(() -> parser.extractAssistantText(json, mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("no text content");
  }

  @Test
  void rejectsBlankJson() {
    assertThatThrownBy(() -> parser.extractAssistantText("   ", mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejectsJsonNullRoot() {
    assertThatThrownBy(() -> parser.extractAssistantText("null", mapper))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("null");
  }

  @Test
  void rejectsContentThatIsNotAnArray() {
    assertThatThrownBy(() -> parser.extractAssistantText("{\"content\":\"x\"}", mapper))
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
    assertThat(parser.extractAssistantText(json, mapper)).isEqualTo("from tool");
  }

  @Test
  void returnsFirstNonEmptyTextWhenMultipleTextBlocksExist() throws IOException {
    String json = """
        {"content":[
          {"type":"text","text":"first"},
          {"type":"text","text":"second"}
        ]}
        """;
    assertThat(parser.extractAssistantText(json, mapper)).isEqualTo("first");
  }

  @Test
  void rejectsNullObjectMapper() {
    assertThatThrownBy(
        () -> parser.extractAssistantText("{\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}",
            null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ObjectMapper");
  }
}
