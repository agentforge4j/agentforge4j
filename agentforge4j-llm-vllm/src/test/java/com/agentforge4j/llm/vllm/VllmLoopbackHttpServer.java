// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal loopback HTTP/1.1 server for deterministic integration tests (no extra test dependencies).
 */
final class VllmLoopbackHttpServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final Thread thread;
  private final int statusCode;
  private final byte[] responseBodyBytes;
  private final AtomicReference<String> capturedRequestBodyUtf8;
  private final AtomicReference<String> capturedFullRequestUtf8;
  private final long responseDelayMillis;
  private volatile IOException serveFailure;

  VllmLoopbackHttpServer(int statusCode, String responseBody) {
    this(statusCode, responseBody, null, null, 0L);
  }

  VllmLoopbackHttpServer(int statusCode, String responseBody,
      AtomicReference<String> capturedRequestBodyUtf8) {
    this(statusCode, responseBody, capturedRequestBodyUtf8, null, 0L);
  }

  VllmLoopbackHttpServer(int statusCode, String responseBody,
      AtomicReference<String> capturedRequestBodyUtf8,
      AtomicReference<String> capturedFullRequestUtf8) {
    this(statusCode, responseBody, capturedRequestBodyUtf8, capturedFullRequestUtf8, 0L);
  }

  /**
   * @param responseDelayMillis sleep this long after reading the request, before sending the response
   */
  VllmLoopbackHttpServer(int statusCode, String responseBody,
      AtomicReference<String> capturedRequestBodyUtf8,
      AtomicReference<String> capturedFullRequestUtf8,
      long responseDelayMillis) {
    this.statusCode = statusCode;
    this.responseBodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
    this.capturedRequestBodyUtf8 = capturedRequestBodyUtf8;
    this.capturedFullRequestUtf8 = capturedFullRequestUtf8;
    this.responseDelayMillis = responseDelayMillis;
    try {
      this.serverSocket = new ServerSocket(0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    this.thread = new Thread(this::serveOnce, "vllm-loopback-http");
    this.thread.setDaemon(true);
    this.thread.start();
  }

  URI baseUri() {
    return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/v1/chat/completions");
  }

  private void serveOnce() {
    try (Socket socket = serverSocket.accept()) {
      socket.setSoTimeout(60_000);
      String requestText = drainHttpRequest(socket.getInputStream());
      if (capturedFullRequestUtf8 != null) {
        capturedFullRequestUtf8.set(requestText);
      }
      if (capturedRequestBodyUtf8 != null) {
        int sep = requestText.indexOf("\r\n\r\n");
        if (sep >= 0) {
          capturedRequestBodyUtf8.set(requestText.substring(sep + 4));
        }
      }
      if (responseDelayMillis > 0) {
        Thread.sleep(responseDelayMillis);
      }
      try {
        String reasonPhrase = switch (statusCode) {
          case 200 -> "OK";
          case 400 -> "Bad Request";
          case 503 -> "Service Unavailable";
          default -> "Error";
        };
        String headers = "HTTP/1.1 " + statusCode + " " + reasonPhrase + "\r\n"
            + "Content-Length: " + responseBodyBytes.length + "\r\n"
            + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(responseBodyBytes);
        out.flush();
      } catch (IOException ignored) {
        // Client may close first (e.g. HttpClient request timeout); ignore broken pipe / abort.
      }
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      serveFailure = e instanceof IOException ioe ? ioe : new IOException(e);
    } finally {
      try {
        serverSocket.close();
      } catch (IOException ignored) {
      }
    }
  }

  private static String drainHttpRequest(InputStream in) throws IOException {
    byte[] buf = new byte[4096];
    var captured = new ByteArrayOutputStream();
    int total = 0;
    while (total < 262_144) {
      int n = in.read(buf);
      if (n < 0) {
        break;
      }
      captured.write(buf, 0, n);
      total += n;
      byte[] all = captured.toByteArray();
      String raw = new String(all, StandardCharsets.US_ASCII);
      int sep = raw.indexOf("\r\n\r\n");
      if (sep < 0) {
        continue;
      }
      int contentLength = 0;
      String headerSection = raw.substring(0, sep);
      for (String line : headerSection.split("\r\n")) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.startsWith("content-length:")) {
          contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
        }
      }
      int alreadyInBody = all.length - (sep + 4);
      int remaining = contentLength - alreadyInBody;
      while (remaining > 0) {
        int toRead = Math.min(buf.length, remaining);
        int r = in.read(buf, 0, toRead);
        if (r < 0) {
          break;
        }
        captured.write(buf, 0, r);
        remaining -= r;
      }
      return new String(captured.toByteArray(), StandardCharsets.UTF_8);
    }
    return "";
  }

  @Override
  public void close() throws Exception {
    serverSocket.close();
    thread.join(15_000);
    if (serveFailure != null) {
      throw serveFailure;
    }
  }
}
