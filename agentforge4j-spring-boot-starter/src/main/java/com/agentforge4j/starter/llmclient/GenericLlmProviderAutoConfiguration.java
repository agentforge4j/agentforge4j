// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient;

import com.agentforge4j.starter.BootstrapAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Generic provider wiring: replaces the per-provider auto-configurations with a single
 * {@code LlmClientConfigurationAdapter}-driven registrar, so a new {@code ServiceLoader}-registered provider is
 * configurable through the starter without new starter code. Each provider owns its property-to-neutral mapping in its
 * own module; the starter holds no per-provider logic. The {@code agentforge4j.llm.<providerId>.*} property namespace is
 * unchanged.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@Import(GenericLlmProviderRegistrar.class)
public class GenericLlmProviderAutoConfiguration {

}
