// SPDX-License-Identifier: Apache-2.0
/**
 * A downstream named module that depends on the testkit alone. It deliberately does <em>not</em>
 * declare {@code requires} for {@code agentforge4j.llm.fake}, {@code agentforge4j.llm.api} or
 * {@code agentforge4j.runtime}; if {@code agentforge4j.testkit} did not re-export them transitively,
 * {@code TestkitModuleConsumer} would fail to compile. Compiling this module is the contract guard.
 */
module agentforge4j.testkit.consumer {
  requires agentforge4j.testkit;

  exports com.agentforge4j.testkit.consumer;
}
