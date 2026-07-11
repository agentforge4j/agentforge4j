// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputContractTest {

  @Test
  void buildsWithSchemaAndDiscipline() {
    OutputContract contract = new OutputContract("schema/req.json", OutputDiscipline.STRUCTURED_ONLY,
        true);

    assertThat(contract.schemaRef()).isEqualTo("schema/req.json");
    assertThat(contract.discipline()).isEqualTo(OutputDiscipline.STRUCTURED_ONLY);
    assertThat(contract.rationaleAllowed()).isTrue();
  }

  @Test
  void allowsNullSchemaRefForFreeform() {
    OutputContract contract = new OutputContract(null, OutputDiscipline.FREEFORM, false);

    assertThat(contract.schemaRef()).isNull();
  }

  @Test
  void rejectsNullDiscipline() {
    assertThatThrownBy(() -> new OutputContract("s", null, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankSchemaRefWhenStructuredOnly() {
    assertThatThrownBy(() -> new OutputContract("  ", OutputDiscipline.STRUCTURED_ONLY, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankSchemaRefForOtherDisciplines() {
    // A present schemaRef is never blank; only null means "no schema declared".
    assertThatThrownBy(() -> new OutputContract("  ", OutputDiscipline.FREEFORM, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OutputContract("  ", OutputDiscipline.STRUCTURED_PREFERRED, false))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
