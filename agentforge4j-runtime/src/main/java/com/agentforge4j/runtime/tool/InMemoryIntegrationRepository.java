package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.MutableIntegrationRepository;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link MutableIntegrationRepository} (OSS default). Integrations are keyed by id and
 * mutated administratively; the OSS active-toggle is an edit-then-reload, so writes are direct
 * upserts rather than snapshot replacement. Uniqueness across integrations (for example a capability
 * exposed by two of them) is enforced downstream by the resolver, not here — this store allows an
 * id to be overwritten so a reload can replace a prior definition idempotently.
 */
public final class InMemoryIntegrationRepository implements MutableIntegrationRepository {

  private final Map<String, IntegrationDefinition> byId = new ConcurrentHashMap<>();

  @Override
  public List<IntegrationDefinition> findActive() {
    return byId.values().stream().filter(IntegrationDefinition::active).toList();
  }

  @Override
  public IntegrationDefinition findById(String id) {
    Validate.notBlank(id, "id must not be blank");
    return byId.get(id);
  }

  @Override
  public List<IntegrationDefinition> findByCapability(String capability) {
    Validate.notBlank(capability, "capability must not be blank");
    return byId.values().stream()
        .filter(IntegrationDefinition::active)
        .filter(definition -> exposes(definition, capability))
        .toList();
  }

  @Override
  public void save(IntegrationDefinition definition) {
    Validate.notNull(definition, "definition must not be null");
    byId.put(definition.id(), definition);
  }

  @Override
  public void setActive(String id, boolean active) {
    Validate.notBlank(id, "id must not be blank");
    IntegrationDefinition updated = byId.computeIfPresent(id, (key, existing) ->
        new IntegrationDefinition(existing.id(), existing.displayName(), existing.type(),
            existing.config(), existing.capabilities(), active));
    Validate.notNull(updated, () -> new IllegalArgumentException(
        "No integration registered with id '%s'".formatted(id)));
  }

  @Override
  public void remove(String id) {
    Validate.notBlank(id, "id must not be blank");
    byId.remove(id);
  }

  private static boolean exposes(IntegrationDefinition definition, String capability) {
    return definition.capabilities().stream()
        .anyMatch(integrationCapability -> integrationCapability.capability().equals(capability));
  }
}
