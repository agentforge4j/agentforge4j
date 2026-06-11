package com.agentforge4j.config.loader.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.integration.IntegrationCapability;
import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.agentforge4j.schema.SchemaProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemIntegrationConfigLoaderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaProvider SCHEMA_PROVIDER = new ClasspathSchemaProvider();

  @TempDir
  Path tempDir;

  @Test
  void load_mapsSingleValidFileAndRetainsConfigVerbatim() throws IOException {
    write("github.json", """
        {
          "id": "github",
          "displayName": "GitHub",
          "type": "MCP_STDIO",
          "active": true,
          "config": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-github"] },
          "capabilities": [
            { "capability": "github.create_issue", "remoteToolName": "create_issue", "mutating": true }
          ]
        }
        """);

    List<IntegrationDefinition> loaded = loader().load();

    assertThat(loaded).hasSize(1);
    IntegrationDefinition definition = loaded.get(0);
    assertThat(definition.id()).isEqualTo("github");
    assertThat(definition.displayName()).isEqualTo("GitHub");
    assertThat(definition.type()).isEqualTo(IntegrationType.MCP_STDIO);
    assertThat(definition.active()).isTrue();
    assertThat(MAPPER.readTree(definition.config()))
        .isEqualTo(MAPPER.readTree(
            "{ \"command\": \"npx\", \"args\": [\"-y\", \"@modelcontextprotocol/server-github\"] }"));
    assertThat(definition.capabilities()).containsExactly(
        new IntegrationCapability("github.create_issue", "create_issue", true));
  }

  @Test
  void load_fallsBackToFilenameStemWhenIdAbsent() throws IOException {
    write("airtable.json", """
        {
          "displayName": "Airtable",
          "type": "HTTP_TOOL",
          "config": [ { "capability": "airtable.list_records", "method": "GET" } ],
          "capabilities": [ { "capability": "airtable.list_records" } ]
        }
        """);

    List<IntegrationDefinition> loaded = loader().load();

    assertThat(loaded).hasSize(1);
    assertThat(loaded.get(0).id()).isEqualTo("airtable");
    assertThat(loaded.get(0).capabilities())
        .containsExactly(new IntegrationCapability("airtable.list_records", null, false));
  }

  @Test
  void load_loadsInactiveDefinitionWithoutFiltering() throws IOException {
    write("jira.json", """
        {
          "displayName": "Jira",
          "type": "MCP_STREAMABLE_HTTP",
          "active": false,
          "config": { "url": "https://mcp.example.com/jira" },
          "capabilities": [ { "capability": "jira.create_issue" } ]
        }
        """);

    List<IntegrationDefinition> loaded = loader().load();

    assertThat(loaded).hasSize(1);
    assertThat(loaded.get(0).active()).isFalse();
  }

  @Test
  void constructor_failsFastWhenDirectoryIsMissing() {
    Path missing = tempDir.resolve("does-not-exist");

    assertThatThrownBy(
        () -> new FileSystemIntegrationConfigLoader(MAPPER, SCHEMA_PROVIDER, missing))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(missing.toString());
  }

  @Test
  void constructor_failsFastWhenPathIsNotADirectory() throws IOException {
    Path file = Files.writeString(tempDir.resolve("not-a-directory.json"), "{}");

    assertThatThrownBy(
        () -> new FileSystemIntegrationConfigLoader(MAPPER, SCHEMA_PROVIDER, file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(file.toString());
  }

  @Test
  void load_returnsEmptyListForEmptyDirectory() {
    assertThat(loader().load()).isEmpty();
  }

  @Test
  void load_failsFastNamingEachSchemaInvalidFile() throws IOException {
    write("bad-type.json", """
        {
          "displayName": "X",
          "type": "SMOKE_SIGNAL",
          "config": {},
          "capabilities": [ { "capability": "x.do_thing" } ]
        }
        """);
    write("malformed.json", "{ not json");

    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bad-type.json")
        .hasMessageContaining("malformed.json");
  }

  @Test
  void load_failsFastOnDuplicateIdNamingIdAndBothFiles() throws IOException {
    write("first.json", """
        {
          "id": "github",
          "displayName": "GitHub",
          "type": "MCP_STDIO",
          "config": { "command": "npx" },
          "capabilities": [ { "capability": "github.create_issue" } ]
        }
        """);
    write("second.json", """
        {
          "id": "github",
          "displayName": "GitHub Mirror",
          "type": "MCP_STDIO",
          "config": { "command": "npx" },
          "capabilities": [ { "capability": "github.create_pull_request" } ]
        }
        """);

    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("github")
        .hasMessageContaining("first.json")
        .hasMessageContaining("second.json");
  }

  @Test
  void load_ignoresSubdirectoriesAndNonJsonEntries() throws IOException {
    write("github.json", """
        {
          "displayName": "GitHub",
          "type": "MCP_STDIO",
          "config": { "command": "npx" },
          "capabilities": [ { "capability": "github.create_issue" } ]
        }
        """);
    Files.writeString(tempDir.resolve("README.md"), "not an integration");
    Path subdirectory = Files.createDirectory(tempDir.resolve("nested"));
    Files.writeString(subdirectory.resolve("ignored.json"), "{ not even json");

    List<IntegrationDefinition> loaded = loader().load();

    assertThat(loaded).hasSize(1);
    assertThat(loaded.get(0).id()).isEqualTo("github");
  }

  private FileSystemIntegrationConfigLoader loader() {
    return new FileSystemIntegrationConfigLoader(MAPPER, SCHEMA_PROVIDER, tempDir);
  }

  private void write(String fileName, String content) throws IOException {
    Files.writeString(tempDir.resolve(fileName), content);
  }
}
