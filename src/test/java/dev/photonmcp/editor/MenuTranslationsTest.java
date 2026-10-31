package dev.photonmcp.editor;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuTranslationsTest {
    @Test
    void compatibilityLabelKeepsExistingMenuTranslations() throws Exception {
        for (var language : new String[]{"en_us", "zh_cn"}) {
            try (var stream = getClass().getResourceAsStream("/assets/photon_mcp/lang/" + language + ".json")) {
                assertNotNull(stream);
                var labels = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var key : new String[]{"status", "configure", "stop", "start", "copy_url", "apply"}) {
                    assertTrue(labels.has("photon_mcp.menu." + key));
                }
                assertTrue(labels.has("photon_mcp_terrain_depth"));
            }
        }
    }
}
