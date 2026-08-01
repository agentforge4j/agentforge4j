// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the properties of the shared-helper migration that no behavioural test can see: that the
 * Bedrock wrapper does not log the prompt-cache decision itself, and that it keeps no member the
 * shared implementation already owns.
 *
 * <p>The shared implementation logs the decision, and every cached request reaches that path. The
 * natural way to introduce the shared helper is to leave the provider's own log statement where it
 * was, and the result — the same decision line twice per request — produces no test failure, no
 * wrong output and no error. It only shows up as duplicated noise in production logs.
 *
 * <p>Asserted against the compiled class file rather than by capturing output:
 * {@code System.Logger} routes to {@code java.util.logging}, which this module does not read under
 * JPMS, and widening the production module descriptor to make a test observable would be a worse
 * trade. Reading the constant pool proves the stronger property a field check cannot — that the
 * wrapper acquires no logger <em>anywhere</em>, including inline inside a method.
 */
class BedrockPromptCacheDelegationTest {

  /**
   * The compiled class file's bytes as latin-1 text, so constant-pool entries (UTF-8 encoded
   * strings) can be searched literally. Read from the classloader resource, which is the file on
   * disk — coverage instrumentation rewrites classes in memory and does not affect it.
   */
  private static String classFileOf(Class<?> type) throws IOException {
    String resource = "/" + type.getName().replace('.', '/') + ".class";
    try (InputStream in = type.getResourceAsStream(resource)) {
      assertThat(in).as("compiled class file for %s", type.getName()).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
    }
  }

  @Test
  void theWrapperAcquiresNoLoggerAnywhere() throws IOException {
    String classFile = classFileOf(BedrockPromptCacheSupport.class);

    assertThat(classFile)
        .as("%s references a logger; the prompt-cache decision is logged once, by "
            + "PromptLayerCacheSupport, and logging it here as well would double every line "
            + "for every cached request",
            BedrockPromptCacheSupport.class.getSimpleName())
        .doesNotContain("getLogger")
        .doesNotContain("java/lang/System$Logger");
  }

  @Test
  void theWrapperDeclaresNoLoggerField() {
    for (Field field : BedrockPromptCacheSupport.class.getDeclaredFields()) {
      assertThat(field.getType())
          .as("%s declares a logger field", BedrockPromptCacheSupport.class.getSimpleName())
          .isNotEqualTo(System.Logger.class);
    }
  }

  @Test
  void theWrapperKeepsOnlyItsProviderSpecificState() {
    // What is left after the migration should be the threshold table — no slicing state, no
    // re-implemented constants, nothing the shared implementation already exposes.
    assertThat(BedrockPromptCacheSupport.class.getDeclaredFields())
        .extracting(Field::getName)
        .containsExactly("MODEL_MIN_CACHEABLE_SEGMENT_TOKENS");
  }

  @Test
  void theWrapperKeepsOnlyItsProductionEntryPoint() {
    // Every other method delegated straight to PromptLayerCacheSupport and lost its production
    // caller once buildSystemBlocks started delegating. Synthetic members (the DTO lambda, and
    // anything coverage tooling adds) are not part of the authored surface.
    assertThat(Arrays.stream(BedrockPromptCacheSupport.class.getDeclaredMethods())
        .filter(method -> !method.isSynthetic())
        .map(Method::getName))
        .containsExactly("buildSystemBlocks");
  }
}
