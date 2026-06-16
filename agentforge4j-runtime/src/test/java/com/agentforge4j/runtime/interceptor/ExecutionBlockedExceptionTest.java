package com.agentforge4j.runtime.interceptor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionBlockedExceptionTest {

  @Test
  void messageOnlyConstructorCarriesMessageAndNoCause() {
    ExecutionBlockedException ex = new ExecutionBlockedException("over budget");

    assertThat(ex).hasMessage("over budget");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void messageAndCauseConstructorCarriesBoth() {
    IllegalStateException cause = new IllegalStateException("ledger unavailable");

    ExecutionBlockedException ex = new ExecutionBlockedException("blocked", cause);

    assertThat(ex).hasMessage("blocked");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
