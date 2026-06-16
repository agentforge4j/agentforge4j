package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import org.junit.jupiter.api.Test;

class InMemoryIntegrationRepositoryTest {

  @Test
  void savesAndFindsById() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    IntegrationDefinition github = definition("github", true);

    repository.save(github);

    assertThat(repository.findById("github")).isSameAs(github);
  }

  @Test
  void findByIdReturnsNullWhenAbsent() {
    assertThat(new InMemoryIntegrationRepository().findById("missing")).isNull();
  }

  @Test
  void findActiveReturnsOnlyActiveIntegrations() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    IntegrationDefinition github = definition("github", true);
    IntegrationDefinition jira = definition("jira", false);
    repository.save(github);
    repository.save(jira);

    assertThat(repository.findActive()).containsExactly(github);
  }

  @Test
  void setActiveTogglesAndIsReflectedByFindActive() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", false));

    assertThat(repository.findActive()).isEmpty();

    repository.setActive("github", true);

    assertThat(repository.findActive())
        .extracting(IntegrationDefinition::id)
        .containsExactly("github");
    assertThat(repository.findById("github").active()).isTrue();
  }

  @Test
  void setActiveFailsFastWhenIdAbsent() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();

    assertThatThrownBy(() -> repository.setActive("missing", true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing");
  }

  @Test
  void removeRemovesAndFindByIdReturnsNullAfterwards() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", true));

    repository.remove("github");

    assertThat(repository.findById("github")).isNull();
    assertThat(repository.findActive()).isEmpty();
  }

  @Test
  void removeIsNoOpWhenIdAbsent() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();

    repository.remove("missing");

    assertThat(repository.findActive()).isEmpty();
  }

  @Test
  void saveWithSameIdOverwritesWithoutThrowing() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", true));
    IntegrationDefinition replacement = definition("github", false);

    repository.save(replacement);

    assertThat(repository.findById("github")).isSameAs(replacement);
  }

  private static IntegrationDefinition definition(String id, boolean active) {
    return new IntegrationDefinition(id, id, IntegrationType.MCP_STDIO, "{}", active);
  }
}
