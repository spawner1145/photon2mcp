package dev.photonmcp.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.Resource;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourceTools {
    private ResourceTools() {}

    public static JsonArray entries() {
        var entries = new JsonArray();
        for (var category : new FXProject().getResources().resources) {
            var resource = resource(category.getName());
            var instance = resource.getResourceInstance();
            var providers = new java.util.ArrayList<>(instance.getBuiltinProviders().values().stream().flatMap(java.util.List::stream).toList());
            providers.addAll(instance.getCustomProviders().values().stream().flatMap(java.util.List::stream).toList());
            for (var provider : providers) {
                for (var entry : provider) {
                    var value = new JsonObject();
                    value.addProperty("category", resource.getName());
                    value.addProperty("provider", provider.getName());
                    value.addProperty("resourcePath", entry.getKey().getPathWithType());
                    entries.add(value);
                }
            }
        }
        return entries;
    }

    public static JsonObject read(JsonObject arguments) throws Exception {
        var resource = resource(arguments.get("category").getAsString());
        var data = readData(resource, arguments.get("path").getAsString());
        var result = describe(data);
        result.addProperty("category", resource.getName());
        return result;
    }

    public static JsonObject write(JsonObject arguments) throws Exception {
        var resource = resource(arguments.get("category").getAsString());
        Tag source = arguments.has("source") ? readData(resource, arguments.get("source").getAsString()) : new CompoundTag();
        if (arguments.has("snbt")) source = TagParser.parseTag(arguments.get("snbt").getAsString());
        if (!(source instanceof CompoundTag compound)) throw new IllegalArgumentException("Resource payload is not a compound; supply a compound SNBT payload");
        var data = arguments.has("patch") ? NbtJson.merge(compound, arguments.getAsJsonObject("patch")) : compound.copy();
        var decoded = resource.deserializeResource(data, Platform.getFrozenRegistry());
        if (decoded == null) throw new IllegalArgumentException("Photon rejected the resource payload");
        var serialized = resource.serializeResource(decoded, Platform.getFrozenRegistry());
        if (serialized == null) throw new IllegalArgumentException("Cannot serialize resource");
        var file = resolve(arguments.get("path").getAsString());
        if (!file.toString().endsWith(resource.getFileExtension())) {
            throw new IllegalArgumentException("Resource file must end with " + resource.getFileExtension());
        }
        Files.createDirectories(file.getParent());
        var wrapper = new CompoundTag();
        wrapper.putString("type", resource.getName());
        wrapper.put("data", serialized);
        NbtIo.write(wrapper, file);
        resource.getResourceInstance().clearCache();
        var result = describe(serialized);
        result.addProperty("path", file.toString());
        result.addProperty("resourcePath", "file(" + FilePath.toGameRelative(file.toString()) + ")");
        result.addProperty("bytes", Files.size(file));
        return result;
    }

    private static Tag readData(Resource<Object> resource, String reference) throws Exception {
        if (reference.matches("^[a-zA-Z0-9_-]+\\(.+\\)$")) {
            var path = IResourcePath.parse(reference);
            var value = resource.getResourceInstance().getResource(path);
            if (value == null) throw new IllegalArgumentException("Resource not found: " + reference);
            var data = resource.serializeResource(value, Platform.getFrozenRegistry());
            if (data == null) throw new IllegalArgumentException("Cannot serialize resource");
            return data;
        }
        var wrapper = NbtIo.read(resolve(reference));
        if (wrapper == null || !wrapper.getString("type").equals(resource.getName()) || wrapper.get("data") == null) {
            throw new IllegalArgumentException("Resource file type does not match " + resource.getName());
        }
        return wrapper.get("data").copy();
    }

    @SuppressWarnings("unchecked")
    private static Resource<Object> resource(String category) {
        return (Resource<Object>) new FXProject().getResources().resources.stream()
                .filter(resource -> resource.getName().equals(category)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown resource category: " + category));
    }

    private static JsonObject describe(Tag tag) {
        var result = new JsonObject();
        result.add("json", NbtJson.json(tag));
        result.addProperty("snbt", tag.toString());
        result.add("schema", NbtJson.describe(tag));
        return result;
    }

    private static Path resolve(String name) {
        var file = Path.of(name);
        return (file.isAbsolute() ? file : Minecraft.getInstance().gameDirectory.toPath().resolve(file)).toAbsolutePath().normalize();
    }
}
