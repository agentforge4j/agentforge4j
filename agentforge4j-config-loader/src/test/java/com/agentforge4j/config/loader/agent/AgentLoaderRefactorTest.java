package com.agentforge4j.config.loader.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.config.loader.prompt.FileSystemAgentPromptResolver;
import com.agentforge4j.config.loader.prompt.PromptLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentLoaderRefactorTest {

  @TempDir
  Path tempDir;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void filesystemGlobalAgentsLoad() throws IOException {
    Path agentsRoot = tempDir.resolve("agents");
    createAgentBundle(agentsRoot, "alpha.agent", "alpha", null, true);

    FileSystemAgentLoader loader = fileSystemLoader(agentsRoot);
    assertThat(loader.loadAgents()).containsKey("alpha");
  }

  @Test
  void filesystemIdMismatchFails() throws IOException {
    Path agentsRoot = tempDir.resolve("agents");
    createAgentBundle(agentsRoot, "alpha.agent", "different-id", null, true);

    FileSystemAgentLoader loader = fileSystemLoader(agentsRoot);
    assertThatThrownBy(loader::loadAgents)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match bundle id");
  }

  @Test
  void filesystemBundledAgentsAllowMismatch() throws IOException {
    Path agentsRoot = tempDir.resolve("workflow-agents");
    createAgentBundle(agentsRoot, "different-id.agent", "different-id", null, true);

    FileSystemAgentLoader loader = new FileSystemAgentLoader(objectMapper,
        new FileSystemAgentPromptResolver(new PromptLoader()), agentsRoot);
    assertThat(loader.loadAgents())
        .containsKey("different-id");
  }

  @Test
  void classpathShippedAgentsLoad() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        ClasspathAgentLoader.SHIPPED_AGENTS_ROOT);
    assertThat(loader.loadAgents()).isNotNull();
  }

  @Test
  void classpathBundledAgentsLoad() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        "/test-bundles/workflow-a.workflow/agents");
    List<String> entries = List.of(
        "agents/test-bundle.agent");
    assertThat(loader.loadAgents(entries)).containsKey("test-bundle");
  }

  @Test
  void duplicateIdsFail() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        "/test-bundles/workflow-a.workflow/agents");
    List<String> entries = List.of(
        "agents/test-bundle.agent",
        "agents/test-bundle.agent");
    assertThatThrownBy(() -> loader.loadAgents(entries))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate agent id");
  }

  @Test
  void missingIdFails() throws IOException {
    Path agentsRoot = tempDir.resolve("agents");
    createAgentBundle(agentsRoot, "missing-id.agent", null, null, true);
    assertThatThrownBy(() -> fileSystemLoader(agentsRoot).loadAgents())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agent id is required");
  }

  @Test
  void missingNameFails() throws IOException {
    Path agentsRoot = tempDir.resolve("agents");
    createAgentBundle(agentsRoot, "missing-name.agent", "missing-name", "name", true);
    assertThatThrownBy(() -> fileSystemLoader(agentsRoot).loadAgents())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agent name is required");
  }

  @Test
  void missingLocalityFails() throws IOException {
    Path agentsRoot = tempDir.resolve("agents");
    createAgentBundle(agentsRoot, "missing-locality.agent", "missing-locality", "locality", true);
    assertThatThrownBy(() -> fileSystemLoader(agentsRoot).loadAgents())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Agent locality is required");
  }

  @Test
  void missingProviderPreferencesFails() throws IOException {
    Path agentsRoot = tempDir.resolve("agents");
    createAgentBundle(agentsRoot, "missing-providers.agent", "missing-providers", null, false);
    assertThatThrownBy(() -> fileSystemLoader(agentsRoot).loadAgents())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one providerPreference");
  }

  @Test
  void inlineSystemPromptWorks() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper,
        "/test-bundles/workflow-c.workflow/agents/");
    List<String> entries = List.of(
        "inline-prompt.agent");
    assertThat(loader.loadAgents(entries).get("inline-prompt").systemPrompt())
        .contains("Inline system prompt");
  }

  @Test
  void systemPromptFallbackWorks() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper, "/test-bundles/workflow-a.workflow/agents/");
    List<String> entries = List.of(
        "test-bundle.agent");
    assertThat(loader.loadAgents(entries).get("test-bundle").systemPrompt())
        .contains("Test bundle system prompt");
  }

  @Test
  void boundariesAreAppended() {
    ClasspathAgentLoader loader = new ClasspathAgentLoader(objectMapper, "/test-bundles/workflow-a.workflow/agents/");
    List<String> entries = List.of(
        "test-bundle.agent");
    String prompt = loader.loadAgents(entries).get("test-bundle")
        .systemPrompt();
    assertThat(prompt).contains("Test bundle system prompt");
    assertThat(prompt).contains("Test boundaries");
  }

  private FileSystemAgentLoader fileSystemLoader(Path agentRoot) {
    return new FileSystemAgentLoader(objectMapper,
        new FileSystemAgentPromptResolver(new PromptLoader()), agentRoot);
  }

  private void createAgentBundle(Path root, String dirName, String id, String blankField,
      boolean withProviders) throws IOException {
    Path bundle = root.resolve(dirName);
    Files.createDirectories(bundle);
    String name = "Local Agent";
    String locality = "\"CLOUD\"";
    String providerSection = """
          "providerPreferences": [
            { "provider": "openai", "model": "gpt-4o-mini" }
          ],
        """;
    if ("name".equals(blankField)) {
      name = " ";
    }
    if ("locality".equals(blankField)) {
      locality = "null";
    }
    if (!withProviders) {
      providerSection = """
            "providerPreferences": [],
          """;
    }
    String idValue = id == null ? "null" : "\"%s\"".formatted(id);
    Files.writeString(bundle.resolve("agent.json"), """
        {
          "id": %s,
          "name": "%s",
          "locality": %s,
          "enabled": true,
          "version": "1.0.0",
        %s
          "supportedCommands": ["COMPLETE"]
        }
        """.formatted(idValue, name, locality, providerSection));
    Files.writeString(bundle.resolve("systemprompt.md"), "filesystem prompt");
  }
}
