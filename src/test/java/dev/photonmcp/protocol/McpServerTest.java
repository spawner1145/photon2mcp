package dev.photonmcp.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.photonmcp.McpConfig;
import dev.photonmcp.editor.ToolCatalog;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpServerTest {
    private McpServer server;
    private URI endpoint;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        server = new McpServer(new ToolBackend() {
            public JsonArray tools() { return ToolCatalog.tools(); }
            public CompletableFuture<JsonObject> call(String name, JsonObject arguments) {
                if (name.equals("throws")) return CompletableFuture.failedFuture(new IllegalArgumentException("bad setting"));
                return CompletableFuture.completedFuture(ToolResults.text(arguments));
            }
        });
        var config = new McpConfig("127.0.0.1", port);
        server.start(config);
        endpoint = URI.create(config.endpoint());
    }

    @AfterEach
    void stop() { server.close(); }

    private HttpResponse<String> post(String body, String session) throws Exception {
        var request = HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        if (session != null) request.header("Mcp-Session-Id", session);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @Test
    void initializesAndTracksNamedClientsWithoutAuthentication() throws Exception {
        var response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"clientInfo\":{\"name\":\"test-agent\"}}}", null);
        assertEquals(200, response.statusCode());
        assertEquals("2025-06-18", json(response).getAsJsonObject("result").get("protocolVersion").getAsString());
        assertTrue(response.headers().firstValue("Mcp-Session-Id").isPresent());
        assertEquals("test-agent", server.status().getAsJsonArray("recentClients").get(0).getAsString());
    }

    @Test
    void acceptsNotificationWithNoResponseBody() throws Exception {
        var response = post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", null);
        assertEquals(202, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void listsValidToolSchemas() throws Exception {
        var response = post("{\"jsonrpc\":\"2.0\",\"id\":\"list\",\"method\":\"tools/list\"}", null);
        var tools = json(response).getAsJsonObject("result").getAsJsonArray("tools");
        assertTrue(tools.size() >= 29);
        var names = new java.util.HashSet<String>();
        for (var entry : tools) {
            var tool = entry.getAsJsonObject();
            assertTrue(names.add(tool.get("name").getAsString()));
            var schema = tool.getAsJsonObject("inputSchema");
            assertEquals("object", schema.get("type").getAsString());
            for (var required : schema.getAsJsonArray("required")) {
                assertTrue(schema.getAsJsonObject("properties").has(required.getAsString()));
            }
        }
    }

    @Test
    void returnsStructuredContentAndPreservesStringRequestIds() throws Exception {
        var response = post("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"中文\"}}}", null);
        assertEquals("abc", json(response).get("id").getAsString());
        var result = json(response).getAsJsonObject("result");
        assertEquals("中文", result.getAsJsonObject("structuredContent").get("message").getAsString());
        assertFalse(result.get("isError").getAsBoolean());
    }

    @Test
    void toolFailureIsAnMcpToolErrorNotJsonRpcTransportError() throws Exception {
        var response = post("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"throws\"}}", null);
        assertEquals(200, response.statusCode());
        assertTrue(json(response).getAsJsonObject("result").get("isError").getAsBoolean());
        assertTrue(server.status().get("lastError").getAsString().contains("bad setting"));
    }

    @Test
    void malformedJsonAndUnknownMethodsUseRpcErrors() throws Exception {
        var malformed = post("not-json", null);
        assertEquals(400, malformed.statusCode());
        assertEquals(-32700, json(malformed).getAsJsonObject("error").get("code").getAsInt());
        var unknown = post("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"unknown\"}", null);
        assertEquals(-32601, json(unknown).getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void optionalGetStreamIsExplicitlyUnsupported() throws Exception {
        var response = client.send(HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }

    @Test
    void invalidMethodAndParamsAreReportedRatherThanDroppingConnection() throws Exception {
        var invalidMethod = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":{}}", null);
        assertEquals(-32600, json(invalidMethod).getAsJsonObject("error").get("code").getAsInt());
        var invalidParams = post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\",\"params\":[]}", null);
        assertEquals(-32602, json(invalidParams).getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void deletingSessionRemovesItFromConnectionStatus() throws Exception {
        var initialized = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", null);
        var session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
        var response = client.send(HttpRequest.newBuilder(endpoint).header("Mcp-Session-Id", session).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
        assertEquals(0, server.status().getAsJsonArray("recentClients").size());
    }

    @Test
    void screenshotResultUsesStandardImageContent() {
        var result = ToolResults.image("aGVsbG8=", 1280, 720);
        assertEquals("image", result.getAsJsonArray("content").get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("image/png", result.getAsJsonArray("content").get(1).getAsJsonObject().get("mimeType").getAsString());
    }
}
