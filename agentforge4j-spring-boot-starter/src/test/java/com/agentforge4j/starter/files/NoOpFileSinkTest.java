package com.agentforge4j.starter.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class NoOpFileSinkTest {

  @Test
  void writeDiscardsContentWithoutThrowing() {
    NoOpFileSink sink = new NoOpFileSink();
    assertThatCode(() -> sink.write("run-1", "step-a", "out.txt", "hello")).doesNotThrowAnyException();
  }

  @Test
  void logsDiscardWarningOnlyOncePerInstance() {
    Logger logger = (Logger) LoggerFactory.getLogger(NoOpFileSink.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    Level previous = logger.getLevel();
    logger.setLevel(Level.ALL);
    try {
      NoOpFileSink sink = new NoOpFileSink();
      sink.write("run-1", "step-a", "a.txt", "x");
      sink.write("run-2", "step-b", "b.txt", "y");
      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
      assertThat(appender.list.get(0).getFormattedMessage())
          .contains("generated file outputs will be discarded");
    } finally {
      logger.setLevel(previous);
      logger.detachAppender(appender);
    }
  }

  @Test
  void separateInstancesEachLogOnce() {
    Logger logger = (Logger) LoggerFactory.getLogger(NoOpFileSink.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    Level previous = logger.getLevel();
    logger.setLevel(Level.ALL);
    try {
      new NoOpFileSink().write("r", "s", "p", "c");
      new NoOpFileSink().write("r", "s", "p", "c");
      assertThat(appender.list).hasSize(2);
    } finally {
      logger.setLevel(previous);
      logger.detachAppender(appender);
    }
  }
}
