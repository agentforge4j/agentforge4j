package com.agentforge4j.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultLlmClientResolverTest {

  @Nested
  class ConstructorTests {

    @Test
    void should_construct_with_single_client() {
      TestFixtures.TestLlmClient client = new TestFixtures.TestLlmClient("openai");
      Collection<LlmClient> clients = List.of((LlmClient) client);

      DefaultLlmClientResolver resolver = new DefaultLlmClientResolver(clients);

      assertNotNull(resolver);
    }

    @Test
    void should_construct_with_multiple_clients() {
      Collection<LlmClient> clients = List.of(
          new TestFixtures.TestLlmClient("openai"),
          new TestFixtures.TestLlmClient("claude"),
          new TestFixtures.TestLlmClient("ollama")
      );

      DefaultLlmClientResolver resolver = new DefaultLlmClientResolver(clients);

      assertNotNull(resolver);
    }

    @Test
    void should_allow_empty_collection_construction() {
      Collection<LlmClient> clients = new ArrayList<>();

      DefaultLlmClientResolver resolver = new DefaultLlmClientResolver(clients);

      assertNotNull(resolver);
    }

    @Test
    void should_throw_on_null_client_in_collection() {
      List<LlmClient> clientList = new ArrayList<>();
      clientList.add(new TestFixtures.TestLlmClient("openai"));
      clientList.add(null);
      Collection<LlmClient> clients = clientList;

      assertThrows(IllegalArgumentException.class, () -> {
        new DefaultLlmClientResolver(clients);
      });
    }

    @Test
    void should_throw_on_client_with_blank_provider_name() {
      LlmClient badClient = new LlmClient() {
        @Override
        public String getProviderName() {
          return "";
        }

        @Override
        public String execute(LlmExecutionRequest request) {
          return "";
        }
      };
      Collection<LlmClient> clients = List.of(badClient);

      assertThrows(IllegalArgumentException.class, () -> {
        new DefaultLlmClientResolver(clients);
      });
    }

    @Test
    void should_throw_on_duplicate_provider_names() {
      Collection<LlmClient> clients = List.of(
          new TestFixtures.TestLlmClient("openai"),
          new TestFixtures.TestLlmClient("openai")
      );

      assertThrows(IllegalArgumentException.class, () -> {
        new DefaultLlmClientResolver(clients);
      });
    }

    @Test
    void should_normalize_provider_names_for_duplicates() {
      Collection<LlmClient> clients = List.of(
          new TestFixtures.TestLlmClient("OPENAI"),
          new TestFixtures.TestLlmClient("openai")
      );

      assertThrows(IllegalArgumentException.class, () -> {
        new DefaultLlmClientResolver(clients);
      });
    }

    @Test
    void should_preserve_only_provided_clients() {
      LlmClient client1 = new TestFixtures.TestLlmClient("openai");
      LlmClient client2 = new TestFixtures.TestLlmClient("claude");
      Collection<LlmClient> clients = List.of(client1, client2);

      DefaultLlmClientResolver resolver = new DefaultLlmClientResolver(clients);

      assertEquals(client1, resolver.resolve("openai"));
      assertEquals(client2, resolver.resolve("claude"));
    }
  }

  @Nested
  class ResolveTests {

    private DefaultLlmClientResolver resolver;

    void setup(Collection<LlmClient> clients) {
      resolver = new DefaultLlmClientResolver(clients);
    }

    @Test
    void should_resolve_client_by_exact_provider_name() {
      setup(List.of(new TestFixtures.TestLlmClient("openai")));

      LlmClient client = resolver.resolve("openai");

      assertNotNull(client);
      assertEquals("openai", client.getProviderName());
    }

    @Test
    void should_resolve_client_with_uppercase_provider() {
      setup(List.of(new TestFixtures.TestLlmClient("openai")));

      LlmClient client = resolver.resolve("OPENAI");

      assertNotNull(client);
      assertEquals("openai", client.getProviderName());
    }

    @Test
    void should_resolve_client_with_mixed_case_provider() {
      setup(List.of(new TestFixtures.TestLlmClient("openai")));

      LlmClient client = resolver.resolve("OpenAi");

      assertNotNull(client);
      assertEquals("openai", client.getProviderName());
    }

    @Test
    void should_resolve_client_with_whitespace_provider() {
      setup(List.of(new TestFixtures.TestLlmClient("openai")));

      LlmClient client = resolver.resolve("  openai  ");

      assertNotNull(client);
      assertEquals("openai", client.getProviderName());
    }

    @Test
    void should_throw_on_unknown_provider() {
      setup(List.of(new TestFixtures.TestLlmClient("openai")));

      assertThrows(IllegalArgumentException.class, () -> {
        resolver.resolve("unknown");
      });
    }

    @Test
    void should_throw_on_blank_provider() {
      setup(List.of(new TestFixtures.TestLlmClient("openai")));

      assertThrows(IllegalArgumentException.class, () -> {
        resolver.resolve("   ");
      });
    }

    @Test
    void should_throw_on_empty_provider() {
      setup(List.of(new TestFixtures.TestLlmClient("openai")));

      assertThrows(IllegalArgumentException.class, () -> {
        resolver.resolve("");
      });
    }

    @Test
    void should_include_available_providers_in_error_message() {
      setup(List.of(
          new TestFixtures.TestLlmClient("openai"),
          new TestFixtures.TestLlmClient("claude")
      ));

      Exception exception = assertThrows(IllegalArgumentException.class, () -> {
        resolver.resolve("unknown");
      });

      String message = exception.getMessage();
      assertTrue(message.contains("openai"), "Error should mention available providers");
      assertTrue(message.contains("claude"), "Error should mention available providers");
    }

    @Test
    void should_resolve_correct_client_from_multiple() {
      LlmClient openaiClient = new TestFixtures.TestLlmClient("openai");
      LlmClient claudeClient = new TestFixtures.TestLlmClient("claude");
      LlmClient ollamaClient = new TestFixtures.TestLlmClient("ollama");
      setup(List.of(openaiClient, claudeClient, ollamaClient));

      assertEquals(claudeClient, resolver.resolve("claude"));
      assertEquals(ollamaClient, resolver.resolve("ollama"));
      assertEquals(openaiClient, resolver.resolve("openai"));
    }
  }

  @Nested
  class DiscoverTests {

    @Test
    void should_throw_when_no_clients_can_be_created() {
      ObjectMapper mapper = new ObjectMapper();
      Collection<LlmClientConfiguration> configs = new ArrayList<>();

      assertThrows(IllegalStateException.class, () -> {
        DefaultLlmClientResolver.discover(mapper, configs);
      });
    }

    @Test
    void should_throw_with_helpful_error_message_when_no_clients_created() {
      ObjectMapper mapper = new ObjectMapper();
      Collection<LlmClientConfiguration> configs = new ArrayList<>();

      Exception exception = assertThrows(IllegalStateException.class, () -> {
        DefaultLlmClientResolver.discover(mapper, configs);
      });

      assertTrue(exception.getMessage().contains("No LLM clients could be created"));
    }
  }
}



