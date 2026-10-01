package dev.photonmcp.mixin;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.view.HistoryView;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.photonmcp.editor.EditorBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HistoryView.class, remap = false)
public abstract class HistoryViewMixin {
    @Shadow @Final public Editor editor;

    @Inject(method = "jumpToHistory", at = @At("RETURN"))
    private void photonMcp$synchronizeProject(CallbackInfo callback) {
        if (editor instanceof FXEditor fxEditor) EditorBridge.synchronizeHistory(fxEditor);
    }
}
