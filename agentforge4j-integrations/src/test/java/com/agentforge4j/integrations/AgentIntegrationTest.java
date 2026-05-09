package com.agentforge4j.integrations;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentIntegrationTest {

  @Test
  void executeReceivesOperationAndPayload() {
    AgentIntegration integration = new AgentIntegration() {
      @Override
      public String integrationId() {
        return "test";
      }

      @Override
      public String execute(String operation, Map<String, Object> payload) {
        return operation + ":" + payload.get("k");
      }
    };

    assertThat(integration.integrationId()).isEqualTo("test");
    assertThat(integration.execute("op", Map.of("k", 1))).isEqualTo("op:1");
  }

  @Test
  void executeReceivesEmptyPayloadWhenCallerPassesEmptyMap() {
    AgentIntegration integration = new AgentIntegration() {
      @Override
      public String integrationId() {
        return "id";
      }

      @Override
      public String execute(String operation, Map<String, Object> payload) {
        return operation + ":" + payload.size();
      }
    };

    assertThat(integration.execute("x", Map.of())).isEqualTo("x:0");
  }
}
