package dev.photonmcp.editor;

import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

public final class TimelineNbt {
    private TimelineNbt() {}

    public static void normalize(CompoundTag timeline) {
        visit(timeline);
    }

    private static void visit(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("channels") && compound.contains("type")) {
                compound.put("channels", floatLists(compound.get("channels")));
                if (compound.contains("base")) compound.put("base", floatLists(compound.get("base")));
                var channels = compound.getList("channels", Tag.TAG_LIST);
                var base = compound.getList("base", Tag.TAG_FLOAT);
                for (int axis = 0; axis < channels.size(); axis++) {
                    var segments = channels.getList(axis);
                    if (!segments.isEmpty()) continue;
                    float value = axis < base.size() ? base.getFloat(axis) : compound.getString("type").equals("speed") ? 1f : 0f;
                    var single = new ListTag();
                    for (int index = 0; index < 4; index++) {
                        single.add(FloatTag.valueOf(0));
                        single.add(FloatTag.valueOf(value));
                    }
                    segments.add(single);
                }
            }
            for (String key : compound.getAllKeys()) visit(compound.get(key));
        } else if (tag instanceof CollectionTag<?> collection) {
            for (Tag child : collection) visit(child);
        }
    }

    private static Tag floatLists(Tag tag) {
        if (tag instanceof NumericTag number) return FloatTag.valueOf(number.getAsFloat());
        if (tag instanceof CollectionTag<?> collection) {
            var values = new ListTag();
            for (Tag child : collection) values.add(floatLists(child));
            return values;
        }
        throw new IllegalArgumentException("Timeline numeric channels must contain only numeric lists");
    }
}
