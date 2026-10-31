package dev.photonmcp.compat.terrain;

import dev.photonmcp.PhotonMcp;
import java.lang.reflect.Method;

final class IrisTerrainSource {
    record Source(int framebuffer, int texture, int width, int height) {}

    private static boolean warned;
    private static Method pipelineManager;
    private static Method pipelineGetter;
    private static Method renderingWorld;
    private static Method renderTargets;
    private static Method defaultFramebuffer;
    private static Method framebufferId;
    private static Method hasDepth;
    private static Method depthTexture;
    private static Method width;
    private static Method height;

    private IrisTerrainSource() {}

    static Source resolve() {
        try {
            if (pipelineManager == null) {
                var iris = Class.forName("net.irisshaders.iris.Iris");
                pipelineManager = iris.getMethod("getPipelineManager");
                pipelineGetter = pipelineManager.getReturnType().getMethod("getPipelineNullable");
            }
            Object pipeline = pipelineGetter.invoke(pipelineManager.invoke(null));
            if (pipeline == null) return null;
            if (renderingWorld == null) {
                var accessor = Class.forName("com.lowdragmc.photon.core.mixins.iris.IrisRenderingPipelineAccessor");
                if (!accessor.isInstance(pipeline)) return null;
                renderingWorld = accessor.getMethod("photon$isRenderingWorld");
                renderTargets = accessor.getMethod("photon$renderTargets");
                defaultFramebuffer = accessor.getMethod("photon$defaultFB");
                var framebufferType = defaultFramebuffer.getReturnType();
                framebufferId = framebufferType.getMethod("getId");
                hasDepth = framebufferType.getMethod("hasDepthAttachment");
                var targetsType = renderTargets.getReturnType();
                depthTexture = targetsType.getMethod("getDepthTexture");
                width = targetsType.getMethod("getCurrentWidth");
                height = targetsType.getMethod("getCurrentHeight");
            }
            if (!(boolean) renderingWorld.invoke(pipeline)) return null;
            Object targets = renderTargets.invoke(pipeline);
            Object framebuffer = defaultFramebuffer.invoke(pipeline);
            if (targets == null || framebuffer == null || !(boolean) hasDepth.invoke(framebuffer)) return null;
            return new Source((int) framebufferId.invoke(framebuffer), (int) depthTexture.invoke(targets),
                    (int) width.invoke(targets), (int) height.invoke(targets));
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warned) {
                warned = true;
                PhotonMcp.LOGGER.warn("Terrain-only depth unavailable for this Iris build; block highlights are disabled, not sampled from entity depth", exception);
            }
            return null;
        }
    }
}
