package com.agentforge4j.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for {@code integration.schema.json}: it is a valid Draft 2020-12 document, accepts a
 * well-formed integration definition (MCP and HTTP tiers), and rejects malformed ones. Mirrors
 * {@link SchemaContractTest} / {@link WorkflowExecutableSchemaContractTest}.
 */
class IntegrationSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonSchemaFactory SCHEMA_FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
  private static final Path SCHEMA_PATH =
      Path.of("src/main/resources/schema/integration.schema.json").toAbsolutePath().normalize();

  @Test
  void integrationSchema_isValidDraft202012Document() throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    JsonSchema metaSchema = SCHEMA_FACTORY.getSchema(
        URI.create("https://json-schema.org/draft/2020-12/schema"));
    assertThat(metaSchema.validate(schemaNode)).isEmpty();
  }

  @Test
  void integrationSchema_acceptsValidMcpDefinition() throws Exception {
    Set<ValidationMessage> violations = validate("""
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
    Set<ValidationMessage> violations = validate("""
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
    Set<ValidationMessage> violations = validate("""
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
    Set<ValidationMessage> violations = validate("""
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
    Set<ValidationMessage> violations = validate("""
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
    Set<ValidationMessage> violations = validate("""
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
    Set<ValidationMessage> violations = validate("""
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
    Set<ValidationMessage> violations = validate("""
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

  private static Set<ValidationMessage> validate(String instanceJson) throws Exception {
    JsonNode schemaNode = MAPPER.readTree(Files.readString(SCHEMA_PATH));
    JsonSchema schema = SCHEMA_FACTORY.getSchema(SCHEMA_PATH.toUri(), schemaNode);
    return schema.validate(MAPPER.readTree(instanceJson));
  }
}
