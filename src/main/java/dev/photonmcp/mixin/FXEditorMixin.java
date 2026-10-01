package dev.photonmcp.mixin;

import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.photonmcp.ui.McpMenu;
import dev.photonmcp.editor.EditorBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FXEditor.class, remap = false)
public abstract class FXEditorMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void photonMcp$attachMenu(CallbackInfo callback) {
        var editor = (FXEditor) (Object) this;
        editor.menuContainer.addChild(new McpMenu(editor).createMenuTab());
    }

    @Inject(method = "closeCurrentProject", at = @At("HEAD"))
    private void photonMcp$releaseProject(CallbackInfo callback) {
        EditorBridge.releaseProject((FXEditor) (Object) this);
    }
}
