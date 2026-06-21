// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.core.spi.tool.ToolSourceKind;
import org.junit.jupiter.api.Test;

class SecureDefaultToolPolicyTest {

  private final SecureDefaultToolPolicy policy = new SecureDefaultToolPolicy();

  // The policy reads only the descriptor's source kind; the command and context are irrelevant to
  // the decision, so the tests pass null for them deliberately.

  @Test
  void allowsInProcessTools() {
    PolicyDecision decision =
        policy.evaluate(null, descriptor(ToolSourceKind.IN_PROCESS, false), null);

    assertThat(decision).isInstanceOf(PolicyDecision.Allow.class);
  }

  @Test
  void deniesRemoteHttpToolsWithAProviderSpecificReason() {
    PolicyDecision decision =
        policy.evaluate(null, descriptor(ToolSourceKind.REMOTE_HTTP, false), null);

    assertThat(decision).isInstanceOfSatisfying(PolicyDecision.Deny.class,
        deny -> assertThat(deny.reason()).contains("remote network tool", "http:remote"));
  }

  @Test
  void deniesLocalProcessToolsWithAProviderSpecificReason() {
    PolicyDecision decision =
        policy.evaluate(null, descriptor(ToolSourceKind.LOCAL_PROCESS, false), null);

    assertThat(decision).isInstanceOfSatisfying(PolicyDecision.Deny.class,
        deny -> assertThat(deny.reason()).contains("local-process tool", "mcp:stdio"));
  }

  @Test
  void aReadOnlyRemoteToolIsStillDenied() {
    // mutating=false is advisory and must never grant an exemption from the secure default.
    PolicyDecision decision =
        policy.evaluate(null, descriptor(ToolSourceKind.REMOTE_HTTP, false), null);

    assertThat(decision).isInstanceOf(PolicyDecision.Deny.class);
  }

  @Test
  void allowAllLiftsTheDenyForRemoteTools() {
    PolicyDecision decision = ToolPolicy.allowAll()
        .evaluate(null, descriptor(ToolSourceKind.REMOTE_HTTP, true), null);

    assertThat(decision).isInstanceOf(PolicyDecision.Allow.class);
  }

  private static ToolDescriptor descriptor(ToolSourceKind kind, boolean mutating) {
    String providerId = switch (kind) {
      case IN_PROCESS -> "inproc:demo";
      case REMOTE_HTTP -> "http:remote";
      case LOCAL_PROCESS -> "mcp:stdio";
    };
    return new ToolDescriptor("demo.do_thing", "Do Thing", null, null, null,
        new ToolSource(providerId, "do_thing", kind), new ToolRiskMetadata(mutating));
  }
}
