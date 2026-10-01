package dev.photonmcp.mixin;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Editor.class, remap = false)
public interface EditorAccessor {
    @Invoker("loadNewProject")
    void photonMcp$loadProject(IProject project, File file);

    @Invoker("closeCurrentProject")
    void photonMcp$closeProject();

    @Accessor("currentProjectFile")
    void photonMcp$setProjectFile(File file);
}
