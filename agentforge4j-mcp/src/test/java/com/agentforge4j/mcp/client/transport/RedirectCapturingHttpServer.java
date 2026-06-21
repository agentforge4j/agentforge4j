// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal loopback HTTP/1.1 server for transport tests that records the request-line target of every
 * request and answers any request to {@code /mcp} with a {@code 302} redirect to {@code /redirected}.
 * Any other path gets {@code 200 {}}. A client that follows redirects would therefore request
 * {@code /redirected}; a client with {@code Redirect.NEVER} never does. No external test dependencies.
 */
final class RedirectCapturingHttpServer implements AutoCloseable {

  static final String REDIRECT_TARGET = "/redirected";

  private final ServerSocket serverSocket;
  private final Thread thread;
  private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
  private volatile boolean running = true;

  RedirectCapturingHttpServer() {
    try {
      this.serverSocket = new ServerSocket(0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    this.thread = new Thread(this::serveLoop, "mcp-redirect-capturing-loopback");
    this.thread.setDaemon(true);
    this.thread.start();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/mcp";
  }

  List<String> requestedPaths() {
    return List.copyOf(requestedPaths);
  }

  private void serveLoop() {
    while (running) {
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(10_000);
        String path = readRequestTarget(socket.getInputStream());
        if (path != null) {
          requestedPaths.add(path);
        }
        OutputStream out = socket.getOutputStream();
        if (path != null && path.startsWith("/mcp")) {
          String response = "HTTP/1.1 302 Found\r\n"
              + "Location: " + REDIRECT_TARGET + "\r\n"
              + "Content-Length: 0\r\n"
              + "Connection: close\r\n\r\n";
          out.write(response.getBytes(StandardCharsets.US_ASCII));
        } else {
          byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
          String response = "HTTP/1.1 200 OK\r\n"
              + "Content-Type: application/json\r\n"
              + "Content-Length: " + body.length + "\r\n"
              + "Connection: close\r\n\r\n";
          out.write(response.getBytes(StandardCharsets.US_ASCII));
          out.write(body);
        }
        out.flush();
      } catch (IOException e) {
        if (!running) {
          return;
        }
      }
    }
  }

  private static String readRequestTarget(InputStream in) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    byte[] buf = new byte[2048];
    while (sink.size() < 65_536) {
      int n = in.read(buf);
      if (n < 0) {
        break;
      }
      sink.write(buf, 0, n);
      String raw = sink.toString(StandardCharsets.US_ASCII);
      int sep = raw.indexOf("\r\n");
      if (sep < 0) {
        continue;
      }
      String[] requestLine = raw.substring(0, sep).split(" ");
      return requestLine.length >= 2 ? requestLine[1] : null;
    }
    return null;
  }

  @Override
  public void close() {
    running = false;
    try {
      serverSocket.close();
    } catch (IOException ignored) {
      // best effort
    }
    try {
      thread.join(5_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
