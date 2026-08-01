// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.llm.api.ModelTier;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@code agentforge4j.llm.model-tiers.<provider>.<tier>} binding through
 * {@link BootstrapAutoConfiguration}: every declared {@link ModelTier} is accepted as a tier key,
 * and an unrecognised one fails context startup with a message naming the whole valid set.
 *
 * <p>The resolver built from these overrides is not exposed on {@code AgentForge4j.components()}, so
 * a started context is the observable outcome: an unrecognised tier throws out of {@code parseTier}
 * during bean creation, which is what the negative case pins.
 */
class ModelTierPropertiesBindingTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(BootstrapAutoConfiguration.class))
      .withPropertyValues(
          "agentforge4j.load-shipped-agents=false",
          "agentforge4j.load-shipped-workflows=false");

  @Test
  void bindsPremiumTierOverride() {
    runner.withPropertyValues("agentforge4j.llm.model-tiers.openai.premium=gpt-some-premium-model")
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).hasSingleBean(AgentForge4j.class);
        });
  }

  @Test
  void bindsEveryDeclaredTierAsAnOverrideKey() {
    String[] properties = new String[ModelTier.values().length];
    for (int i = 0; i < ModelTier.values().length; i++) {
      properties[i] = "agentforge4j.llm.model-tiers.openai."
          + ModelTier.values()[i].name().toLowerCase(Locale.ROOT) + "=some-model";
    }

    runner.withPropertyValues(properties).run(ctx -> assertThat(ctx).hasNotFailed());
  }

  @Test
  void rejectsUnknownTierNameAndListsEveryDeclaredTier() {
    runner.withPropertyValues("agentforge4j.llm.model-tiers.openai.supreme=gpt-some-model")
        .run(ctx -> {
          assertThat(ctx).hasFailed();
          assertThat(illegalStateCauseOf(ctx.getStartupFailure()))
              .hasMessageContaining("Invalid tier 'supreme'")
              .hasMessageContaining("openai")
              .hasMessageContaining("agentforge4j.llm.model-tiers")
              // Derived from the enum, so the message can never omit a tier that is actually valid.
              .hasMessageContaining(ModelTier.joinedNames())
              .hasMessageContaining("PREMIUM");
        });
  }

  /**
   * Returns the {@link IllegalStateException} {@code parseTier} raised, from inside the bean-creation
   * wrapper Spring adds around it. Not the root cause — that is the {@link IllegalArgumentException}
   * {@code ModelTier.fromName} threw, which carries the raw enum message rather than the guidance.
   */
  private static Throwable illegalStateCauseOf(Throwable startupFailure) {
    for (Throwable current = startupFailure; current != null; current = current.getCause()) {
      if (current instanceof IllegalStateException) {
        return current;
      }
    }
    throw new AssertionError("no IllegalStateException in the startup failure chain",
        startupFailure);
  }
}
