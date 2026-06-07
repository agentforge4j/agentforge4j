package com.agentforge4j.runtime.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Security-focused tests for {@link LocalFileSink}, the filesystem-backed {@link FileSink} that a
 * {@code CREATE_FILE} command ultimately reaches. This is the live path-traversal guard for the
 * dangerous-file-creation scenario: a model-supplied path that tries to escape the base directory
 * must be rejected with the same {@link IllegalArgumentException} that
 * {@code Validate.requireWithinBase} raises, exercised end-to-end through the sink rather than by
 * calling {@code Validate} directly.
 */
class LocalFileSinkSecurityTest {

  private static final String RUN_ID = "run-1";
  private static final String STEP_ID = "step-1";

  @TempDir
  Path baseDir;

  private LocalFileSink sink;

  @BeforeEach
  void setUp() {
    sink = new LocalFileSink(baseDir);
  }

  @Test
  void validPath_writesWithinRunNamespace() throws IOException {
    sink.write(RUN_ID, STEP_ID, "reports/summary.txt", "hello");

    Path written = baseDir.resolve(RUN_ID).resolve("reports").resolve("summary.txt");
    assertThat(Files.readString(written)).isEqualTo("hello");
  }

  @Test
  void traversalPathEscapingBase_rejected() {
    // run-1/../../escape.txt resolves above the base directory once the run namespace is cancelled.
    assertThatThrownBy(() -> sink.write(RUN_ID, STEP_ID, "../../escape.txt", "owned"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Path escapes base directory: %s/%s".formatted(RUN_ID, "../../escape.txt"));

    assertThat(baseDir.getParent().resolve("escape.txt")).doesNotExist();
  }

  @Test
  void deepTraversalPathEscapingBase_rejected() {
    String path = "../../../../../../etc/passwd";

    assertThatThrownBy(() -> sink.write(RUN_ID, STEP_ID, path, "owned"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Path escapes base directory: %s/%s".formatted(RUN_ID, path));
  }

  @Test
  void blankPath_rejectedWithoutTouchingFilesystem() {
    assertThatThrownBy(() -> sink.write(RUN_ID, STEP_ID, "   ", "owned"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("path must not be blank");
  }
}
