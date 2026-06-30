// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.spi.validation.ArtifactValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link AgentBundleArtifactValidatorFactory}: it produces a working {@code agent-bundle} validator bound to the
 * supplied mapper, and is registered in {@code META-INF/services} so a consumer module (e.g. {@code bootstrap}) can
 * discover the built-in validator via {@code ServiceLoader} rather than a hardcoded registration.
 *
 * <p>The actual {@code ServiceLoader} discovery is exercised end-to-end from the consumer side in
 * {@code BuiltInArtifactValidatorDiscoveryTest} (bootstrap), which is where the {@code uses} declaration lives; this
 * provider-side test only asserts the registration manifest is present and correct.
 */
class AgentBundleArtifactValidatorFactoryTest {

  @Test
  void createProducesAgentBundleValidatorBoundToTheMapper() {
    ArtifactValidator validator =
        new AgentBundleArtifactValidatorFactory().create(new ObjectMapper());

    assertThat(validator).isInstanceOf(AgentBundleArtifactValidator.class);
    assertThat(validator.validatorId()).isEqualTo(AgentBundleArtifactValidator.VALIDATOR_ID);
  }

  @Test
  void factoryIsRegisteredInServicesManifest() throws IOException {
    String resource = "/META-INF/services/" + ArtifactValidatorFactory.class.getName();
    try (InputStream in = ArtifactValidatorFactory.class.getResourceAsStream(resource)) {
      assertThat(in).as("services manifest %s must exist", resource).isNotNull();
      String contents = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(contents)
          .as("services manifest must register the agent-bundle factory")
          .contains(AgentBundleArtifactValidatorFactory.class.getName());
    }
  }
}
