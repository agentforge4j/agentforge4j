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
 * upserts rather than snapshot replacement. Uniqueness across integrations (for example a
 * capability exposed by two of them) is enforced downstream by the resolver, not here — this store
 * allows an id to be overwritten so a reload can replace a prior definition idempotently.
 */
public final class InMemoryIntegrationRepository implements MutableIntegrationRepository {

  private final Map<String, IntegrationDefinition> byId = new ConcurrentHashMap<>();

  @Override
  public List<IntegrationDefinition> findActive() {
    return byId.values().stream().filter(IntegrationDefinition::active).toList();
  }

  @Override
  public IntegrationDefinition findById(String id) {
    return byId.get(Validate.notBlank(id, "id must not be blank"));
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
            existing.config(), active));
    Validate.notNull(updated, () -> new IllegalArgumentException(
        "No integration registered with id '%s'".formatted(id)));
  }

  @Override
  public void remove(String id) {
    byId.remove(Validate.notBlank(id, "id must not be blank"));
  }
}
