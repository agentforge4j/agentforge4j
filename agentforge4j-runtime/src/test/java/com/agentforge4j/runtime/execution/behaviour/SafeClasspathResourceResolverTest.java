// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.runtime.execution.behaviour.resource.SafeClasspathResourceResolver;
import org.junit.jupiter.api.Test;

class SafeClasspathResourceResolverTest {

  private final SafeClasspathResourceResolver resolver =
      new SafeClasspathResourceResolver();

  @Test
  void resolves_resources_from_each_allowed_root() {
    assertThat(resolver.resolve("/workflow-resources/info.txt")).contains("workflow-resource");
    assertThat(resolver.resolve("/schemas/custom.json")).contains("\"ok\": true");
    assertThat(resolver.resolve("/templates/template.txt")).contains("template-content");
    assertThat(resolver.resolve("/examples/sample.txt")).contains("sample-content");
    assertThat(resolver.resolve("/schema/workflow.schema.json")).contains("\"title\": \"WorkflowDefinition\"");
  }

  @Test
  void rejects_parent_traversal_variants() {
    assertThatThrownBy(() -> resolver.resolve("/examples/../../templates/template.txt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traversal");

    assertThatThrownBy(() -> resolver.resolve("/examples/%2e%2e/%2e%2e/templates/template.txt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("traversal");
  }

  @Test
  void rejects_absolute_filesystem_paths() {
    assertThatThrownBy(() -> resolver.resolve("C:\\Windows\\win.ini"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("absolute filesystem");
  }

  @Test
  void rejects_paths_outside_allowed_roots_and_private_targets() {
    assertThatThrownBy(() -> resolver.resolve("/private/secret.txt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outside allowed roots");
    assertThatThrownBy(() -> resolver.resolve("/META-INF/MANIFEST.MF"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outside allowed roots");
  }

  @Test
  void rejects_control_chars_and_classpath_uri_forms() {
    assertThatThrownBy(() -> resolver.resolve("/examples/sample.txt\u0000"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("control characters");
    assertThatThrownBy(() -> resolver.resolve("jar:file:/etc/passwd!/payload"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URI-style");
  }

  @Test
  void equivalent_normalized_paths_resolve_identically() {
    String canonical = resolver.resolve("/examples/sample.txt");
    assertThat(resolver.resolve("/examples/./sample.txt")).isEqualTo(canonical);
    assertThat(resolver.resolve("/examples/folder/../sample.txt")).isEqualTo(canonical);
  }
}
