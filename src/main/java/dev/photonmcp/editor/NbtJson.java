package dev.photonmcp.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.*;

public final class NbtJson {
    private NbtJson() {}

    public static JsonElement json(Tag tag) {
        return Dynamic.convert(NbtOps.INSTANCE, JsonOps.INSTANCE, tag);
    }

    public static CompoundTag merge(CompoundTag base, JsonObject patch) {
        var merged = base.copy();
        patch.entrySet().forEach(entry -> {
            if (entry.getValue().isJsonNull()) merged.remove(entry.getKey());
            else merged.put(entry.getKey(), convert(entry.getValue(), merged.get(entry.getKey())));
        });
        return merged;
    }

    private static Tag convert(JsonElement value, Tag previous) {
        if (value.isJsonObject()) {
            return merge(previous instanceof CompoundTag compound ? compound : new CompoundTag(), value.getAsJsonObject());
        }
        if (value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return ByteTag.valueOf(primitive.getAsBoolean());
            if (primitive.isNumber()) {
                return switch (previous) {
                    case ByteTag ignored -> ByteTag.valueOf(primitive.getAsByte());
                    case ShortTag ignored -> ShortTag.valueOf(primitive.getAsShort());
                    case IntTag ignored -> IntTag.valueOf(primitive.getAsInt());
                    case LongTag ignored -> LongTag.valueOf(primitive.getAsLong());
                    case FloatTag ignored -> FloatTag.valueOf(primitive.getAsFloat());
                    case DoubleTag ignored -> DoubleTag.valueOf(primitive.getAsDouble());
                    case null, default -> Dynamic.convert(JsonOps.INSTANCE, NbtOps.INSTANCE, value);
                };
            }
        }
        if (value.isJsonArray() && previous instanceof ListTag list) {
            var next = new ListTag();
            for (var element : value.getAsJsonArray()) {
                next.add(convert(element, list.isEmpty() ? null : list.get(0)));
            }
            return next;
        }
        return Dynamic.convert(JsonOps.INSTANCE, NbtOps.INSTANCE, value);
    }

    public static JsonObject describe(Tag tag) {
        var info = new JsonObject();
        info.addProperty("nbtType", tag.getType().getPrettyName());
        if (tag instanceof CompoundTag compound) {
            var fields = new JsonObject();
            for (var key : compound.getAllKeys()) fields.add(key, describe(compound.get(key)));
            info.add("fields", fields);
        } else if (tag instanceof ListTag list) {
            info.addProperty("length", list.size());
            if (!list.isEmpty()) info.add("element", describe(list.get(0)));
        } else {
            info.add("value", json(tag));
        }
        return info;
    }
}
