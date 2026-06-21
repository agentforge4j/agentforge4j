// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowSchemaVersionTest {

  @Test
  void supportedVersionIsOne() {
    assertThat(WorkflowSchemaVersion.SUPPORTED_WORKFLOW_SCHEMA_VERSION).isEqualTo(1);
  }
}
