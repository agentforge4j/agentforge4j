// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Defines a workflow: its steps, blueprints, and artifacts. Instances are immutable and validated
 * at construction time.
 *
 * <p>Workflow configuration controls the execution flow; AI/model output provides commands or
 * content but does not own runtime flow control.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowDefinition(
    String id,
    String name,
    String description,
    String author,
    String contact,
    String version,
     // Stable server-side identity for cost estimates (optional in workflow JSON). Do not use
     // {@link #id} as the long-term estimate key; ids may be renamed when forking or editing drafts.
    String uuid,
    WorkflowSource source,
    WorkflowLifecycle lifecycle,
    Map<String, ArtifactDefinition> artifacts,
    Map<String, BlueprintDefinition> blueprints,
    List<Executable> steps,
     // Central, self-targeting requirement declarations; {@code null} or absent becomes an empty
     // list. Opaque to {@code core}: structurally validated at load and asserted at the run-start
     // checkpoint, but never interpreted here.
    List<WorkflowRequirement> requirements,
     // Structured ledger declarations; {@code null} or absent becomes an empty list. Opaque to
     // {@code core}: schema refs resolve and merge rules apply at load and run time, not here.
    List<LedgerDefinition> ledgers
) implements Executable {

  public WorkflowDefinition {
    Validate.notBlank(id, "WorkflowDefinition id must not be blank");
    Validate.notBlank(name,
        "WorkflowDefinition name must not be blank for workflow: %s".formatted(id));
    uuid = StringUtils.defaultIfBlank(uuid, null);
    source = source != null ? source : WorkflowSource.CUSTOM;
    lifecycle = lifecycle != null ? lifecycle : WorkflowLifecycle.ACTIVE;
    artifacts = artifacts != null ? Map.copyOf(artifacts) : Map.of();
    blueprints = blueprints != null ? Map.copyOf(blueprints) : Map.of();
    Validate.notEmpty(steps,
        "WorkflowDefinition steps must not be empty for workflow: %s".formatted(id));
    steps = List.copyOf(steps);
    requirements = requirements != null ? List.copyOf(requirements) : List.of();
    ledgers = ledgers != null ? List.copyOf(ledgers) : List.of();
  }

  /**
   * Returns a new {@link Builder} for assembling a {@link WorkflowDefinition} without positional
   * arguments. Optional fields may be left unset; the required fields are validated when
   * {@link Builder#build()} is called.
   *
   * @return new builder; never {@code null}
   */
  public static Builder builder() {
    return new Builder();
  }

  public static WorkflowDefinition duplicate(WorkflowDefinition workflowDefinition,
      Map<String, BlueprintDefinition> blueprints, List<Executable> loadedStepPrompts) {
    return new WorkflowDefinition(
        workflowDefinition.id(),
        workflowDefinition.name(),
        workflowDefinition.description(),
        workflowDefinition.author(),
        workflowDefinition.contact(),
        workflowDefinition.version(),
        workflowDefinition.uuid(),
        workflowDefinition.source(),
        workflowDefinition.lifecycle(),
        workflowDefinition.artifacts(),
        blueprints,
        loadedStepPrompts,
        workflowDefinition.requirements(),
        workflowDefinition.ledgers());
  }

  public static WorkflowDefinition duplicate(WorkflowDefinition workflowDefinition,
      WorkflowSource source,
      WorkflowLifecycle lifecycle, Map<String, ArtifactDefinition> artifacts,
      Map<String, BlueprintDefinition> blueprints, List<Executable> loadedStepPrompts) {
    return new WorkflowDefinition(
        workflowDefinition.id(),
        workflowDefinition.name(),
        workflowDefinition.description(),
        workflowDefinition.author(),
        workflowDefinition.contact(),
        workflowDefinition.version(),
        workflowDefinition.uuid(),
        source,
        lifecycle,
        artifacts,
        blueprints,
        loadedStepPrompts,
        workflowDefinition.requirements(),
        workflowDefinition.ledgers());
  }

  /**
   * Fluent builder for {@link WorkflowDefinition}. Lets callers omit optional fields without
   * passing a run of trailing {@code null}s to the canonical constructor. Validation is deferred to
   * {@link #build()}, which delegates to the canonical constructor.
   */
  public static final class Builder {

    private String id;
    private String name;
    private String description;
    private String author;
    private String contact;
    private String version;
    private String uuid;
    private WorkflowSource source;
    private WorkflowLifecycle lifecycle;
    private Map<String, ArtifactDefinition> artifacts;
    private Map<String, BlueprintDefinition> blueprints;
    private List<Executable> steps;
    private List<WorkflowRequirement> requirements;
    private List<LedgerDefinition> ledgers;

    private Builder() {
      // obtain via WorkflowDefinition.builder()
    }

    /**
     * Sets the non-blank workflow id.
     *
     * @param id non-blank workflow id
     *
     * @return this builder
     */
    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    /**
     * Sets the non-blank display or logical workflow name.
     *
     * @param name non-blank name
     *
     * @return this builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the optional workflow description.
     *
     * @param description description text
     *
     * @return this builder
     */
    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    /**
     * Sets the optional workflow author.
     *
     * @param author author name
     *
     * @return this builder
     */
    public Builder withAuthor(String author) {
      this.author = author;
      return this;
    }

    /**
     * Sets the optional author contact.
     *
     * @param contact contact information
     *
     * @return this builder
     */
    public Builder withContact(String contact) {
      this.contact = contact;
      return this;
    }

    /**
     * Sets the optional workflow version string.
     *
     * @param version version string
     *
     * @return this builder
     */
    public Builder withVersion(String version) {
      this.version = version;
      return this;
    }

    /**
     * Sets the stable server-side identity used for cost estimates.
     *
     * @param uuid stable identity, or {@code null} if not yet assigned
     *
     * @return this builder
     */
    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * Sets the workflow source.
     *
     * @param source workflow source; {@code null} becomes {@link WorkflowSource#CUSTOM}
     *
     * @return this builder
     */
    public Builder withSource(WorkflowSource source) {
      this.source = source;
      return this;
    }

    /**
     * Sets the workflow lifecycle state.
     *
     * @param lifecycle lifecycle state; {@code null} becomes {@link WorkflowLifecycle#ACTIVE}
     *
     * @return this builder
     */
    public Builder withLifecycle(WorkflowLifecycle lifecycle) {
      this.lifecycle = lifecycle;
      return this;
    }

    /**
     * Sets the workflow's artifact definitions.
     *
     * @param artifacts artifact definitions keyed by id; {@code null} becomes an empty map
     *
     * @return this builder
     */
    public Builder withArtifacts(Map<String, ArtifactDefinition> artifacts) {
      this.artifacts = artifacts;
      return this;
    }

    /**
     * Sets the workflow's blueprint definitions.
     *
     * @param blueprints blueprint definitions keyed by id; {@code null} becomes an empty map
     *
     * @return this builder
     */
    public Builder withBlueprints(Map<String, BlueprintDefinition> blueprints) {
      this.blueprints = blueprints;
      return this;
    }

    /**
     * Sets the workflow's steps.
     *
     * @param steps non-empty list of executable steps
     *
     * @return this builder
     */
    public Builder withSteps(List<Executable> steps) {
      this.steps = steps;
      return this;
    }

    /**
     * Sets the workflow's self-targeting requirement declarations.
     *
     * @param requirements requirement declarations; {@code null} becomes an empty list
     *
     * @return this builder
     */
    public Builder withRequirements(List<WorkflowRequirement> requirements) {
      this.requirements = requirements;
      return this;
    }

    /**
     * Sets the workflow's structured ledger declarations.
     *
     * @param ledgers ledger declarations; {@code null} becomes an empty list
     *
     * @return this builder
     */
    public Builder withLedgers(List<LedgerDefinition> ledgers) {
      this.ledgers = ledgers;
      return this;
    }

    /**
     * Builds the validated {@link WorkflowDefinition}.
     *
     * @return immutable workflow definition; never {@code null}
     */
    public WorkflowDefinition build() {
      return new WorkflowDefinition(id, name, description, author, contact, version, uuid, source,
          lifecycle, artifacts, blueprints, steps, requirements, ledgers);
    }
  }
}
