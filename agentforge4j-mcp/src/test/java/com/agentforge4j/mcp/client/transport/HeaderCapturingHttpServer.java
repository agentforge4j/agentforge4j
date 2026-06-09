package com.agentforge4j.mcp.client.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal loopback HTTP/1.1 server for transport tests. Captures the headers of every request it
 * receives (header names lowercased) and responds {@code 200 {}} so the MCP SDK client makes its
 * request before failing to complete the handshake. No external test dependencies.
 */
final class HeaderCapturingHttpServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final Thread thread;
  private final List<Map<String, String>> capturedHeaders = new CopyOnWriteArrayList<>();
  private volatile boolean running = true;

  HeaderCapturingHttpServer() {
    try {
      this.serverSocket = new ServerSocket(0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    this.thread = new Thread(this::serveLoop, "mcp-header-capturing-loopback");
    this.thread.setDaemon(true);
    this.thread.start();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/mcp";
  }

  List<Map<String, String>> capturedHeaders() {
    return List.copyOf(capturedHeaders);
  }

  private void serveLoop() {
    while (running) {
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(10_000);
        Map<String, String> headers = readHeaders(socket.getInputStream());
        if (headers != null) {
          capturedHeaders.add(headers);
        }
        OutputStream out = socket.getOutputStream();
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 200 OK\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Connection: close\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
      } catch (IOException e) {
        if (!running) {
          return;
        }
      }
    }
  }

  private static Map<String, String> readHeaders(InputStream in) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    byte[] buf = new byte[2048];
    while (sink.size() < 65_536) {
      int n = in.read(buf);
      if (n < 0) {
        break;
      }
      sink.write(buf, 0, n);
      String raw = sink.toString(StandardCharsets.US_ASCII);
      int sep = raw.indexOf("\r\n\r\n");
      if (sep < 0) {
        continue;
      }
      Map<String, String> headers = new LinkedHashMap<>();
      String[] lines = raw.substring(0, sep).split("\r\n");
      for (int i = 1; i < lines.length; i++) {
        int colon = lines[i].indexOf(':');
        if (colon > 0) {
          headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
              lines[i].substring(colon + 1).trim());
        }
      }
      return headers;
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
