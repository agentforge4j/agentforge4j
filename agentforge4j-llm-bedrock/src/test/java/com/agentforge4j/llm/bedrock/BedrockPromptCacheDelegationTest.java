// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one property of the shared-helper migration that no behavioural test can see: that the
 * Claude wrapper does not log the prompt-cache decision itself.
 *
 * <p>The shared implementation logs it, and every cached request reaches that path. The natural way
 * to introduce the shared helper is to leave the provider's own log statement where it was, and the
 * result — the same decision line twice per request — produces no test failure, no wrong output and
 * no error. It only shows up as duplicated noise in production logs.
 *
 * <p>Asserted structurally rather than by capturing output: {@code System.Logger} routes to
 * {@code java.util.logging}, which this module does not read under JPMS, and widening the
 * production module descriptor to make a test observable would be a worse trade than checking the
 * one thing that actually matters — that no logger is declared here at all.
 */
class BedrockPromptCacheDelegationTest {

  @Test
  void theWrapperDeclaresNoLoggerOfItsOwn() {
    for (Field field : BedrockPromptCacheSupport.class.getDeclaredFields()) {
      assertThat(field.getType())
          .as("%s declares a logger; the prompt-cache decision is logged once, by "
              + "PromptLayerCacheSupport, and logging it here as well would double every line",
              BedrockPromptCacheSupport.class.getSimpleName())
          .isNotEqualTo(System.Logger.class);
    }
  }

  @Test
  void theWrapperKeepsOnlyItsProviderSpecificState() {
    // What is left after the migration should be the threshold table and the default it falls back
    // to — no slicing state, no re-implemented constants.
    assertThat(BedrockPromptCacheSupport.class.getDeclaredFields())
        .extracting(Field::getName)
        .containsExactlyInAnyOrder("DEFAULT_MIN_CACHEABLE_SEGMENT_TOKENS",
            "MODEL_MIN_CACHEABLE_SEGMENT_TOKENS");
  }
}
