package com.agentforge4j.llm.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTierResolutionExceptionTest {

  @Test
  void preservesMessage() {
    ModelTierResolutionException exception =
        new ModelTierResolutionException("no mapping for provider 'claude' tier POWERFUL");

    assertThat(exception).hasMessage("no mapping for provider 'claude' tier POWERFUL");
  }

  @Test
  void isUnchecked() {
    assertThat(new ModelTierResolutionException("x")).isInstanceOf(RuntimeException.class);
  }
}
