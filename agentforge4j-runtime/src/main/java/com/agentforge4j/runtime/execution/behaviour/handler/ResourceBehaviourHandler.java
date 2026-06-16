// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.behaviour.BehaviourHandler;
import com.agentforge4j.runtime.execution.behaviour.resource.ResourceResolver;
import com.agentforge4j.util.Validate;

public final class ResourceBehaviourHandler implements BehaviourHandler<ResourceBehaviour> {

  private static final System.Logger LOG = System.getLogger(
      ResourceBehaviourHandler.class.getName());

  private final ResourceResolver resourceResolver;

  public ResourceBehaviourHandler(ResourceResolver resourceResolver) {
    this.resourceResolver = Validate.notNull(resourceResolver, "resourceResolver must not be null");
  }

  @Override
  public Class<ResourceBehaviour> behaviourType() {
    return ResourceBehaviour.class;
  }

  @Override
  public ExecutionOutcome handle(StepDefinition step,
      ResourceBehaviour behaviour,
      ExecutionContext executionContext) {
    LOG.log(System.Logger.Level.DEBUG,
        "Resource behaviour start stepId={0}, resourcePath={1}, contextKey={2}",
        step.stepId(), behaviour.resourcePath(), behaviour.contextKey());
    String content = resolveResource(behaviour.resourcePath());
    executionContext.getState().putContextValue(
        behaviour.contextKey(),
        new StringContextValue(content));

    return ExecutionOutcome.COMPLETED;
  }

  private String resolveResource(String resourcePath) {
    try {
      return resourceResolver.resolve(resourcePath);
    } catch (IllegalArgumentException exception) {
      String identifier = Integer.toHexString(resourcePath == null ? 0 : resourcePath.hashCode());
      LOG.log(System.Logger.Level.INFO,
          "Resource request rejected reason={0}, pathId={1}",
          exception.getMessage(), identifier);
      LOG.log(System.Logger.Level.DEBUG, "Rejected resourcePath={0}", resourcePath);
      throw exception;
    }
  }
}
