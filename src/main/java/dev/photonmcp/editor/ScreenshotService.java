package dev.photonmcp.editor;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.photon.gui.editor.FXEditor;
import dev.photonmcp.protocol.ToolResults;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public final class ScreenshotService {
    private record Pending(FXEditor editor, JsonObject arguments, CompletableFuture<JsonObject> result, long frame) {}
    private record Crop(float left, float top, float right, float bottom) {}
    private static final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private static long frame;

    private ScreenshotService() {}

    public static void request(FXEditor editor, JsonObject arguments, CompletableFuture<JsonObject> result) {
        var region = arguments.has("region") ? arguments.get("region").getAsString() : "scene";
        if (!region.equals("scene") && !region.equals("editor") && !region.equals("screen")) {
            throw new IllegalArgumentException("region must be scene, editor or screen");
        }
        if (editor == null && !region.equals("screen")) throw new IllegalStateException("Open Photon editor before capturing");
        pending.add(new Pending(editor, arguments.deepCopy(), result, frame + 2));
    }

    public static void onFrame() {
        frame++;
        while (!pending.isEmpty() && pending.peek().frame <= frame) {
            var capture = pending.remove();
            if (capture.result.isDone()) continue;
            try {
                take(capture);
            } catch (Exception exception) {
                capture.result.complete(ToolResults.failure(exception.toString()));
            }
        }
    }

    private static void take(Pending capture) {
        var minecraft = Minecraft.getInstance();
        var region = capture.arguments.has("region") ? capture.arguments.get("region").getAsString() : "scene";
        Crop crop = null;
        if (!region.equals("screen")) {
            if (EditorBridge.activeEditor() != capture.editor) throw new IllegalStateException("Editor changed before capture");
            var element = region.equals("scene") ? capture.editor.sceneView.sceneEditor.scene : capture.editor;
            crop = bounds(element);
        }
        var requestedCrop = crop;
        var scale = minecraft.getWindow().getGuiScale();
        var directory = minecraft.gameDirectory.toPath();
        var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
        Thread.startVirtualThread(() -> {
            try (image) {
                if (capture.result.isDone()) return;
                var buffered = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                buffered.setRGB(0, 0, image.getWidth(), image.getHeight(), image.makePixelArray(), 0, image.getWidth());
                if (requestedCrop != null) {
                    var left = Math.max(0, (int) Math.floor(requestedCrop.left * scale));
                    var top = Math.max(0, (int) Math.floor(requestedCrop.top * scale));
                    var right = Math.min(buffered.getWidth(), (int) Math.ceil(requestedCrop.right * scale));
                    var bottom = Math.min(buffered.getHeight(), (int) Math.ceil(requestedCrop.bottom * scale));
                    if (right <= left || bottom <= top) throw new IllegalStateException("Capture region is not visible");
                    buffered = buffered.getSubimage(left, top, right - left, bottom - top);
                }
                var maxSize = capture.arguments.has("max_size") ? capture.arguments.get("max_size").getAsInt() : 1280;
                if (maxSize < 64 || maxSize > 4096) throw new IllegalArgumentException("max_size must be 64..4096");
                var largest = Math.max(buffered.getWidth(), buffered.getHeight());
                if (largest > maxSize) {
                    var width = Math.max(1, buffered.getWidth() * maxSize / largest);
                    var height = Math.max(1, buffered.getHeight() * maxSize / largest);
                    var resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    var graphics = resized.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(buffered, 0, 0, width, height, null);
                    } finally {
                        graphics.dispose();
                    }
                    buffered = resized;
                }
                var output = new ByteArrayOutputStream();
                ImageIO.write(buffered, "png", output);
                var bytes = output.toByteArray();
                if (capture.arguments.has("path")) {
                    var file = Path.of(capture.arguments.get("path").getAsString());
                    file = (file.isAbsolute() ? file : directory.resolve(file)).toAbsolutePath().normalize();
                    Files.createDirectories(file.getParent());
                    Files.write(file, bytes);
                }
                capture.result.complete(ToolResults.image(Base64.getEncoder().encodeToString(bytes), buffered.getWidth(), buffered.getHeight()));
            } catch (Exception exception) {
                capture.result.complete(ToolResults.failure(exception.toString()));
            }
        });
    }

    private static Crop bounds(UIElement element) {
        var left = element.getPositionX();
        var top = element.getPositionY();
        var right = left + element.getSizeWidth();
        var bottom = top + element.getSizeHeight();
        var topLeft = element.getWorldMouse(left, top);
        var topRight = element.getWorldMouse(right, top);
        var bottomLeft = element.getWorldMouse(left, bottom);
        var bottomRight = element.getWorldMouse(right, bottom);
        return new Crop(Math.min(Math.min(topLeft.x, topRight.x), Math.min(bottomLeft.x, bottomRight.x)),
                Math.min(Math.min(topLeft.y, topRight.y), Math.min(bottomLeft.y, bottomRight.y)),
                Math.max(Math.max(topLeft.x, topRight.x), Math.max(bottomLeft.x, bottomRight.x)),
                Math.max(Math.max(topLeft.y, topRight.y), Math.max(bottomLeft.y, bottomRight.y)));
    }
}
