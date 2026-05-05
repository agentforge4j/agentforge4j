package com.agentforge4j.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

class ClasspathSchemaProviderTest {

  @Test
  void exposes_all_expected_schemas_with_distinct_content() {
    SchemaProvider provider = new ClasspathSchemaProvider();

    assertThat(provider.workflowSchema())
        .contains("\"title\": \"WorkflowDefinition\"")
        .contains("\"$schema\": \"https://json-schema.org/draft/2020-12/schema\"");
    assertThat(provider.agentSchema()).contains("\"title\": \"AgentDefinition\"");
    assertThat(provider.blueprintSchema()).contains("\"title\": \"BlueprintDefinition\"");
    assertThat(provider.artifactSchema()).contains("\"title\": \"ArtifactDefinition\"");

    assertThat(provider.workflowSchema()).isNotEqualTo(provider.agentSchema());
    assertThat(provider.blueprintSchema()).isNotEqualTo(provider.artifactSchema());
  }

  @Test
  void schemas_are_non_blank_json_documents() {
    SchemaProvider provider = new ClasspathSchemaProvider();

    assertThat(provider.workflowSchema().trim()).startsWith("{").endsWith("}");
    assertThat(provider.agentSchema().trim()).startsWith("{").endsWith("}");
    assertThat(provider.blueprintSchema().trim()).startsWith("{").endsWith("}");
    assertThat(provider.artifactSchema().trim()).startsWith("{").endsWith("}");
  }

  @Test
  void throws_illegal_state_exception_when_schema_resource_is_missing() {
    assertThatThrownBy(() -> new ClasspathSchemaProvider(resourcePath -> null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Missing classpath resource: /schema/workflow.schema.json");
  }

  @Test
  void throws_unchecked_io_exception_when_schema_resource_cannot_be_read() {
    assertThatThrownBy(() -> new ClasspathSchemaProvider(resourcePath -> new BrokenInputStream()))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessage("Failed to read classpath resource: /schema/workflow.schema.json")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void rejects_null_resource_loader() {
    assertThatThrownBy(() -> new ClasspathSchemaProvider(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("resourceLoader must not be null");
  }

  private static final class BrokenInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      throw new IOException("simulated read failure");
    }
  }
}
