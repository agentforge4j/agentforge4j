package com.agentforge4j.tools.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal loopback HTTP/1.1 server for deterministic tests (no extra test dependencies). Serves a
 * sequence of scripted responses (repeating the last once exhausted) and captures each request so
 * tests can assert request mapping and retry behaviour.
 */
final class LoopbackHttpServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final Thread thread;
  private final List<Response> responses;
  private final List<Captured> captured = new CopyOnWriteArrayList<>();
  private volatile boolean running = true;

  LoopbackHttpServer(Response... responses) {
    this.responses = List.of(responses);
    try {
      this.serverSocket = new ServerSocket(0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    this.thread = new Thread(this::serveLoop, "tools-http-loopback");
    this.thread.setDaemon(true);
    this.thread.start();
  }

  URI baseUri() {
    return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort());
  }

  List<Captured> captured() {
    return List.copyOf(captured);
  }

  private void serveLoop() {
    int index = 0;
    while (running) {
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(60_000);
        Captured request = readRequest(socket.getInputStream());
        if (request != null) {
          captured.add(request);
        }
        Response response = responses.get(Math.min(index, responses.size() - 1));
        index++;
        if (response.delayMillis() > 0) {
          Thread.sleep(response.delayMillis());
        }
        writeResponse(socket.getOutputStream(), response);
      } catch (IOException e) {
        if (running) {
          // socket closed during shutdown; ignore otherwise-benign accept failures
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static void writeResponse(OutputStream out, Response response) throws IOException {
    byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
    StringBuilder headers = new StringBuilder()
        .append("HTTP/1.1 ").append(response.status()).append(" X\r\n")
        .append("Content-Length: ").append(body.length).append("\r\n");
    if (response.contentType() != null) {
      headers.append("Content-Type: ").append(response.contentType()).append("\r\n");
    }
    headers.append("Connection: close\r\n\r\n");
    out.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
    out.write(body);
    out.flush();
  }

  private static Captured readRequest(InputStream in) throws IOException {
    byte[] buf = new byte[4096];
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    while (sink.size() < 1_048_576) {
      int n = in.read(buf);
      if (n < 0) {
        break;
      }
      sink.write(buf, 0, n);
      byte[] all = sink.toByteArray();
      String raw = new String(all, StandardCharsets.US_ASCII);
      int sep = raw.indexOf("\r\n\r\n");
      if (sep < 0) {
        continue;
      }
      String headerSection = raw.substring(0, sep);
      String[] lines = headerSection.split("\r\n");
      String[] requestLine = lines[0].split(" ");
      Map<String, String> headers = new LinkedHashMap<>();
      int contentLength = 0;
      for (int i = 1; i < lines.length; i++) {
        int colon = lines[i].indexOf(':');
        if (colon <= 0) {
          continue;
        }
        String name = lines[i].substring(0, colon).trim();
        String value = lines[i].substring(colon + 1).trim();
        headers.put(name, value);
        if (name.toLowerCase(Locale.ROOT).equals("content-length")) {
          contentLength = Integer.parseInt(value);
        }
      }
      int bodyStart = sep + 4;
      int alreadyHave = all.length - bodyStart;
      while (alreadyHave < contentLength) {
        int read = in.read(buf);
        if (read < 0) {
          break;
        }
        sink.write(buf, 0, read);
        alreadyHave += read;
      }
      byte[] full = sink.toByteArray();
      String body = new String(full, bodyStart, Math.min(contentLength, full.length - bodyStart),
          StandardCharsets.UTF_8);
      String method = requestLine.length > 0 ? requestLine[0] : "";
      String target = requestLine.length > 1 ? requestLine[1] : "";
      return new Captured(method, target, headers, body);
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

  /** A scripted response. */
  record Response(int status, String body, String contentType, long delayMillis) {

    static Response of(int status, String body, String contentType) {
      return new Response(status, body, contentType, 0L);
    }

    static Response json(int status, String body) {
      return new Response(status, body, "application/json", 0L);
    }
  }

  /** A captured request. */
  record Captured(String method, String target, Map<String, String> headers, String body) {
  }
}
