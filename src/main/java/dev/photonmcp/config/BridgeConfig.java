package dev.photonmcp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BridgeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public String host = "127.0.0.1";
    public int port = 8390;

    public static BridgeConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            var config = new BridgeConfig();
            config.save(path);
            return config;
        }
        var config = GSON.fromJson(Files.readString(path), BridgeConfig.class);
        if (config == null) throw new IllegalArgumentException("Empty MCP configuration");
        config.validate();
        return config;
    }

    public void validate() {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("Host must not be empty");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
    }

    public void save(Path path) throws IOException {
        validate();
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, GSON.toJson(this) + System.lineSeparator());
    }

    public String endpoint() {
        var address = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return "http://" + address + ":" + port + "/mcp";
    }
}
