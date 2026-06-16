// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import java.io.IOException;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractHttpLlmClientTest {

  static class TestAbstractHttpLlmClient extends AbstractHttpLlmClient {

    public TestAbstractHttpLlmClient(LlmClientConfiguration config) {
      super(config);
    }

    @Override
    protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
      return HttpRequest.newBuilder()
          .uri(java.net.URI.create("http://localhost:8000"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build();
    }

    @Override
    protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
      return new LlmExecutionResponse(json, null, null);
    }
  }

  @Nested
  class ConstructorTests {

    @Test
    void should_construct_with_valid_config() {
      LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");

      TestAbstractHttpLlmClient client = new TestAbstractHttpLlmClient(config);

      assertEquals("openai", client.getProviderName());
      assertEquals("gpt-4", client.getDefaultModel());
    }

    @Test
    void should_throw_on_null_config() {
      assertThrows(IllegalArgumentException.class, () -> {
        new TestAbstractHttpLlmClient(null);
      });
    }

    @Test
    void should_throw_on_blank_provider_name() {
      LlmClientConfiguration config = TestFixtures.testConfig("", "gpt-4");

      assertThrows(IllegalArgumentException.class, () -> {
        new TestAbstractHttpLlmClient(config);
      });
    }

    @Test
    void should_throw_on_blank_default_model() {
      LlmClientConfiguration config = TestFixtures.testConfig("openai", "  ");

      assertThrows(IllegalArgumentException.class, () -> {
        new TestAbstractHttpLlmClient(config);
      });
    }

    @Test
    void should_throw_on_whitespace_provider_name() {
      LlmClientConfiguration config = TestFixtures.testConfig("   ", "gpt-4");

      assertThrows(IllegalArgumentException.class, () -> {
        new TestAbstractHttpLlmClient(config);
      });
    }

    @Test
    void should_preserve_provider_name_and_default_model() {
      LlmClientConfiguration config = TestFixtures.testConfig("ollama", "neural-chat");

      TestAbstractHttpLlmClient client = new TestAbstractHttpLlmClient(config);

      assertEquals("ollama", client.getProviderName());
      assertEquals("neural-chat", client.getDefaultModel());
    }
  }

  @Nested
  class StripCodeFenceTests {

    @Test
    void should_strip_markdown_code_fence() {
      String input = "```json\n{\"key\": \"value\"}\n```";

      String result = LlmClient.stripCodeFence(input);

      assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void should_strip_code_fence_with_language_spec() {
      String input = "```python\nprint('hello')\n```";

      String result = LlmClient.stripCodeFence(input);

      assertEquals("print('hello')", result);
    }

    @Test
    void should_return_input_unchanged_when_no_fence() {
      String input = "{\"key\": \"value\"}";

      String result = LlmClient.stripCodeFence(input);

      assertEquals(input, result);
    }

    @Test
    void should_return_input_unchanged_when_null() {
      String result = LlmClient.stripCodeFence(null);

      assertNull(result);
    }

    @Test
    void should_handle_fence_without_closing_marker() {
      String input = "```json\n{\"key\": \"value\"}";

      String result = LlmClient.stripCodeFence(input);

      assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void should_handle_fence_without_newline_after_opening() {
      String input = "```\n```";

      String result = LlmClient.stripCodeFence(input);

      assertEquals("", result);
    }

    @Test
    void should_strip_leading_trailing_whitespace_from_content() {
      String input = "```\n  content with spaces  \n```";

      String result = LlmClient.stripCodeFence(input);

      assertEquals("content with spaces", result);
    }

    @Test
    void should_handle_multiline_content_with_code_fence() {
      String input = "```json\nline1\nline2\nline3\n```";

      String result = LlmClient.stripCodeFence(input);

      assertTrue(result.contains("line1"));
      assertTrue(result.contains("line2"));
      assertTrue(result.contains("line3"));
    }

    @Test
    void should_only_strip_outermost_fence() {
      String input = "```\nouter ``` inner\n```";

      String result = LlmClient.stripCodeFence(input);

      assertTrue(result.contains("outer"));
      assertTrue(result.contains("inner"));
    }

    @Test
    void should_return_empty_string_unchanged_when_not_a_fence() {
      assertEquals("", LlmClient.stripCodeFence(""));
    }

    @Test
    void should_return_opening_fence_unchanged_when_no_newline_follows_opening_ticks() {
      assertEquals("```json", LlmClient.stripCodeFence("```json"));
    }
  }

  @Nested
  class ExecuteTests {

    private TestAbstractHttpLlmClient client;

    @BeforeEach
    void setup() {
      LlmClientConfiguration config = TestFixtures.testConfig("openai", "gpt-4");
      client = new TestAbstractHttpLlmClient(config);
    }

    @Test
    void should_throw_on_null_request() {
      assertThrows(IllegalArgumentException.class, () -> {
        client.execute(null);
      });
    }

    @Test
    void should_throw_on_mismatched_provider() {
      LlmExecutionRequest request = new LlmExecutionRequest("claude", "gpt-4", "prompt", "input", null, null, null);

      assertThrows(IllegalArgumentException.class, () -> {
        client.execute(request);
      });
    }

    @Test
    void should_throw_on_blank_user_input() {
      assertThrows(IllegalArgumentException.class, () -> {
        client.execute(new LlmExecutionRequest("openai", null, "system", "  ", null, null, null));
      });
    }

    @Test
    void should_throw_on_blank_system_prompt() {
      assertThrows(IllegalArgumentException.class, () -> {
        client.execute(new LlmExecutionRequest("openai", null, "\t", "user", null, null, null));
      });
    }
  }
}
