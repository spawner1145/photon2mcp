package dev.photonmcp.mixin;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import dev.photonmcp.compat.terrain.TerrainDepthCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldSceneRenderer.class, remap = false)
public abstract class TerrainEditorCaptureMixin {
    @Inject(method = "drawWorld", at = @At("HEAD"))
    private void photonMcp$beginTerrainScene(CallbackInfo callback) {
        TerrainDepthCapture.beginEditor();
    }

    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void photonMcp$captureTerrain(CallbackInfo callback) {
        if (!TerrainDepthCapture.needsEditorCapture()) return;
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        TerrainDepthCapture.captureEditor();
    }

    @Inject(method = "drawWorld", at = @At("RETURN"))
    private void photonMcp$endTerrainScene(CallbackInfo callback) {
        TerrainDepthCapture.endEditor();
    }
}
