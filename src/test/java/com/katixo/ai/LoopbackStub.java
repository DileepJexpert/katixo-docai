package com.katixo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * A tiny HTTP stub bound to 127.0.0.1 that imitates Ollama ({@code /api/generate}, {@code /api/ps})
 * and the OCR sidecar ({@code /ocr}, {@code /health}). Built on the JDK's own HttpServer so the
 * offline end-to-end test needs no extra dependency and provably talks to loopback only.
 */
final class LoopbackStub {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;

    private LoopbackStub(HttpServer server) {
        this.server = server;
    }

    static LoopbackStub start(String cannedExtractionJson, double ocrConfidence) {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // --- OCR sidecar ---
            server.createContext("/health", ex -> respondJson(ex, Map.of("status", "ok")));
            server.createContext("/ocr", ex -> {
                drain(ex);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("text", "stub ocr text");
                body.put("blocks", List.of());
                body.put("confidence", ocrConfidence);
                respondJson(ex, body);
            });

            // --- Ollama ---
            server.createContext("/api/ps", ex -> respondJson(ex,
                    Map.of("models", List.of(Map.of("name", "qwen2.5:7b-instruct", "model", "qwen2.5:7b-instruct")))));
            server.createContext("/api/generate", ex -> {
                drain(ex);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("response", cannedExtractionJson);
                body.put("done", true);
                respondJson(ex, body);
            });

            server.start();
            return new LoopbackStub(server);
        } catch (IOException e) {
            throw new RuntimeException("Could not start loopback stub", e);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void stop() {
        server.stop(0);
    }

    private static void drain(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            is.readAllBytes();
        }
    }

    private static void respondJson(HttpExchange ex, Map<String, Object> body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
