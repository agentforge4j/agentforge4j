package com.agentforge4j.starter.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoOpFileSinkTest {

  private static final String LOGGER_NAME = NoOpFileSink.class.getName();

  private final Logger julLogger = Logger.getLogger(LOGGER_NAME);
  private final List<LogRecord> records = new ArrayList<>();
  private Handler handler;
  private Level previousLevel;

  @BeforeEach
  void attachHandler() {
    handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        records.add(record);
      }

      @Override
      public void flush() {
        // no-op
      }

      @Override
      public void close() {
        // no-op
      }
    };
    previousLevel = julLogger.getLevel();
    julLogger.setLevel(Level.ALL);
    julLogger.addHandler(handler);
  }

  @AfterEach
  void detachHandler() {
    julLogger.removeHandler(handler);
    julLogger.setLevel(previousLevel);
    records.clear();
  }

  @Test
  void writeDiscardsContentWithoutThrowing() {
    NoOpFileSink sink = new NoOpFileSink();
    assertThatCode(() -> sink.write("run-1", "step-a", "out.txt", "hello")).doesNotThrowAnyException();
  }

  @Test
  void logsDiscardWarningOnlyOncePerInstance() {
    NoOpFileSink sink = new NoOpFileSink();
    sink.write("run-1", "step-a", "a.txt", "x");
    sink.write("run-2", "step-b", "b.txt", "y");
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getLevel()).isEqualTo(Level.WARNING);
    assertThat(records.get(0).getMessage())
        .contains("generated file outputs will be discarded");
  }

  @Test
  void separateInstancesEachLogOnce() {
    new NoOpFileSink().write("r", "s", "p", "c");
    new NoOpFileSink().write("r", "s", "p", "c");
    assertThat(records).hasSize(2);
  }
}
