package com.agentforge4j.integrations;

import com.agentforge4j.util.Validate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link IntegrationRegistry} backed by registered {@link AgentIntegration} instances and a
 * parallel map of {@link IntegrationConfig} entries keyed by integration id.
 * <p>
 * {@link #resolve(String)}, {@link #isEnabled(String)}, and
 * {@link #isOperationAllowed(String, String)} treat an integration as available only when it is
 * enabled in {@code configs}, registered in {@code integrations}, and (for operations) listed in
 * the config allow list.
 */
public final class DefaultIntegrationRegistry implements IntegrationRegistry {

  private static final System.Logger LOG = System.getLogger(
      DefaultIntegrationRegistry.class.getName());

  private final Map<String, AgentIntegration> integrations;
  private final Map<String, IntegrationConfig> configs;

  /**
   * Builds an unmodifiable view of {@code integrations} keyed by
   * {@link AgentIntegration#integrationId()} and an unmodifiable copy of {@code configs}. Runs
   * {@link IntegrationConfig#validate()} on each config value before storing.
   *
   * @param integrations integration beans to expose through {@link #resolve(String)}
   * @param configs      configuration keyed by integration id; must contain an entry for every id
   *                     that should participate in enablement and allow-list checks
   * @throws IllegalArgumentException when {@code integrations} or {@code configs} is {@code null},
   *                                  or when any config value fails validation
   * @throws IllegalStateException    when two integrations share the same
   *                                  {@link AgentIntegration#integrationId()}
   */
  public DefaultIntegrationRegistry(List<AgentIntegration> integrations,
      Map<String, IntegrationConfig> configs) {
    Validate.notNull(configs, "configs must not be null")
        .values()
        .forEach(IntegrationConfig::validate);
    this.integrations = Validate.notNull(integrations, "integrations must not be null")
        .stream()
        .collect(Collectors.toUnmodifiableMap(
            AgentIntegration::integrationId,
            Function.identity()));
    this.configs = Map.copyOf(configs);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException when {@code integrationId} is null or blank
   */
  @Override
  public Optional<AgentIntegration> resolve(String integrationId) {
    if (!isEnabled(integrationId)) {
      return Optional.empty();
    }
    LOG.log(System.Logger.Level.DEBUG, "Resolving integration integrationId={0}", integrationId);
    return Optional.ofNullable(integrations.get(integrationId));
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException when {@code integrationId} or {@code operation} is null or
   *                                  blank
   */
  @Override
  public boolean isOperationAllowed(String integrationId, String operation) {
    Validate.notBlank(operation, "operation must not be blank");
    boolean permitted = isEnabled(integrationId)
        && configs.get(integrationId).allowedOperations().contains(operation);
    System.Logger.Level level = permitted
        ? System.Logger.Level.DEBUG
        : System.Logger.Level.WARNING;
    LOG.log(level,
        "Integration operation {0} integrationId={1}, operation={2}",
        permitted ? "allowed" : "blocked",
        integrationId, operation);
    return permitted;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException when {@code integrationId} is null or blank
   */
  @Override
  public boolean isEnabled(String integrationId) {
    Validate.notBlank(integrationId, "integrationId must not be blank");
    IntegrationConfig config = configs.get(integrationId);
    return config != null && config.enabled() && integrations.containsKey(integrationId);
  }
}
