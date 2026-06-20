// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.capture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CapturingFileSinkTest {

  @Test
  void capturesWritesVerbatim() {
    CapturingFileSink sink = new CapturingFileSink();

    sink.write("run-1", "step-a", "out/result.txt", "hello");
    sink.write("run-1", "step-b", "../escape.txt", "traversal-attempt");

    assertThat(sink.capturedFiles())
        .extracting(CapturedFile::path, CapturedFile::content)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("out/result.txt", "hello"),
            org.assertj.core.groups.Tuple.tuple("../escape.txt", "traversal-attempt"));
  }

  @Test
  void emptyByDefault() {
    assertThat(new CapturingFileSink().capturedFiles()).isEmpty();
  }
}
