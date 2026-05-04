package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
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
    /**
     * Stable server-side identity for cost estimates (optional in workflow JSON). Do not use
     * {@link #id} as the long-term estimate key; ids may be renamed when forking or editing drafts.
     */
    String uuid,
    WorkflowSource source,
    WorkflowLifecycle lifecycle,
    Map<String, ArtifactDefinition> artifacts,
    Map<String, BlueprintDefinition> blueprints,
    List<Executable> steps
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
  }
}
