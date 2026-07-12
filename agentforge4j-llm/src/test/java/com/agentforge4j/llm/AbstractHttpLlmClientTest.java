// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import java.io.IOException;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
