// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal one-shot loopback HTTP/1.1 server for offline provider-smoke verification — no extra test
 * dependencies, no network. Binds an ephemeral port, accepts a single request, captures its body,
 * and replies with a canned {@code 200} response. Copied from the per-provider loopback pattern used
 * by the OpenAI-compatible client integration tests.
 */
public final class LoopbackHttpServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final Thread thread;
  private final byte[] responseBodyBytes;
  private final AtomicReference<String> capturedRequestBodyUtf8 = new AtomicReference<>();
  private final AtomicReference<String> capturedRequestUtf8 = new AtomicReference<>();
  private volatile IOException serveFailure;

  /**
   * Starts a one-shot server that replies to the next request with {@code 200} and the given body.
   *
   * @param responseBody the canned response body (UTF-8); must not be {@code null}
   */
  public LoopbackHttpServer(String responseBody) {
    this.responseBodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
    try {
      // Bind explicitly to the loopback interface so the server actually honours the "loopback"
      // guarantee its name and baseUri() advertise (a wildcard bind would listen on every interface).
      this.serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    this.thread = new Thread(this::serveOnce, "verification-loopback-http");
    this.thread.setDaemon(true);
    this.thread.start();
  }

  /**
   * The base URI (scheme + loopback host + ephemeral port) this server is listening on.
   *
   * @return the base URI, without a trailing slash
   */
  public URI baseUri() {
    return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort());
  }

  /**
   * The request body the single served request carried, or {@code null} if nothing was served yet.
   *
   * @return the captured request body (UTF-8)
   */
  public String capturedRequestBody() {
    return capturedRequestBodyUtf8.get();
  }

  /**
   * Returns the value of the named request header (case-insensitive) from the single served request,
   * or {@code null} if the request carried no such header or nothing was served yet. Lets a caller
   * assert credential/option propagation (for example the {@code Authorization} header).
   *
   * @param name the header name to look up
   *
   * @return the header value, trimmed, or {@code null}
   */
  public String capturedHeader(String name) {
    String request = capturedRequestUtf8.get();
    if (request == null) {
      return null;
    }
    int sep = request.indexOf("\r\n\r\n");
    String headerSection = sep >= 0 ? request.substring(0, sep) : request;
    String prefix = name.toLowerCase(Locale.ROOT) + ":";
    for (String line : headerSection.split("\r\n")) {
      if (line.toLowerCase(Locale.ROOT).startsWith(prefix)) {
        return line.substring(line.indexOf(':') + 1).trim();
      }
    }
    return null;
  }

  private void serveOnce() {
    try (Socket socket = serverSocket.accept()) {
      socket.setSoTimeout(60_000);
      String requestText = drainHttpRequest(socket.getInputStream());
      capturedRequestUtf8.set(requestText);
      int sep = requestText.indexOf("\r\n\r\n");
      if (sep >= 0) {
        capturedRequestBodyUtf8.set(requestText.substring(sep + 4));
      }
      String headers = "HTTP/1.1 200 OK\r\n"
          + "Content-Length: " + responseBodyBytes.length + "\r\n"
          + "Connection: close\r\n\r\n";
      OutputStream out = socket.getOutputStream();
      out.write(headers.getBytes(StandardCharsets.US_ASCII));
      out.write(responseBodyBytes);
      out.flush();
    } catch (IOException e) {
      serveFailure = e;
    } finally {
      try {
        serverSocket.close();
      } catch (IOException ignored) {
        // already closing down
      }
    }
  }

  private static String drainHttpRequest(InputStream in) throws IOException {
    byte[] buf = new byte[4096];
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
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
    thread.join(30_000);
    if (serveFailure != null) {
      throw serveFailure;
    }
  }
}
