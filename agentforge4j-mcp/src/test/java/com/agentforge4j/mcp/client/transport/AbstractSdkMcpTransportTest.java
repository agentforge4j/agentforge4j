// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@link AbstractSdkMcpTransport}'s handling of a handshake that fails after the underlying
 * SDK transport has already connected. Regression test for a leak where {@code start()} discarded a
 * successfully-connected SDK client without closing it when the subsequent {@code initialize()}
 * handshake failed, because the client was only assigned to the {@code client} field on success — for
 * {@link StdioTransport} this left an orphaned server subprocess running.
 */
class AbstractSdkMcpTransportTest {

  private static final McpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(new ObjectMapper());

  @Test
  void closesTheSdkTransportWhenTheHandshakeFailsAfterConnectSucceeds() {
    FailingHandshakeTransport sdkTransport = new FailingHandshakeTransport();
    TestTransport transport = new TestTransport(JSON_MAPPER, Duration.ofSeconds(5), sdkTransport);

    assertThatThrownBy(transport::start).isInstanceOf(RuntimeException.class);

    assertThat(sdkTransport.connected).isTrue();
    assertThat(sdkTransport.closed).isTrue();
    assertThat(transport.isReady()).isFalse();
  }

  private static final class TestTransport extends AbstractSdkMcpTransport {

    private final McpClientTransport sdkTransport;

    TestTransport(McpJsonMapper jsonMapper, Duration requestTimeout, McpClientTransport sdkTransport) {
      super(jsonMapper, requestTimeout);
      this.sdkTransport = sdkTransport;
    }

    @Override
    protected McpClientTransport createSdkTransport() {
      return sdkTransport;
    }
  }

  /**
   * Fake SDK transport whose {@code connect()} succeeds (simulating an already-live subprocess or
   * socket) but whose first outbound message — the {@code initialize} request — fails, simulating a
   * handshake that never completes.
   */
  private static final class FailingHandshakeTransport implements McpClientTransport {

    private volatile boolean connected;
    private volatile boolean closed;

    @Override
    public Mono<Void> connect(
        Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
      connected = true;
      return Mono.empty();
    }

    @Override
    public Mono<Void> closeGracefully() {
      closed = true;
      return Mono.empty();
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
      return Mono.error(new IllegalStateException("simulated handshake failure"));
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
      throw new UnsupportedOperationException("not needed for this test");
    }
  }
}
