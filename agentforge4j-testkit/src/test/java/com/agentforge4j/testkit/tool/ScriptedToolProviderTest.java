// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link ScriptedToolProvider}: the {@code succeeding}/{@code failing} factories,
 * the advertised descriptor, the fixed-result invocation, and constructor validation.
 */
class ScriptedToolProviderTest {

  @Test
  void succeedingProviderReturnsSuccessResult() {
    ScriptedToolProvider provider = ScriptedToolProvider.succeeding("prov", "search", "hit");

    assertThat(provider.providerId()).isEqualTo("prov");

    ToolResult result = provider.invoke(null, "{}", null, null);
    assertThat(result.success()).isTrue();
    assertThat(result.output()).isEqualTo("hit");
  }

  @Test
  void failingProviderReturnsFailureResult() {
    ScriptedToolProvider provider = ScriptedToolProvider.failing("prov", "search", "boom");

    ToolResult result = provider.invoke(null, "{}", null, null);
    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("boom");
  }

  @Test
  void listToolsAdvertisesSingleConservativeDescriptor() {
    ScriptedToolProvider provider = ScriptedToolProvider.succeeding("prov", "search", "hit");

    List<ToolDescriptor> descriptors = provider.listTools();

    assertThat(descriptors).hasSize(1);
    ToolDescriptor descriptor = descriptors.get(0);
    assertThat(descriptor.capability()).isEqualTo("search");
    assertThat(descriptor.source().providerId()).isEqualTo("prov");
    assertThat(descriptor.riskMetadata().mutating()).isTrue();
  }

  @Test
  void healthReportsUp() {
    ScriptedToolProvider provider = ScriptedToolProvider.succeeding("prov", "search", "hit");

    assertThat(provider.health().state()).isEqualTo(HealthStatus.State.UP);
  }

  @Test
  void constructorRejectsBlankProviderId() {
    assertThatThrownBy(() -> new ScriptedToolProvider("  ", "search",
        ToolResult.success("x", 1L), "{\"type\":\"object\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructorRejectsBlankCapability() {
    assertThatThrownBy(() -> new ScriptedToolProvider("prov", "  ",
        ToolResult.success("x", 1L), "{\"type\":\"object\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructorRejectsNullResult() {
    assertThatThrownBy(() -> new ScriptedToolProvider("prov", "search", null,
        "{\"type\":\"object\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
