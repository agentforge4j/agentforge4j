package com.agentforge4j.integrations;

import java.util.List;

final class StubIntegrationConfig implements IntegrationConfig {

  private final boolean enabled;
  private final List<String> allowedOperations;
  private final Runnable validateAction;

  StubIntegrationConfig(boolean enabled, List<String> allowedOperations) {
    this(enabled, allowedOperations, () -> {});
  }

  private StubIntegrationConfig(boolean enabled, List<String> allowedOperations,
      Runnable validateAction) {
    this.enabled = enabled;
    this.allowedOperations = allowedOperations == null ? List.of() : List.copyOf(allowedOperations);
    this.validateAction = validateAction;
  }

  static StubIntegrationConfig failingValidate() {
    return new StubIntegrationConfig(true, List.of("op"),
        () -> {
          throw new IllegalStateException("invalid config");
        });
  }

  @Override
  public boolean enabled() {
    return enabled;
  }

  @Override
  public void validate() {
    validateAction.run();
  }

  @Override
  public List<String> allowedOperations() {
    return allowedOperations;
  }
}
