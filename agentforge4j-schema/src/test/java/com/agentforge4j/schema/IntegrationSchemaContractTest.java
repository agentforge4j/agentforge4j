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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for {@code integration.schema.json}: it is a valid Draft 2020-12 document, accepts a
 * well-formed integration definition (MCP and HTTP tiers), and rejects malformed ones. Mirrors
 * {@link SchemaContractTest} / {@link WorkflowExecutableSchemaContractTest}.
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
  void integrationSchema_acceptsValidMcpDefinition() throws Exception {
    List<Error> violations = validate("""
        {
          "id": "github",
          "displayName": "GitHub",
          "type": "MCP_STDIO",
          "active": true,
          "config": { "name": "github", "providerId": "mcp:github", "transport": "STDIO" },
          "capabilities": [
            { "capability": "github.create_issue", "remoteToolName": "create_issue", "mutating": true }
          ]
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
          "config": [ { "capability": "airtable.list_records", "method": "GET" } ],
          "capabilities": [ { "capability": "airtable.list_records", "mutating": false } ]
        }
        """);
    assertThat(violations).isEmpty();
  }

  @Test
  void integrationSchema_rejectsUnknownType() throws Exception {
    List<Error> violations = validate("""
        {
          "displayName": "X",
          "type": "SMOKE_SIGNAL",
          "config": {},
          "capabilities": [ { "capability": "x.do_thing" } ]
        }
        """);
    assertThat(violations).isNotEmpty();
  }

  @Test
  void integrationSchema_enforcesCapabilityPattern() throws Exception {
    // `pattern` on capabilities[].capability — must reject a non-snake-case id.
    List<Error> violations = validate("""
        {
          "displayName": "X",
          "type": "HTTP_TOOL",
          "config": [],
          "capabilities": [ { "capability": "Github.CreatePR" } ]
        }
        """);
    assertThat(violations).isNotEmpty();
  }

  @Test
  void integrationSchema_enforcesNestedItemsRequired() throws Exception {
    // nested `items` subschema — capability object missing its required `capability` field.
    List<Error> violations = validate("""
        {
          "displayName": "X",
          "type": "HTTP_TOOL",
          "config": [],
          "capabilities": [ { "mutating": true } ]
        }
        """);
    assertThat(violations).isNotEmpty();
  }

  @Test
  void integrationSchema_rejectsMissingRequiredField() throws Exception {
    // displayName is required at the envelope level.
    List<Error> violations = validate("""
        {
          "type": "HTTP_TOOL",
          "config": [],
          "capabilities": [ { "capability": "x.do_thing" } ]
        }
        """);
    assertThat(violations).isNotEmpty();
  }

  @Test
  void integrationSchema_rejectsUnknownTopLevelProperty() throws Exception {
    List<Error> violations = validate("""
        {
          "displayName": "X",
          "type": "HTTP_TOOL",
          "config": [],
          "capabilities": [ { "capability": "x.do_thing" } ],
          "surpriseField": true
        }
        """);
    assertThat(violations).isNotEmpty();
  }

  @Test
  void integrationSchema_rejectsEmptyCapabilities() throws Exception {
    List<Error> violations = validate("""
        {
          "displayName": "X",
          "type": "HTTP_TOOL",
          "config": [],
          "capabilities": []
        }
        """);
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
}
