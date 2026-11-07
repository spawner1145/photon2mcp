package dev.photonmcp.editor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.*;

class AuthorityApiCompatibilityTest {
    private static Map<String, String> methods(String type) throws IOException {
        var signatures = new HashMap<String, String>();
        String property = type.startsWith("com/lowdragmc/photon/") ? "photonMcp.photonJar" : "photonMcp.ldlibJar";
        try (var archive = new JarFile(System.getProperty(property))) {
            var entry = archive.getJarEntry(type + ".class");
            assertNotNull(entry, type);
            try (var stream = archive.getInputStream(entry)) {
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        signatures.put(name, descriptor);
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return signatures;
    }

    @Test
    void shaderBindingAndEditorCaptureMixinTargetsExistInInstalledLibraries() throws IOException {
        var material = methods("com/lowdragmc/photon/client/gameobject/emitter/data/material/ShaderGraphMaterial");
        assertEquals("(Lnet/minecraft/client/renderer/ShaderInstance;Lcom/lowdragmc/kilagraph/rendertype/compiler/CompiledShaderGraph;)V",
                material.get("bindDynamicUniforms"));
        var scene = methods("com/lowdragmc/lowdraglib2/client/scene/WorldSceneRenderer");
        assertEquals("()V", scene.get("drawWorld"));
        assertTrue(scene.containsKey("renderEntities"));
        var editor = methods("com/lowdragmc/lowdraglib2/editor/ui/Editor");
        assertTrue(editor.containsKey("loadNewProject"));
        assertTrue(editor.containsKey("closeCurrentProject"));
    }

    @Test
    void nativeCollisionAndOptionalIrisAccessorsExist() throws IOException {
        assertNotNull(methods("com/lowdragmc/photon/client/gameobject/emitter/data/CollisionSetting"));
        var iris = methods("com/lowdragmc/photon/core/mixins/iris/IrisRenderingPipelineAccessor");
        assertTrue(iris.containsKey("photon$renderTargets"));
        assertTrue(iris.containsKey("photon$isRenderingWorld"));
        assertTrue(iris.containsKey("photon$defaultFB"));
    }

    @Test
    void nativeSectionControlsAndClockAccessAreAvailable() throws IOException {
        var player = methods("com/lowdragmc/photon/client/fx/timeline/TimelinePlayer");
        assertEquals("()D", player.get("localTime"));
        var controls = methods("com/lowdragmc/photon/client/fx/timeline/SectionControl");
        assertEquals("(Ljava/lang/String;)Z", controls.get("jumpTo"));
        assertEquals("(Ljava/lang/String;)Z", controls.get("queue"));
        assertEquals("()Z", controls.get("advance"));
        assertEquals("()Z", controls.get("release"));
        var host = methods("com/lowdragmc/lowdraglib2/gui/ui/window/ModularUIWindow");
        assertTrue(host.containsKey("getGuiScale"));
        assertTrue(host.containsKey("surface"));
        assertTrue(host.containsKey("openWindows"));
    }
}
