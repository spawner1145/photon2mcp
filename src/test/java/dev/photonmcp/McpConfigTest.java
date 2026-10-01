package dev.photonmcp;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class McpConfigTest {
    @TempDir Path directory;

    @Test
    void createsAndPersistsOnlyHostAndPort() throws Exception {
        var path = directory.resolve("photon-mcp.json");
        var defaults = McpConfig.load(path);
        assertEquals("127.0.0.1", defaults.host());
        assertEquals(8765, defaults.port());
        new McpConfig("0.0.0.0", 12345).save(path);
        assertEquals(new McpConfig("0.0.0.0", 12345), McpConfig.load(path));
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
        assertEquals(2, json.size());
        assertFalse(json.has("token"));
    }

    @Test
    void formatsIpv6AndValidatesSocketValues() {
        assertEquals("http://[::1]:8765/mcp", new McpConfig("::1", 8765).endpoint());
        assertThrows(IllegalArgumentException.class, () -> new McpConfig("", 12));
        assertThrows(IllegalArgumentException.class, () -> new McpConfig("localhost", 65536));
    }
}
