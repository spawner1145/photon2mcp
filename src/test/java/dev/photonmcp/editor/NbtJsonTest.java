package dev.photonmcp.editor;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NbtJsonTest {
    @Test
    void patchesNestedSettingsWithoutLosingNumericTypesOrMutatingSource() {
        var base = new CompoundTag();
        base.putFloat("size", 1.0f);
        base.putInt("count", 12);
        var child = new CompoundTag();
        child.putString("keep", "existing");
        child.putBoolean("enable", true);
        base.put("shape", child);
        var patch = JsonParser.parseString("{\"size\":2.5,\"count\":24,\"shape\":{\"enable\":false},\"remove\":null}").getAsJsonObject();
        var result = NbtJson.merge(base, patch);
        assertInstanceOf(FloatTag.class, result.get("size"));
        assertInstanceOf(IntTag.class, result.get("count"));
        assertEquals("existing", result.getCompoundOrEmpty("shape").getStringOr("keep", ""));
        assertFalse(result.getCompoundOrEmpty("shape").getBooleanOr("enable", true));
        assertEquals(1.0f, base.getFloatOr("size", 0));
    }

    @Test
    void nullRemovesKeysAndArraysReplaceWithoutLosingElementTypes() {
        var base = new CompoundTag();
        base.putString("name", "old");
        var values = new net.minecraft.nbt.ListTag();
        values.add(FloatTag.valueOf(1.0f));
        values.add(FloatTag.valueOf(2.0f));
        base.put("values", values);
        var result = NbtJson.merge(base, JsonParser.parseString("{\"name\":null,\"values\":[3.5]}").getAsJsonObject());
        assertFalse(result.contains("name"));
        assertEquals(1, result.getListOrEmpty("values").size());
        assertInstanceOf(FloatTag.class, result.getListOrEmpty("values").getFirst());
    }
}
