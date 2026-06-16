// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileSinkTest {

  @Test
  void write_createsNamespacedFileUnderBase(@TempDir Path base) throws Exception {
    LocalFileSink sink = new LocalFileSink(base);
    sink.write("run-a", "step-1", "out/note.txt", "hello");

    Path expected = base.resolve("run-a").resolve("out").resolve("note.txt");
    assertThat(expected).exists();
    assertThat(Files.readString(expected, StandardCharsets.UTF_8)).isEqualTo("hello");
  }

  @Test
  void write_rejectsPathThatEscapesRunNamespace(@TempDir Path base) {
    LocalFileSink sink = new LocalFileSink(base);
    assertThatThrownBy(() -> sink.write("run-a", "step-1", "../../escape.txt", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Path escapes");
  }

  @Test
  void write_rejectsBlankRunId(@TempDir Path base) {
    LocalFileSink sink = new LocalFileSink(base);
    assertThatThrownBy(() -> sink.write(" ", "step-1", "f.txt", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("runId");
  }
}
