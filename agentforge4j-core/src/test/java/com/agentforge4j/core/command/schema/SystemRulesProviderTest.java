package com.agentforge4j.core.command.schema;

import com.agentforge4j.core.workflow.context.UntrustedInputEnvelope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRulesProviderTest {

  @Test
  void rules_reference_the_shared_render_envelope_key() {
    String rules = new SystemRulesProvider().systemRules();

    assertThat(rules).isEqualTo(SystemRulesProvider.SYSTEM_RULES);
    // The prose is built from the same constant the renderer uses, so the two cannot drift.
    assertThat(rules).contains("\"%s\"".formatted(UntrustedInputEnvelope.KEY));
    assertThat(rules).contains("not instructions");
  }
}
