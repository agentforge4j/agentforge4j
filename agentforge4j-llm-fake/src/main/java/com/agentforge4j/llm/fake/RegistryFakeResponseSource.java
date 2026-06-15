package com.agentforge4j.llm.fake;

import com.agentforge4j.util.Validate;

/**
 * The read face of a {@link FakeRunLifecycle}: resolves responses against scripts explicitly registered per run. The
 * same {@link FakeRunLifecycle} instance is the write/lifecycle face (register/deregister/sweep) handed to the test
 * runner or demo flow, so the client and the registration API share one per-run store.
 *
 * <p>When a run has no registered script, {@link #nextResponse(FakeInvocation)} returns
 * {@link FakeResolution.RunNotScripted}.
 */
public final class RegistryFakeResponseSource implements FakeResponseSource {

  private final FakeRunLifecycle lifecycle;

  /**
   * Creates a source backed by the given lifecycle store.
   *
   * @param lifecycle the shared per-run store; must not be {@code null}
   */
  public RegistryFakeResponseSource(FakeRunLifecycle lifecycle) {
    this.lifecycle = Validate.notNull(lifecycle, "lifecycle must not be null");
  }

  @Override
  public FakeResolution nextResponse(FakeInvocation invocation) {
    return lifecycle.nextResponse(invocation);
  }
}
