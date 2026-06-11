package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.integration.IntegrationCapability;
import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryIntegrationRepositoryTest {

  @Test
  void savesAndFindsById() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    IntegrationDefinition github = definition("github", true, "github.create_pull_request");

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
    IntegrationDefinition github = definition("github", true, "github.create_pull_request");
    IntegrationDefinition jira = definition("jira", false, "jira.create_issue");
    repository.save(github);
    repository.save(jira);

    assertThat(repository.findActive()).containsExactly(github);
  }

  @Test
  void findByCapabilityReturnsActiveExposersOnly() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    IntegrationDefinition active = definition("github", true, "github.create_pull_request");
    IntegrationDefinition inactive = definition("github-fork", false, "github.create_pull_request");
    repository.save(active);
    repository.save(inactive);

    assertThat(repository.findByCapability("github.create_pull_request")).containsExactly(active);
    assertThat(repository.findByCapability("jira.create_issue")).isEmpty();
  }

  @Test
  void setActiveTogglesAndIsReflectedByFindActive() {
    InMemoryIntegrationRepository repository = new InMemoryIntegrationRepository();
    repository.save(definition("github", false, "github.create_pull_request"));

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
    repository.save(definition("github", true, "github.create_pull_request"));

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
    repository.save(definition("github", true, "github.create_pull_request"));
    IntegrationDefinition replacement = definition("github", false, "github.merge_pull_request");

    repository.save(replacement);

    assertThat(repository.findById("github")).isSameAs(replacement);
  }

  private static IntegrationDefinition definition(String id, boolean active, String capability) {
    return new IntegrationDefinition(id, id, IntegrationType.MCP_STDIO, "{}",
        List.of(new IntegrationCapability(capability, capability, false)), active);
  }
}
