package com.agentforge4j.core.workflow.context;

/**
 * The reserved root key under which a rendered prompt isolates untrusted (user- or external-tool-
 * supplied) context entries. Single source of truth shared by the runtime renderer that builds the
 * envelope and the framework system-rules text that references it for the model, so the two can never
 * drift apart.
 */
public final class UntrustedInputEnvelope {

  /**
   * Reserved render-envelope key. A trusted context key colliding with this name is rejected by the
   * renderer.
   */
  public static final String KEY = "untrustedUserInput";

  private UntrustedInputEnvelope() {
  }
}
