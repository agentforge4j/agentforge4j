package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaLlmClientTest {

  record TestOllamaConfiguration(String getUrl, Duration getRequestTimeout,
                                 String getDefaultModel) implements OllamaConfiguration {

    TestOllamaConfiguration() {
      this("http://localhost:11434/api/chat", Duration.ofSeconds(30), "llama2");
    }

    @Override
    public Duration getConnectTimeout() {
      return Duration.ofSeconds(10);
    }
  }

  @Nested
  class ConstructorTests {

    @Test
    void shouldConstructWithValidConfiguration() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();

      OllamaLlmClient client = new OllamaLlmClient(mapper, config);

      assertThat(client.getProviderName()).isEqualTo("ollama");
      assertThat(client.getDefaultModel()).isEqualTo("llama2");
    }

    @Test
    void shouldThrowWhenObjectMapperNull() {
      OllamaConfiguration config = new TestOllamaConfiguration();

      assertThatThrownBy(() -> new OllamaLlmClient(null, config))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfigurationNull() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new OllamaLlmClient(mapper, null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenUrlBlank() {
      OllamaConfiguration config = new TestOllamaConfiguration("", Duration.ofSeconds(30),
          "llama2");
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new OllamaLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Ollama URL");
    }

    @Test
    void shouldThrowWhenDefaultModelBlank() {
      OllamaConfiguration config = new TestOllamaConfiguration("http://localhost:11434/api/chat",
          Duration.ofSeconds(30), "");
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new OllamaLlmClient(mapper, config))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ValidateAndExtractResponseTests {

    @Test
    void shouldThrowWhenJsonBlank() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);

      assertThatThrownBy(() -> client.validateAndExtractResponse(""))
          .isInstanceOf(LlmInvocationException.class);
    }

    @Test
    void shouldThrowWhenResponseHasError() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);
      String errorResponse = "{\"error\": \"Model not found\", \"message\": null}";

      assertThatThrownBy(() -> client.validateAndExtractResponse(errorResponse))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("Ollama error");
    }

    @Test
    void shouldThrowWhenMessageMissing() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);
      String invalidResponse = "{\"error\": null, \"message\": null}";

      assertThatThrownBy(() -> client.validateAndExtractResponse(invalidResponse))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("missing message");
    }

    @Test
    void shouldThrowWhenMessageContentEmpty() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);
      String emptyResponse = "{\"error\": null, \"message\": {\"role\": \"assistant\", \"content\": \"\"}}";

      assertThatThrownBy(() -> client.validateAndExtractResponse(emptyResponse))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("empty message.content");
    }

    @Test
    void shouldExtractResponseContent() throws IOException {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);
      String validResponse = "{\"error\": null, \"message\": {\"role\": \"assistant\", \"content\": \"Hello, World!\"}}";

      String result = client.validateAndExtractResponse(validResponse).text();

      assertThat(result).isEqualTo("Hello, World!");
    }
  }

  @Nested
  class BuildHttpRequestTests {

    @Test
    void shouldBuildValidHttpRequest() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);
      LlmExecutionRequest request = new LlmExecutionRequest("ollama", null, "system prompt",
          "user input", null, null, null);

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.uri().toString()).isEqualTo("http://localhost:11434/api/chat");
      assertThat(httpRequest.method()).isEqualTo("POST");
    }

    @Test
    void shouldIncludeContentTypeHeader() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);
      LlmExecutionRequest request = new LlmExecutionRequest("ollama", null, "system prompt",
          "user input", null, null, null);

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.headers().firstValue("Content-Type"))
          .contains("application/json");
    }

    @Test
    void shouldApplyRequestTimeoutToBuiltRequest() {
      Duration shortTimeout = Duration.ofMillis(250);
      OllamaConfiguration config =
          new TestOllamaConfiguration("http://localhost:11434/api/chat", shortTimeout, "llama2");
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);
      LlmExecutionRequest request = new LlmExecutionRequest("ollama", null, "system prompt",
          "user input", null, null, null);

      HttpRequest httpRequest = client.buildHttpRequest(request);

      assertThat(httpRequest.timeout()).hasValue(shortTimeout);
    }
  }

  @Nested
  class ExecuteValidationTests {

    @Test
    void shouldThrowWhenExecuteRequestIsNull() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);

      assertThatThrownBy(() -> client.execute(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Request must not be null");
    }

    @Test
    void shouldThrowWhenExecuteRequestProviderDoesNotMatchClient() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);

      assertThatThrownBy(
          () -> client.execute(
              new LlmExecutionRequest("openai", null, "system prompt", "user input", null, null, null)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("does not match");
    }
  }

  @Nested
  class PromptCacheConformanceTests {

    @Test
    void shouldProduceIdenticalRequestBodyRegardlessOfBoundaries() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, new TestOllamaConfiguration());
      LlmExecutionRequest withoutBoundaries =
          new LlmExecutionRequest("ollama", "llama2", "system", "user", null, null, null);
      PromptLayerBoundaries boundaries = new PromptLayerBoundaries(50, 100, null);
      LlmExecutionRequest withBoundaries = new LlmExecutionRequest(
          "ollama", "llama2", "system", "user", null, boundaries, null);

      String withoutBody = collectUtf8RequestBody(client.buildHttpRequest(withoutBoundaries));
      String withBody = collectUtf8RequestBody(client.buildHttpRequest(withBoundaries));

      assertThat(withBody).isEqualTo(withoutBody);
    }

    @Test
    void shouldOmitExplicitCacheMarkersWhenBoundariesPresent() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, new TestOllamaConfiguration());
      PromptLayerBoundaries boundaries = new PromptLayerBoundaries(100, 200, null);
      LlmExecutionRequest request = new LlmExecutionRequest(
          "ollama", "llama2", "system", "user", null, boundaries, null);

      String body = collectUtf8RequestBody(client.buildHttpRequest(request));

      assertThat(body).doesNotContain("cache_control");
      assertThat(body).doesNotContain("cachePoint");
    }
  }

  @Nested
  class ValidateAndExtractResponseMalformedJsonTests {

    @Test
    void shouldPropagateJacksonFailureAsIOException() {
      OllamaConfiguration config = new TestOllamaConfiguration();
      ObjectMapper mapper = new ObjectMapper();
      OllamaLlmClient client = new OllamaLlmClient(mapper, config);

      assertThatThrownBy(() -> client.validateAndExtractResponse("{ not valid json"))
          .isInstanceOf(java.io.IOException.class);
    }
  }

  private static String collectUtf8RequestBody(HttpRequest request) throws Exception {
    assertThat(request.bodyPublisher()).isPresent();
    HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
    var out = new ByteArrayOutputStream();
    var latch = new CountDownLatch(1);
    publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
      @Override
      public void onSubscribe(Flow.Subscription s) {
        s.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(ByteBuffer b) {
        byte[] chunk = new byte[b.remaining()];
        b.get(chunk);
        out.writeBytes(chunk);
      }

      @Override
      public void onError(Throwable t) {
        latch.countDown();
      }

      @Override
      public void onComplete() {
        latch.countDown();
      }
    });
    assertThat(latch.await(5, TimeUnit.SECONDS)).as("body publisher should complete").isTrue();
    return out.toString(StandardCharsets.UTF_8);
  }
}

