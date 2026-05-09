package com.agentforge4j.integrations;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultIntegrationRegistryTest {

  @Test
  void resolveReturnsIntegrationWhenEnabledAndRegistered() {
    AgentIntegration slack = integration("slack", (op, p) -> "ok");
    var registry = new DefaultIntegrationRegistry(
        List.of(slack),
        Map.of("slack", new StandardIntegrationConfig(true, List.of("post"))));

    assertThat(registry.resolve("slack")).containsSame(slack);
  }

  @Test
  void resolveEmptyWhenConfigDisabled() {
    AgentIntegration slack = integration("slack", (op, p) -> "ok");
    var registry = new DefaultIntegrationRegistry(
        List.of(slack),
        Map.of("slack", new StandardIntegrationConfig(false, List.of("post"))));

    assertThat(registry.resolve("slack")).isEmpty();
  }

  @Test
  void resolveEmptyWhenNoConfigEntry() {
    AgentIntegration slack = integration("slack", (op, p) -> "ok");
    var registry = new DefaultIntegrationRegistry(List.of(slack), Map.of());

    assertThat(registry.resolve("slack")).isEmpty();
  }

  @Test
  void resolveEmptyWhenEnabledButIntegrationNotRegistered() {
    var registry = new DefaultIntegrationRegistry(
        List.of(),
        Map.of("slack", new StandardIntegrationConfig(true, List.of("post"))));

    assertThat(registry.resolve("slack")).isEmpty();
  }

  @Test
  void isOperationAllowedWhenListed() {
    AgentIntegration jira = integration("jira", (op, p) -> "x");
    var registry = new DefaultIntegrationRegistry(
        List.of(jira),
        Map.of("jira", new StandardIntegrationConfig(true, List.of("createIssue", "search"))));

    assertThat(registry.isOperationAllowed("jira", "search")).isTrue();
    assertThat(registry.isOperationAllowed("jira", "createIssue")).isTrue();
  }

  @Test
  void isOperationAllowedFalseWhenDisabled() {
    AgentIntegration jira = integration("jira", (op, p) -> "x");
    var registry = new DefaultIntegrationRegistry(
        List.of(jira),
        Map.of("jira", new StandardIntegrationConfig(false, List.of("read"))));

    assertThat(registry.isOperationAllowed("jira", "read")).isFalse();
  }

  @Test
  void isOperationAllowedFalseWhenUnknownIntegration() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("jira", (op, p) -> "x")),
        Map.of("jira", new StandardIntegrationConfig(true, List.of("read"))));

    assertThat(registry.isOperationAllowed("unknown", "read")).isFalse();
  }

  @Test
  void isOperationAllowedFalseWhenAllowedListEmpty() {
    AgentIntegration jira = integration("jira", (op, p) -> "x");
    var registry = new DefaultIntegrationRegistry(
        List.of(jira),
        Map.of("jira", new StandardIntegrationConfig(true, List.of())));

    assertThat(registry.isOperationAllowed("jira", "search")).isFalse();
  }

  @Test
  void isOperationAllowedFalseWhenNotInList() {
    AgentIntegration jira = integration("jira", (op, p) -> "x");
    var registry = new DefaultIntegrationRegistry(
        List.of(jira),
        Map.of("jira", new StandardIntegrationConfig(true, List.of("read"))));

    assertThat(registry.isOperationAllowed("jira", "delete")).isFalse();
  }

  @Test
  void isOperationAllowedTrueWhenIntegrationMissingFromListButConfiguredAndEnabled() {
    var registry = new DefaultIntegrationRegistry(
        List.of(),
        Map.of("slack", new StandardIntegrationConfig(true, List.of("post"))));

    assertThat(registry.isOperationAllowed("slack", "post")).isTrue();
  }

  @Test
  void rejectsNullIntegrationsList() {
    assertThatThrownBy(() -> new DefaultIntegrationRegistry(null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("integrations must not be null");
  }

  @Test
  void rejectsNullConfigsMap() {
    AgentIntegration jira = integration("jira", (op, p) -> "x");
    assertThatThrownBy(() -> new DefaultIntegrationRegistry(List.of(jira), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configs must not be null");
  }

  @Test
  void configValidateFailurePropagatesFromConstructor() {
    AgentIntegration jira = integration("jira", (op, p) -> "x");

    assertThatThrownBy(() -> new DefaultIntegrationRegistry(
        List.of(jira),
        Map.of("jira", StubIntegrationConfig.failingValidate())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid config");
  }

  @Test
  void duplicateIntegrationIdsFailFast() {
    AgentIntegration a = integration("dup", (op, p) -> "a");
    AgentIntegration b = integration("dup", (op, p) -> "b");

    assertThatThrownBy(() -> new DefaultIntegrationRegistry(
        List.of(a, b),
        Map.of("dup", new StandardIntegrationConfig(true, List.of("x")))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageMatching("(?i).*duplicate.*");
  }

  @Test
  void resolveRejectsBlankIntegrationId() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")),
        Map.of("x", new StandardIntegrationConfig(true, List.of("p"))));

    assertThatThrownBy(() -> registry.resolve(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("integrationId");
  }

  @Test
  void resolveRejectsNullIntegrationId() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")),
        Map.of("x", new StandardIntegrationConfig(true, List.of("p"))));

    assertThatThrownBy(() -> registry.resolve(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isEnabledRejectsBlankIntegrationId() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")),
        Map.of("x", new StandardIntegrationConfig(true, List.of("p"))));

    assertThatThrownBy(() -> registry.isEnabled("\t"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("integrationId");
  }

  @Test
  void isEnabledRejectsNullIntegrationId() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")),
        Map.of("x", new StandardIntegrationConfig(true, List.of("p"))));

    assertThatThrownBy(() -> registry.isEnabled(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("integrationId");
  }

  @Test
  void isEnabledTrueWhenConfiguredAndEnabled() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("slack", (op, p) -> "ok")),
        Map.of("slack", new StandardIntegrationConfig(true, List.of("post"))));

    assertThat(registry.isEnabled("slack")).isTrue();
  }

  @Test
  void isEnabledFalseWhenConfiguredButDisabled() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("slack", (op, p) -> "ok")),
        Map.of("slack", new StandardIntegrationConfig(false, List.of("post"))));

    assertThat(registry.isEnabled("slack")).isFalse();
  }

  @Test
  void isEnabledFalseWhenUnknownIntegration() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("slack", (op, p) -> "ok")),
        Map.of("slack", new StandardIntegrationConfig(true, List.of("post"))));

    assertThat(registry.isEnabled("teams")).isFalse();
  }

  @Test
  void isOperationAllowedRejectsBlankOperation() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")),
        Map.of("x", new StandardIntegrationConfig(true, List.of("p"))));

    assertThatThrownBy(() -> registry.isOperationAllowed("x", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operation");
  }

  @Test
  void isOperationAllowedRejectsNullOperation() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")),
        Map.of("x", new StandardIntegrationConfig(true, List.of("p"))));

    assertThatThrownBy(() -> registry.isOperationAllowed("x", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("operation");
  }

  @Test
  void isOperationAllowedRejectsNullIntegrationId() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")),
        Map.of("x", new StandardIntegrationConfig(true, List.of("p"))));

    assertThatThrownBy(() -> registry.isOperationAllowed(null, "p"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("integrationId");
  }

  @Test
  void isOperationAllowedRejectsBlankIntegrationId() {
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")),
        Map.of("x", new StandardIntegrationConfig(true, List.of("p"))));

    assertThatThrownBy(() -> registry.isOperationAllowed("", "p"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("integrationId");
  }

  @Test
  void configsMapIsSnapshotNotAffectedByLaterMutations() {
    Map<String, IntegrationConfig> configs = new HashMap<>();
    configs.put("x", new StandardIntegrationConfig(true, List.of("p")));
    var registry = new DefaultIntegrationRegistry(
        List.of(integration("x", (op, p) -> "")), configs);
    configs.put("y", new StandardIntegrationConfig(true, List.of("q")));

    assertThat(registry.isEnabled("y")).isFalse();
    assertThat(registry.resolve("y")).isEmpty();
  }

  @Test
  void multipleIntegrationsIndependent() {
    AgentIntegration a = integration("a", (op, p) -> "A");
    AgentIntegration b = integration("b", (op, p) -> "B");
    var registry = new DefaultIntegrationRegistry(
        List.of(a, b),
        Map.of(
            "a", new StandardIntegrationConfig(true, List.of("oa")),
            "b", new StandardIntegrationConfig(true, List.of("ob"))));

    assertThat(registry.resolve("a")).containsSame(a);
    assertThat(registry.resolve("b")).containsSame(b);
    assertThat(registry.isOperationAllowed("a", "oa")).isTrue();
    assertThat(registry.isOperationAllowed("b", "ob")).isTrue();
    assertThat(registry.isOperationAllowed("a", "ob")).isFalse();
  }

  private static AgentIntegration integration(String id, IntegrationBody body) {
    return new AgentIntegration() {
      @Override
      public String integrationId() {
        return id;
      }

      @Override
      public String execute(String operation, Map<String, Object> payload) {
        return body.run(operation, payload);
      }
    };
  }

  @FunctionalInterface
  private interface IntegrationBody {
    String run(String operation, Map<String, Object> payload);
  }
}
