package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.runtime.command.LocalFileSink;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileSinkBootstrapTest {

  @Test
  void localFileSinkConstructorWorksFromBootstrapClasspath(@TempDir Path tempDir) throws Exception {
    LocalFileSink sink = new LocalFileSink(tempDir);
    sink.write("run-1", "step-1", "out.txt", "hello");

    Path written = tempDir.resolve("run-1").resolve("out.txt");
    assertThat(written).exists();
    assertThat(Files.readString(written, StandardCharsets.UTF_8)).isEqualTo("hello");
  }
}
