// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class NoOpFileSinkTest {

  @Test
  void writeDiscardsContentWithoutThrowing() {
    NoOpFileSink sink = new NoOpFileSink();
    assertThatCode(() -> sink.write("run-1", "step-a", "out.txt", "hello"))
        .doesNotThrowAnyException();
  }

  @Test
  void sharedConstantIsANoOpFileSink() {
    assertThat(FileSink.NO_OP_FILE_SINK).isInstanceOf(NoOpFileSink.class);
  }
}
