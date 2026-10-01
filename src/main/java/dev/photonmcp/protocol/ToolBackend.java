package dev.photonmcp.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

public interface ToolBackend {
    JsonArray tools();
    CompletableFuture<JsonObject> call(String name, JsonObject arguments);
}
