package com.agentforge4j.core.workflow.context;

/**
 * Origin of a {@link ContextValue} — who produced the content. Provenance is a multi-valued record of
 * origin; <em>trust</em> is a binary function over it ({@link #isTrusted()}). The runtime stamps
 * provenance at every context write so that untrusted content can be structurally isolated from
 * trusted content when the context is rendered into a prompt.
 *
 * <p>This is a pure framework concept: it carries no tenant, user, or authorization meaning.
 */
public enum ContextProvenance {

  /**
   * Content originating from an external actor — initial run input or {@code Input}-behaviour step
   * results. Untrusted.
   */
  USER_SUPPLIED,

  /**
   * Content produced by the framework or workflow definition — reserved runtime keys, author-authored
   * configuration, retry bookkeeping. Trusted.
   */
  SYSTEM_GENERATED,

  /**
   * Content returned by an external tool (for example an MCP or HTTP tool). Attacker-influenceable
   * (indirect prompt injection), so untrusted.
   */
  EXTERNAL_TOOL,

  /**
   * Content produced by an LLM and written back to context (for example a {@code SET_CONTEXT} command
   * or a SPAR round response). Rendered trusted for the current iteration and labelled distinctly so it
   * is never silently laundered into {@link #SYSTEM_GENERATED}; cross-step taint propagation is a
   * scheduled follow-on that reclassifies via this distinct value with no contract change.
   */
  LLM_GENERATED;

  /**
   * Whether content of this provenance is trusted when rendered into a prompt. Trusted provenances
   * render at the prompt root; untrusted provenances are isolated under the untrusted-input envelope.
   *
   * @return {@code true} for {@link #SYSTEM_GENERATED} and {@link #LLM_GENERATED}; {@code false} for
   * {@link #USER_SUPPLIED} and {@link #EXTERNAL_TOOL}
   */
  public boolean isTrusted() {
    return this == SYSTEM_GENERATED || this == LLM_GENERATED;
  }

  /**
   * Returns {@code provenance}, or the fail-safe {@link #USER_SUPPLIED} when {@code null}. The single
   * definition of the deserialization-seam default used by the {@code ContextValue} JSON factories.
   *
   * @param provenance a provenance, possibly {@code null}
   *
   * @return {@code provenance} when non-null, otherwise {@link #USER_SUPPLIED}
   */
  public static ContextProvenance orUserSupplied(ContextProvenance provenance) {
    return provenance == null ? USER_SUPPLIED : provenance;
  }
}
