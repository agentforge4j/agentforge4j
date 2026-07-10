// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Loads JSON schema documents from classpath resources in {@code /schema}.
 */
@Getter
@Accessors(fluent = true)
public final class ClasspathSchemaProvider implements SchemaProvider {

  private final String workflowSchema;
  private final String agentSchema;
  private final String blueprintSchema;
  private final String artifactSchema;
  private final String integrationSchema;
  private final String contextPackSchema;

  /**
   * Creates a provider by loading all required schemas from the classpath.
   *
   * @throws IllegalStateException if any required schema resource is missing
   * @throws UncheckedIOException  if a schema resource cannot be read
   */
  public ClasspathSchemaProvider() {
    this(ClasspathSchemaProvider.class::getResourceAsStream);
  }

  ClasspathSchemaProvider(Function<String, InputStream> resourceLoader) {
    Validate.notNull(resourceLoader, "resourceLoader must not be null");
    this.workflowSchema = load(resourceLoader, "/schema/workflow.schema.json");
    this.agentSchema = load(resourceLoader, "/schema/agent.schema.json");
    this.blueprintSchema = load(resourceLoader, "/schema/blueprint.schema.json");
    this.artifactSchema = load(resourceLoader, "/schema/artifact.schema.json");
    this.integrationSchema = load(resourceLoader, "/schema/integration.schema.json");
    this.contextPackSchema = load(resourceLoader, "/schema/context-pack.schema.json");
  }

  private static String load(Function<String, InputStream> resourceLoader, String resourcePath) {
    try (InputStream stream = resourceLoader.apply(resourcePath)) {
      Validate.notNull(stream,
          () -> new IllegalStateException(
              "Missing classpath resource: %s".formatted(resourcePath)));
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read classpath resource: %s".formatted(resourcePath), e);
    }
  }
}

