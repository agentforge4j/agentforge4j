package com.agentforge4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClasspathSchemaProviderIT {

  @Test
  void loads_workflow_schema_from_expected_classpath_resource() {
    ClasspathSchemaProvider provider = new ClasspathSchemaProvider();

    assertThat(provider.workflowSchema()).isEqualTo(readClasspathResource("/schema/workflow.schema.json"));
  }

  @Test
  void loads_agent_schema_from_expected_classpath_resource() {
    ClasspathSchemaProvider provider = new ClasspathSchemaProvider();

    assertThat(provider.agentSchema()).isEqualTo(readClasspathResource("/schema/agent.schema.json"));
  }

  @Test
  void loads_blueprint_schema_from_expected_classpath_resource() {
    ClasspathSchemaProvider provider = new ClasspathSchemaProvider();

    assertThat(provider.blueprintSchema()).isEqualTo(readClasspathResource("/schema/blueprint.schema.json"));
  }

  @Test
  void loads_artifact_schema_from_expected_classpath_resource() {
    ClasspathSchemaProvider provider = new ClasspathSchemaProvider();

    assertThat(provider.artifactSchema()).isEqualTo(readClasspathResource("/schema/artifact.schema.json"));
  }

  private static String readClasspathResource(String resourcePath) {
    try (InputStream inputStream = ClasspathSchemaProviderIT.class.getResourceAsStream(resourcePath)) {
      assertThat(inputStream)
          .as("resource must be packaged on test classpath: %s", resourcePath)
          .isNotNull();
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read test resource: " + resourcePath, e);
    }
  }
}
