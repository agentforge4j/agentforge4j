package com.agentforge4j.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.api.ModelTier;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigModelTierResolverTest {

  @Test
  void resolvesShippedDefaultsForEveryProviderAndTier() {
    ConfigModelTierResolver resolver = ConfigModelTierResolver.withShippedDefaults();

    assertThat(resolver.resolve("claude", ModelTier.POWERFUL)).isEqualTo("claude-opus-4-8");
    assertThat(resolver.resolve("claude", ModelTier.STANDARD)).isEqualTo("claude-sonnet-4-6");
    assertThat(resolver.resolve("claude", ModelTier.LITE)).isEqualTo("claude-haiku-4-5-20251001");
    assertThat(resolver.resolve("openai", ModelTier.POWERFUL)).isEqualTo("gpt-5.5");
    assertThat(resolver.resolve("ollama", ModelTier.LITE)).isEqualTo("qwen3:4b");
    assertThat(resolver.resolve("vllm", ModelTier.POWERFUL)).isEqualTo("Qwen/Qwen3-32B");
    assertThat(resolver.resolve("bedrock", ModelTier.LITE))
        .isEqualTo("anthropic.claude-haiku-4-5-20251001-v1:0");
  }

  @Test
  void returnsNullWhenProviderIsUnknown() {
    ConfigModelTierResolver resolver = ConfigModelTierResolver.withShippedDefaults();

    assertThat(resolver.resolve("no-such-provider", ModelTier.STANDARD)).isNull();
  }

  @Test
  void normalizesProviderNameCaseAndWhitespace() {
    ConfigModelTierResolver resolver = ConfigModelTierResolver.withShippedDefaults();

    assertThat(resolver.resolve("  Claude ", ModelTier.STANDARD)).isEqualTo("claude-sonnet-4-6");
  }

  @Test
  void overridesWinOverShippedDefaultsPerProviderAndTier() {
    Map<ModelTier, String> claudeOverride = new EnumMap<>(ModelTier.class);
    claudeOverride.put(ModelTier.POWERFUL, "claude-opus-custom");
    ConfigModelTierResolver resolver = ConfigModelTierResolver.withShippedDefaultsAndOverrides(
        Map.of("claude", claudeOverride));

    assertThat(resolver.resolve("claude", ModelTier.POWERFUL)).isEqualTo("claude-opus-custom");
    // Untouched tier still falls back to the shipped default.
    assertThat(resolver.resolve("claude", ModelTier.STANDARD)).isEqualTo("claude-sonnet-4-6");
    // Untouched provider still falls back to the shipped default.
    assertThat(resolver.resolve("openai", ModelTier.LITE)).isEqualTo("gpt-5.4-nano");
  }

  @Test
  void overridesCanAddANewProvider() {
    Map<ModelTier, String> custom = new EnumMap<>(ModelTier.class);
    custom.put(ModelTier.STANDARD, "custom-model");
    ConfigModelTierResolver resolver = ConfigModelTierResolver.withShippedDefaultsAndOverrides(
        Map.of("my-provider", custom));

    assertThat(resolver.resolve("my-provider", ModelTier.STANDARD)).isEqualTo("custom-model");
    assertThat(resolver.resolve("my-provider", ModelTier.LITE)).isNull();
  }

  @Test
  void nullOverridesLeavesShippedDefaultsIntact() {
    ConfigModelTierResolver resolver =
        ConfigModelTierResolver.withShippedDefaultsAndOverrides(null);

    assertThat(resolver.resolve("openai", ModelTier.STANDARD)).isEqualTo("gpt-5.4-mini");
  }
}
