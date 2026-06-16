// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards against reintroducing a compile dependency on the OpenAI provider module. */
class MistralModuleIndependenceTest {

  @Test
  void pom_must_not_depend_on_openai_provider_artifact() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));
    assertThat(pom).doesNotContain("<artifactId>agentforge4j-llm-openai</artifactId>");
  }
}
