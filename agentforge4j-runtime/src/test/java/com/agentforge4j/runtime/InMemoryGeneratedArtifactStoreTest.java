// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryGeneratedArtifactStoreTest {

  @Test
  void registers_and_finds_content_for_a_run() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();

    store.register("run-1", "generate", "agent.json", "{\"id\":\"a\"}");

    assertThat(store.find("run-1", "agent.json")).contains("{\"id\":\"a\"}");
    assertThat(store.artifacts("run-1"))
        .containsExactly(new GeneratedArtifact("generate", "agent.json", "{\"id\":\"a\"}"));
  }

  @Test
  void isolates_artifacts_between_runs() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "generate", "agent.json", "a");

    assertThat(store.find("run-2", "agent.json")).isEmpty();
    assertThat(store.artifacts("run-2")).isEmpty();
  }

  @Test
  void same_path_upserts_last_write_wins() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "generate", "agent.json", "a");

    store.register("run-1", "regen", "agent.json", "b");

    assertThat(store.find("run-1", "agent.json")).contains("b");
    assertThat(store.artifacts("run-1"))
        .containsExactly(new GeneratedArtifact("regen", "agent.json", "b"));
  }

  @Test
  void same_path_in_different_runs_is_allowed() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();

    store.register("run-1", "generate", "agent.json", "a");
    store.register("run-2", "generate", "agent.json", "b");

    assertThat(store.find("run-1", "agent.json")).contains("a");
    assertThat(store.find("run-2", "agent.json")).contains("b");
  }

  @Test
  void rejects_when_max_artifacts_per_run_exceeded() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore(1, 1000);
    store.register("run-1", "generate", "agent.json", "a");

    assertThatThrownBy(() -> store.register("run-1", "generate", "systemprompt.md", "b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeded max generated artifacts");
  }

  @Test
  void upsert_of_existing_path_does_not_count_against_artifact_cap() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore(1, 1000);
    store.register("run-1", "generate", "agent.json", "a");

    // Re-registering the same path replaces content and must not trip the per-run count bound.
    store.register("run-1", "regen", "agent.json", "b");

    assertThat(store.find("run-1", "agent.json")).contains("b");
  }

  @Test
  void remove_evicts_only_the_named_path_and_is_idempotent() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "generate", "agent.json", "a");
    store.register("run-1", "generate", "systemprompt.md", "b");

    store.remove("run-1", "agent.json");
    store.remove("run-1", "agent.json");
    store.remove("run-1", "never-registered");
    store.remove("never-seen", "agent.json");

    assertThat(store.find("run-1", "agent.json")).isEmpty();
    assertThat(store.find("run-1", "systemprompt.md")).contains("b");
  }

  @Test
  void rejects_content_exceeding_max_length() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore(10, 4);

    assertThatThrownBy(() -> store.register("run-1", "generate", "agent.json", "12345"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds max content length");
  }

  @Test
  void clear_releases_only_the_named_run() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    store.register("run-1", "generate", "agent.json", "a");
    store.register("run-2", "generate", "agent.json", "b");

    store.clear("run-1");

    assertThat(store.find("run-1", "agent.json")).isEmpty();
    assertThat(store.find("run-2", "agent.json")).contains("b");
  }

  @Test
  void clear_is_idempotent_for_unknown_run() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();

    store.clear("never-seen");

    assertThat(store.artifacts("never-seen")).isEmpty();
  }

  @Test
  void constructor_rejects_non_positive_bounds() {
    assertThatThrownBy(() -> new InMemoryGeneratedArtifactStore(0, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxArtifactsPerRun");
    assertThatThrownBy(() -> new InMemoryGeneratedArtifactStore(10, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxContentLength");
  }
}
