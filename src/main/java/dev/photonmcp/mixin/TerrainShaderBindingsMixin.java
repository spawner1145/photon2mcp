package dev.photonmcp.mixin;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.ShaderGraphMaterial;
import dev.photonmcp.compat.terrain.TerrainDepthCapture;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderGraphMaterial.class, remap = false)
public abstract class TerrainShaderBindingsMixin {
    @Inject(method = "bindDynamicUniforms", at = @At("TAIL"))
    private void photonMcp$bindTerrainDepth(ShaderInstance shader, CompiledShaderGraph compiled, CallbackInfo callback) {
        TerrainDepthCapture.bind(shader);
    }
}
