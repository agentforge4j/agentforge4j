package com.agentforge4j.bootstrap.config;

/**
 * Reads {@code AGENTFORGE4J_*} environment variables and {@code agentforge4j.*} system properties,
 * normalises both to dot-form. Internal — not part of the public API.
 */
final class EnvVarConfigReader {

  private EnvVarConfigReader() {
    throw new UnsupportedOperationException("static utility");
  }
}
