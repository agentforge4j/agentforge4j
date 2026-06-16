// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

/**
 * Supplies scripted responses to a {@link FakeLlmClient}. The implementation owns all per-run mutable state: it
 * atomically advances the ordinal counter for the invocation's {@code (runId, workflowId, stepId, agentId)} sequence,
 * looks up the resulting {@link FakeScriptKey}, and is responsible for per-run isolation and counter lifecycle
 * (eviction). The client is stateless and never computes ordinals.
 */
public interface FakeResponseSource {

  /**
   * Atomically advances the per-sequence ordinal for this invocation and resolves the response. Implementations must
   * keep counters isolated per {@link FakeInvocation#runId()} so concurrent runs never interleave. When no script is
   * available for the run, no counter is advanced and {@link FakeResolution.RunNotScripted} is returned.
   *
   * @param invocation the invocation identity (run, workflow, step, agent); never {@code null}
   *
   * @return the resolution: a found response, a missing key, or an unscripted run
   */
  FakeResolution nextResponse(FakeInvocation invocation);
}
