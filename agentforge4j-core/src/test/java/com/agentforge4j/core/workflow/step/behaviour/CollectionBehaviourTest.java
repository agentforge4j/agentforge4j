// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.collection.AuthorizationMode;
import com.agentforge4j.core.workflow.collection.DuplicatePolicy;
import com.agentforge4j.core.workflow.collection.ReopenPolicy;
import com.agentforge4j.core.workflow.collection.ReplacementPolicy;
import com.agentforge4j.core.workflow.collection.WithdrawalPolicy;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CollectionBehaviourTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  @Test
  void appliesDefaultsForNullAndNonPositiveFields() {
    CollectionBehaviour behaviour =
        new CollectionBehaviour(null, 0, null, null, 0, null, null, null, null, null, null, null, null);

    assertThat(behaviour.minItems()).isZero();
    assertThat(behaviour.maxItems()).isNull();
    assertThat(behaviour.maxItemsPerActor()).isNull();
    assertThat(behaviour.maxInlinePayloadBytes())
        .isEqualTo(CollectionBehaviour.DEFAULT_MAX_INLINE_PAYLOAD_BYTES);
    assertThat(behaviour.duplicatePolicy()).isEqualTo(DuplicatePolicy.ALLOW);
    assertThat(behaviour.replacementPolicy()).isEqualTo(ReplacementPolicy.NONE);
    assertThat(behaviour.withdrawalPolicy()).isEqualTo(WithdrawalPolicy.NONE);
    assertThat(behaviour.manualClose()).isTrue();
    assertThat(behaviour.externalDeadlineClosable()).isFalse();
    assertThat(behaviour.reopenPolicy()).isEqualTo(ReopenPolicy.NONE);
    assertThat(behaviour.authorizationMode()).isEqualTo(AuthorizationMode.OPEN);
    assertThat(behaviour.transition()).isEqualTo(StepTransition.AUTO);
  }

  @Test
  void rejectsNegativeMinItems() {
    assertThatThrownBy(() ->
        new CollectionBehaviour(null, -1, null, null, 0, null, null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMaxItemsBelowMinItems() {
    assertThatThrownBy(() ->
        new CollectionBehaviour(null, 5, 3, null, 0, null, null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveMaxItems() {
    assertThatThrownBy(() ->
        new CollectionBehaviour(null, 0, 0, null, 0, null, null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonPositiveMaxItemsPerActor() {
    assertThatThrownBy(() ->
        new CollectionBehaviour(null, 0, null, 0, 0, null, null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void serialisesAndDeserialisesViaSealedDiscriminator() throws Exception {
    StepBehaviour original = new CollectionBehaviour("cv-schema", 1, 10, 3, 2048,
        DuplicatePolicy.REJECT_BY_CLIENT_TOKEN, ReplacementPolicy.OWNER_REPLACE,
        WithdrawalPolicy.OWNER_WITHDRAW, true, true, ReopenPolicy.ALLOWED,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);

    String json = MAPPER.writeValueAsString(original);
    assertThat(json).contains("\"type\":\"COLLECTION\"");

    StepBehaviour roundTripped = MAPPER.readValue(json, StepBehaviour.class);
    assertThat(roundTripped).isInstanceOf(CollectionBehaviour.class).isEqualTo(original);
  }
}
