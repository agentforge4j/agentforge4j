package com.agentforge4j.core.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreExceptionBoundaryTest {

  @Test
  void coreDoesNotExposeUserPromptLimitExceededException() {
    assertThatThrownBy(() -> Class.forName(
        "com.agentforge4j.core.exception.UserPromptLimitExceededException"))
        .isInstanceOf(ClassNotFoundException.class);
  }
}
