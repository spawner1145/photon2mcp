package dev.photonmcp.editor;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineNbtTest {
    @Test
    void jsonIntegerArraysBecomeNativeFloatBezierChannels() {
        var data = NbtJson.merge(new CompoundTag(), JsonParser.parseString("{\"type\":\"speed\",\"base\":[1],\"channels\":[[[0,1,0,1,0,1,0,1]]]} ").getAsJsonObject());
        TimelineNbt.normalize(data);
        var channel = data.getList("channels", Tag.TAG_LIST).getList(0);
        var segment = channel.getList(0);
        assertEquals(Tag.TAG_FLOAT, segment.getElementType());
        assertEquals(8, segment.size());
        assertEquals(1f, segment.getFloat(1));
    }

    @Test
    void emptyChannelKeepsConstantBaseAndIsSafeToDraw() {
        var data = NbtJson.merge(new CompoundTag(), JsonParser.parseString("{\"type\":\"speed\",\"base\":[0.75],\"channels\":[[]]} ").getAsJsonObject());
        TimelineNbt.normalize(data);
        var segments = data.getList("channels", Tag.TAG_LIST).getList(0);
        assertEquals(1, segments.size());
        assertEquals(0.75f, segments.getList(0).getFloat(1));
        var before = data.copy();
        TimelineNbt.normalize(data);
        assertEquals(before, data);
    }

    @Test
    void uuidArraysOutsideAnimatedChannelsKeepTheirType() {
        var data = new CompoundTag();
        data.putUUID("target", java.util.UUID.randomUUID());
        var before = data.copy();
        TimelineNbt.normalize(data);
        assertEquals(before, data);
    }

    @Test
    void sectionRepeatsReleaseAndClipModesSurviveNormalization() {
        var data = NbtJson.merge(new CompoundTag(), JsonParser.parseString("{\"sections\":[{\"name\":\"hold\",\"start\":0,\"repeat\":0,\"then\":\"linear\"}],\"release\":{\"section\":\"end\",\"timing\":\"section_end\"},\"tracks\":[{\"type\":\"control\",\"data\":{\"clips\":[{\"start\":0,\"duration\":20,\"exit\":\"drain\",\"seam\":\"restart\"}]}}]}").getAsJsonObject());
        var before = data.copy();
        TimelineNbt.normalize(data);
        assertEquals(before, data);
    }
}
