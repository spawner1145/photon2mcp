package dev.photonmcp.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ToolResults {
    private ToolResults() {}

    public static JsonObject text(JsonObject value) {
        var result = new JsonObject();
        var content = new JsonArray();
        var text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", value.toString());
        content.add(text);
        result.add("content", content);
        result.add("structuredContent", value);
        result.addProperty("isError", false);
        return result;
    }

    public static JsonObject failure(String message) {
        var value = new JsonObject();
        value.addProperty("error", message);
        var result = text(value);
        result.addProperty("isError", true);
        return result;
    }

    public static JsonObject image(String base64, int width, int height) {
        var value = new JsonObject();
        value.addProperty("width", width);
        value.addProperty("height", height);
        var result = text(value);
        var image = new JsonObject();
        image.addProperty("type", "image");
        image.addProperty("mimeType", "image/png");
        image.addProperty("data", base64);
        result.getAsJsonArray("content").add(image);
        return result;
    }
}
