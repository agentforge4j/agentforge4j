// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolPropertiesTest {

  @Test
  void allowPrivateNetworksIsFalseWhenEgressIsUnset() {
    ToolProperties properties = new ToolProperties(null, null, null, null);

    assertThat(properties.allowPrivateNetworks()).isFalse();
  }

  @Test
  void allowPrivateNetworksIsFalseWhenEgressFlagIsNull() {
    ToolProperties properties =
        new ToolProperties(null, null, null, new ToolProperties.Egress(null));

    assertThat(properties.allowPrivateNetworks()).isFalse();
  }

  @Test
  void allowPrivateNetworksIsFalseWhenEgressFlagIsFalse() {
    ToolProperties properties =
        new ToolProperties(null, null, null, new ToolProperties.Egress(false));

    assertThat(properties.allowPrivateNetworks()).isFalse();
  }

  @Test
  void allowPrivateNetworksIsTrueOnlyWhenEgressFlagIsExplicitlyTrue() {
    ToolProperties properties =
        new ToolProperties(null, null, null, new ToolProperties.Egress(true));

    assertThat(properties.allowPrivateNetworks()).isTrue();
  }
}
