package dev.photonmcp.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.photonmcp.McpConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class McpServer implements AutoCloseable {
    public static final List<String> PROTOCOLS = List.of("2025-11-25", "2025-06-18", "2025-03-26");
    private final ToolBackend backend;
    private final ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();
    private HttpServer server;
    private ExecutorService executor;
    private volatile String lastTool = "";
    private volatile String lastError = "";
    private volatile String lastRequest = "";
    private McpConfig config;

    private record Client(String name, long lastSeen) {}

    public McpServer(ToolBackend backend) {
        this.backend = backend;
    }

    public synchronized void start(McpConfig nextConfig) throws IOException {
        close();
        config = nextConfig;
        var next = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        next.setExecutor(executor);
        next.createContext("/mcp", this::handle);
        next.start();
        server = next;
        lastError = "";
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public JsonObject status() {
        var status = new JsonObject();
        status.addProperty("running", isRunning());
        status.addProperty("endpoint", config == null ? "" : config.endpoint());
        var clientNames = new com.google.gson.JsonArray();
        clients.values().forEach(client -> {
            if (System.currentTimeMillis() - client.lastSeen < TimeUnit.MINUTES.toMillis(5)) {
                clientNames.add(client.name);
            }
        });
        status.add("recentClients", clientNames);
        status.addProperty("requests", requestCount.get());
        status.addProperty("lastTool", lastTool);
        status.addProperty("lastRequest", lastRequest);
        status.addProperty("lastError", lastError);
        return status;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Accept, Mcp-Session-Id, MCP-Protocol-Version");
            exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "Mcp-Session-Id");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS");
            if (!exchange.getRequestURI().getPath().equals("/mcp")) {
                send(exchange, 404, null);
                return;
            }
            var session = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            switch (exchange.getRequestMethod()) {
                case "OPTIONS" -> send(exchange, 204, null);
                case "DELETE" -> {
                    if (session != null) clients.remove(session);
                    send(exchange, 204, null);
                }
                case "GET" -> {
                    exchange.getResponseHeaders().set("Allow", "POST, DELETE, OPTIONS");
                    send(exchange, 405, null);
                }
                case "POST" -> post(exchange, session);
                default -> send(exchange, 405, null);
            }
        } catch (IOException exception) {
            lastError = exception.getMessage();
        }
    }

    private void post(HttpExchange exchange, String session) throws IOException {
        JsonObject request;
        try {
            request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException exception) {
            send(exchange, 400, error(null, -32700, "Invalid JSON-RPC JSON"));
            return;
        }
        var id = request.get("id");
        if (!request.has("method") || !request.get("method").isJsonPrimitive()
                || !request.getAsJsonPrimitive("method").isString() || !request.has("jsonrpc")
                || !request.get("jsonrpc").isJsonPrimitive() || !request.get("jsonrpc").getAsString().equals("2.0")) {
            send(exchange, 400, error(id, -32600, "Invalid JSON-RPC request"));
            return;
        }
        var method = request.get("method").getAsString();
        if (request.has("params") && !request.get("params").isJsonObject()) {
            send(exchange, 400, error(id, -32602, "params must be an object"));
            return;
        }
        var params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
        requestCount.incrementAndGet();
        lastRequest = Instant.now().toString();
        if (session != null) {
            clients.computeIfPresent(session, (key, client) -> new Client(client.name, System.currentTimeMillis()));
        }
        if (id == null) {
            send(exchange, 202, null);
            return;
        }
        try {
            JsonObject result;
            switch (method) {
                case "initialize" -> {
                    var requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : "";
                    result = new JsonObject();
                    result.addProperty("protocolVersion", PROTOCOLS.contains(requested) ? requested : PROTOCOLS.getFirst());
                    result.add("capabilities", JsonParser.parseString("{\"tools\":{\"listChanged\":false}}"));
                    result.add("serverInfo", JsonParser.parseString("{\"name\":\"photon-mcp\",\"version\":\"0.2.0\"}"));
                    result.addProperty("instructions", "Open the in-game Photon editor. Use photon_get_editor_state, photon_list_object_types and photon_describe_object_type before editing. Inspect defaults instead of guessing keys. JSON patches deep-merge, arrays replace. Edits support editor undo; screenshot returns PNG image content. No arbitrary code execution tool.");
                    var clientName = params.has("clientInfo") ? params.getAsJsonObject("clientInfo").get("name").getAsString() : "agent";
                    var nextSession = UUID.randomUUID().toString();
                    clients.put(nextSession, new Client(clientName, System.currentTimeMillis()));
                    exchange.getResponseHeaders().set("Mcp-Session-Id", nextSession);
                }
                case "ping" -> result = new JsonObject();
                case "tools/list" -> {
                    result = new JsonObject();
                    result.add("tools", backend.tools());
                }
                case "tools/call" -> {
                    lastTool = params.get("name").getAsString();
                    var arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();
                    var pending = backend.call(lastTool, arguments);
                    try {
                        result = pending.get(30, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                        pending.cancel(false);
                        throw exception;
                    }
                }
                default -> {
                    send(exchange, 200, error(id, -32601, "Unknown method: " + method));
                    return;
                }
            }
            var response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", id);
            response.add("result", result);
            send(exchange, 200, response);
        } catch (Exception exception) {
            lastError = exception.getCause() == null ? exception.toString() : exception.getCause().toString();
            if (method.equals("tools/call")) {
                var response = new JsonObject();
                response.addProperty("jsonrpc", "2.0");
                response.add("id", id);
                response.add("result", ToolResults.failure(lastError));
                send(exchange, 200, response);
            } else {
                send(exchange, 200, error(id, -32602, lastError));
            }
        }
    }

    private static JsonObject error(JsonElement id, int code, String message) {
        var response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        var error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    private static void send(HttpExchange exchange, int status, JsonObject payload) throws IOException {
        if (payload == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        var bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public synchronized void close() {
        if (server != null) server.stop(0);
        server = null;
        clients.clear();
        if (executor != null) executor.shutdownNow();
        executor = null;
    }
}
