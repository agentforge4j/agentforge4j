// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for {@code integration.schema.json}: it is a valid Draft 2020-12 document, accepts a well-formed integration
 * definition (MCP and HTTP tiers), and rejects malformed ones. An integration declares no capability envelope — the
 * realised tools reported by the provider are the only capability source — so a {@code capabilities} property is
 * rejected. Mirrors {@link SchemaContractTest} / {@link WorkflowExecutableSchemaContractTest}.
 */
class IntegrationSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
  private static final Path SCHEMA_PATH =
      Path.of("src/main/resources/schema/integration.schema.json").toAbsolutePath().normalize();

  @Test
  void integrationSchema_isValidDraft202012Document() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema metaSchema = SCHEMA_REGISTRY.getSchema(
        SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
    assertThat(metaSchema.validate(schemaNode)).isEmpty();
  }

  @Test
  void integrationSchema_acceptsValidMcpDefinitionWithoutCapabilities() throws Exception {
    List<Error> violations = validate("""
        {
          "id": "github",
          "displayName": "GitHub",
          "type": "MCP_STDIO",
          "active": true,
          "config": { "name": "github", "providerId": "mcp:github", "transport": "STDIO" }
        }
        """);
    assertThat(violations).isEmpty();
  }

  @Test
  void integrationSchema_acceptsValidHttpDefinitionWithoutIdOrRemoteToolName() throws Exception {
    List<Error> violations = validate("""
        {
          "displayName": "Airtable",
          "type": "HTTP_TOOL",
          "config": [
            {
              "capability": "airtable.list_records",
              "mutating": false,
              "method": "GET",
              "urlTemplate": "https://api.airtable.com/v0/{baseId}/{table}",
              "inputSchema": {
                "type": "object",
                "additionalProperties": false,
                "required": ["baseId", "table"],
                "properties": {
                  "baseId": { "type": "string" },
                  "table": { "type": "string" }
                }
              },
              "bodyMode": "NONE",
              "secretHeaders": { "Authorization": "AIRTABLE_TOKEN" },
              "timeout": null,
              "maxRetries": null,
              "maxResponseBytes": null
            }
          ]
        }
        """);
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("integrationSchema_isNotEmpty")
  void integrationSchema_acceptsValidMcpDefinitionWithCapabilities(String value) throws Exception {
    List<Error> violations = validate(value);
    assertThat(violations).isNotEmpty();
  }

  @Test
  void classpathSchemaProvider_exposesIntegrationSchema() {
    assertThat(new ClasspathSchemaProvider().integrationSchema())
        .contains("\"title\": \"IntegrationDefinition\"")
        .contains("\"$schema\": \"https://json-schema.org/draft/2020-12/schema\"");
  }

  private static List<Error> validate(String instanceJson) throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    Schema schema = SCHEMA_REGISTRY.getSchema(
        SchemaLocation.of(SCHEMA_PATH.toUri().toString()), schemaNode);
    return schema.validate(MAPPER.readTree(instanceJson));
  }

  static List<String> integrationSchema_isNotEmpty() {
    return List.of(
        // mutating, urlTemplate, inputSchema, and bodyMode are required on each HTTP endpoint.
        """
            {
              "displayName": "Airtable",
              "type": "HTTP_TOOL",
              "config": [ { "capability": "airtable.list_records", "method": "GET" } ]
            }
            """,
        // additionalProperties:false on the endpoint, plus the method enum (no TRACE).
        """
            {
              "displayName": "Airtable",
              "type": "HTTP_TOOL",
              "config": [
                {
                  "capability": "airtable.list_records",
                  "mutating": false,
                  "method": "TRACE",
                  "urlTemplate": "https://api.airtable.com/v0/x",
                  "inputSchema": { "type": "object" },
                  "bodyMode": "NONE",
                  "surpriseField": true
                }
              ]
            }
            """,
        // `pattern` on config[].capability — must reject a non-snake-case id.
        """
            {
              "displayName": "X",
              "type": "HTTP_TOOL",
              "config": [
                {
                  "capability": "Github.CreatePR",
                  "mutating": true,
                  "method": "POST",
                  "urlTemplate": "https://example.com/x",
                  "inputSchema": { "type": "object" },
                  "bodyMode": "NONE"
                }
              ]
            }
            """,
        """
            {
              "displayName": "X",
              "type": "SMOKE_SIGNAL",
              "config": {}
            }
            """,
        // displayName is required at the envelope level.
        """
            {
              "type": "HTTP_TOOL",
              "config": []
            }
            """,
        """
            {
              "displayName": "X",
              "type": "HTTP_TOOL",
              "config": [],
              "surpriseField": true
            }
            """,
        // The declared capability envelope was removed; realised tools are the only source, so a
        // `capabilities` property is now an unknown top-level property (additionalProperties:false).
        """
            {
              "displayName": "X",
              "type": "MCP_STDIO",
              "config": { "command": "npx" },
              "capabilities": [ { "capability": "x.do_thing" } ]
            }
            """
    );
  }
}
