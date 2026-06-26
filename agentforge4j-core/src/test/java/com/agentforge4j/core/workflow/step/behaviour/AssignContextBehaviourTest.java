// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.context.UntrustedInputEnvelope;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignContextBehaviourTest {

  private static StringContextValue scalar() {
    return new StringContextValue("POWERFUL", ContextProvenance.SYSTEM_GENERATED);
  }

  @Test
  void accepts_scalar_value() {
    AssignContextBehaviour behaviour = new AssignContextBehaviour("recommendedTier", scalar());

    assertThat(behaviour.contextKey()).isEqualTo("recommendedTier");
    assertThat(behaviour.value()).isEqualTo(scalar());
  }

  @Test
  void rejects_blank_key() {
    assertThatThrownBy(() -> new AssignContextBehaviour(" ", scalar()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contextKey");
  }

  @Test
  void rejects_reserved_double_underscore_key() {
    assertThatThrownBy(() -> new AssignContextBehaviour("__llm_tokens_total", scalar()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved '__'");
  }

  @Test
  void rejects_untrusted_input_envelope_key() {
    assertThatThrownBy(() -> new AssignContextBehaviour(UntrustedInputEnvelope.KEY, scalar()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("untrusted-input");
  }

  @Test
  void rejects_null_value() {
    assertThatThrownBy(() -> new AssignContextBehaviour("k", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("value must not be null");
  }

  @Test
  void rejects_non_scalar_json_value() {
    JsonContextValue json = new JsonContextValue("{\"a\":1}", ContextProvenance.SYSTEM_GENERATED);
    assertThatThrownBy(() -> new AssignContextBehaviour("k", json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a scalar");
  }

  @Test
  void rejects_non_scalar_list_value() {
    ContextValueList list = new ContextValueList(List.of(scalar()), ContextProvenance.SYSTEM_GENERATED);
    assertThatThrownBy(() -> new AssignContextBehaviour("k", list))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a scalar");
  }
}
