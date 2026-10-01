package dev.photonmcp;

import dev.photonmcp.editor.EditorBridge;
import dev.photonmcp.editor.ScreenshotService;
import dev.photonmcp.protocol.McpServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

@Mod(value = "photon_mcp", dist = Dist.CLIENT)
public final class PhotonMcp {
    public static final Logger LOGGER = LoggerFactory.getLogger("Photon MCP");
    private static PhotonMcp instance;
    private final Path configPath = FMLPaths.CONFIGDIR.get().resolve("photon-mcp.json");
    public final McpServer server = new McpServer(new EditorBridge());
    private McpConfig config = new McpConfig("127.0.0.1", 8765);
    private String startupError = "";

    public PhotonMcp() {
        instance = this;
        NeoForge.EVENT_BUS.addListener(this::onFrame);
        try {
            config = McpConfig.load(configPath);
            server.start(config);
            LOGGER.info("Listening on {}", config.endpoint());
        } catch (Exception exception) {
            startupError = exception.toString();
            LOGGER.error("Could not start Photon MCP", exception);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "photon-mcp-shutdown"));
    }

    public static PhotonMcp get() {
        return instance;
    }

    public McpConfig config() {
        return config;
    }

    public String startupError() {
        return startupError;
    }

    public void configure(String host, int port) throws Exception {
        var next = new McpConfig(host.trim(), port);
        next.save(configPath);
        config = next;
        start();
    }

    public void start() throws Exception {
        try {
            server.start(config);
            startupError = "";
        } catch (Exception exception) {
            startupError = exception.toString();
            throw exception;
        }
    }

    private void onFrame(RenderFrameEvent.Post event) {
        ScreenshotService.onFrame();
    }
}
