package dev.photonmcp.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.lowdragmc.photon.gui.editor.view.FXObjectTreeNode;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import dev.photonmcp.PhotonMcp;
import dev.photonmcp.mixin.EditorAccessor;
import dev.photonmcp.protocol.ToolBackend;
import dev.photonmcp.protocol.ToolResults;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class EditorBridge implements ToolBackend {
    private final CodeRunner codeRunner = new CodeRunner();
    private static final com.google.gson.Gson gson = new com.google.gson.Gson();
    private final Map<FXProject, Map<String, CompoundTag>> checkpoints = new WeakHashMap<>();
    private static final Map<FXProject, Map<UUID, IFXObject>> retainedObjects = new WeakHashMap<>();

    @Override
    public JsonArray tools() {
        return ToolCatalog.tools();
    }

    @Override
    public java.util.concurrent.CompletableFuture<JsonObject> call(String name, JsonObject arguments) {
        var result = new java.util.concurrent.CompletableFuture<JsonObject>();
        Minecraft.getInstance().execute(() -> {
            if (result.isDone()) return;
            try {
                if (name.equals("photon_screenshot")) {
                    ScreenshotService.request(activeEditor(), arguments, result);
                } else {
                    result.complete(ToolResults.text(dispatch(name, arguments)));
                }
            } catch (Throwable exception) {
                PhotonMcp.LOGGER.warn("MCP tool {} failed: {}", name, exception.toString());
                result.complete(ToolResults.failure(exception.toString()));
            }
        });
        return result;
    }

    public static FXEditor activeEditor() {
        var ui = ModularUI.of(Minecraft.getInstance().screen);
        if (ui != null) {
            var editors = ui.getElementsByType(FXEditor.class);
            for (var editor : editors) {
                boolean displayed = true;
                for (var element = (com.lowdragmc.lowdraglib2.gui.ui.UIElement) editor;
                     element != null; element = element.getParent()) {
                    if (!element.isDisplayed() || !element.isVisible()) {
                        displayed = false;
                        break;
                    }
                }
                if (displayed) return editor;
            }
        }
        return null;
    }

    private static FXEditor requireEditor() {
        var editor = activeEditor();
        if (editor == null) throw new IllegalStateException("Open the Photon editor first (/photon_editor or photon_open_editor).");
        return editor;
    }

    private static FXProject project(FXEditor editor) {
        if (!(editor.getCurrentProject() instanceof FXProject project) || editor.runtime == null) {
            throw new IllegalStateException("No Photon project loaded; use photon_new_project or photon_open_project.");
        }
        return project;
    }

    private JsonObject dispatch(String name, JsonObject arguments) throws Exception {
        if (name.equals("photon_execute_code")) return executeCode(arguments);
        if (name.equals("photon_api_lookup")) return lookupApi(arguments);
        if (name.equals("photon_list_api_classes")) return ApiClasses.list(
                Minecraft.getInstance().gameDirectory.toPath().resolve("mods"),
                arguments.has("package_prefix") ? string(arguments, "package_prefix") : "com.lowdragmc.photon.",
                arguments.has("filter") ? string(arguments, "filter") : "");
        if (name.equals("photon_get_server_status")) return PhotonMcp.get().server.status();
        if (name.equals("photon_get_editor_state")) return state(activeEditor());
        if (name.equals("photon_list_object_types")) return types();
        if (name.equals("photon_describe_component")) return component(arguments);
        if (name.equals("photon_describe_object_type")) {
            var type = PhotonRegistries.FX_OBJECTS.get(string(arguments, "type"));
            if (type == null) throw new IllegalArgumentException("Unknown Photon object type");
            return detail(type.create());
        }
        if (name.equals("photon_open_editor")) {
            if (activeEditor() == null) {
                if (Minecraft.getInstance().level == null) throw new IllegalStateException("Join a single-player world before opening Photon editor.");
                var ui = new ModularUI(UI.of(EditorWindow.open(FXEditor.WINDOW_ID, FXEditor::new).setId("fx_editor")))
                        .shouldCloseOnEsc(false).shouldCloseOnKeyInventory(false);
                Minecraft.getInstance().setScreen(new ModularUIScreen(ui, Component.literal("Photon")));
            }
            return state(requireEditor());
        }
        var editor = requireEditor();
        switch (name) {
            case "photon_new_project" -> {
                replaceProject(editor, new FXProject(), null);
                return state(editor);
            }
            case "photon_open_project" -> {
                var file = path(arguments, "path");
                FXProject next;
                if (file.toString().endsWith(".fxproj")) {
                    next = (FXProject) FXProject.TYPE.loadProjectFromFile(file.toFile());
                } else if (file.toString().endsWith(".fx")) {
                    next = new FXProject();
                    var tag = NbtIo.read(file);
                    if (tag == null) throw new IllegalArgumentException("Empty FX file");
                    next.getFx().deserializeNBT(Platform.getFrozenRegistry(), tag);
                    file = null;
                } else throw new IllegalArgumentException("Expected .fxproj or .fx");
                replaceProject(editor, next, file);
                return state(editor);
            }
            case "photon_set_view" -> {
                view(editor, arguments);
                return state(editor);
            }
            case "photon_editor_action" -> {
                return editorAction(editor, arguments);
            }
            case "photon_fxpack" -> {
                return fxpack(editor, arguments);
            }
            case "photon_list_resources" -> {
                return resources(arguments);
            }
            case "photon_import_texture" -> {
                return importTexture(arguments);
            }
            case "photon_read_resource" -> {
                return ResourceTools.read(arguments);
            }
            case "photon_write_resource" -> {
                var result = ResourceTools.write(arguments);
                editor.reloadEffect();
                return result;
            }
            default -> { }
        }
        var project = project(editor);
        return switch (name) {
            case "photon_get_scene_info" -> scene(editor);
            case "photon_get_object" -> detail(find(editor.runtime, string(arguments, "object")));
            case "photon_get_project" -> tagResult(snapshot(project));
            case "photon_describe_timeline" -> describeTimeline(editor);
            case "photon_save_project" -> save(editor, project, arguments);
            case "photon_export_effect" -> export(project, arguments);
            case "photon_select_object" -> select(editor, string(arguments, "object"));
            case "photon_playback" -> playback(editor, arguments);
            case "photon_checkpoint" -> checkpoint(editor, project, arguments);
            case "photon_undo_redo" -> {
                switch (string(arguments, "action")) {
                    case "undo" -> editor.historyView.undo();
                    case "redo" -> editor.historyView.redo();
                    default -> throw new IllegalArgumentException("action must be undo or redo");
                }
                yield scene(editor);
            }
            case "photon_batch" -> batch(editor, project, arguments);
            default -> mutate(editor, project, name, arguments);
        };
    }

    private static JsonObject state(FXEditor editor) {
        var result = new JsonObject();
        result.addProperty("editorOpen", editor != null);
        if (editor == null) return result;
        result.addProperty("projectLoaded", editor.getCurrentProject() instanceof FXProject);
        result.addProperty("projectFile", editor.getCurrentProjectFile() == null ? "" : editor.getCurrentProjectFile().getAbsolutePath());
        result.addProperty("playing", editor.sceneView.particleManager.isPlaying());
        result.addProperty("tick", editor.sceneView.particleManager.getTime());
        if (editor.runtime != null) {
            try {
                result.addProperty("timelineEvaluationTick", (Number) ApiIntrospection.read(editor.runtime.timelinePlayer, "lastEvalTime"));
                result.addProperty("timelineNextTick", (Number) ApiIntrospection.read(editor.runtime.timelinePlayer, "localTime"));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot inspect Photon timeline clock", exception);
            }
        }
        result.addProperty("objectCount", editor.runtime == null ? 0 : editor.runtime.fxData.objects().size());
        var selected = new JsonArray();
        for (var node : editor.hierarchyView.treeList.getSelected()) selected.add(node.getKey().id().toString());
        result.add("selected", selected);
        var inspected = editor.inspectorView.inspector.getInspectedConfigurable();
        result.addProperty("inspectedObject", inspected instanceof IFXObject object ? object.id().toString() : "");
        return result;
    }

    private static JsonObject types() {
        var result = new JsonObject();
        var objects = new JsonArray();
        for (var type : PhotonRegistries.FX_OBJECTS) objects.add(type.name());
        result.add("objects", objects);
        var shapes = new JsonArray();
        for (var holder : PhotonRegistries.SHAPES) shapes.add(holder.annotation().name());
        result.add("shapes", shapes);
        var functions = new JsonArray();
        for (var holder : PhotonRegistries.NUMBER_FUNCTIONS) functions.add(holder.annotation().name());
        result.add("numberFunctions", functions);
        var materials = new JsonArray();
        for (var holder : PhotonRegistries.MATERIALS) materials.add(holder.annotation().name());
        result.add("materials", materials);
        var tracks = new JsonArray();
        for (var type : PhotonRegistries.TIMELINE_TRACKS) tracks.add(type.name());
        result.add("timelineTracks", tracks);
        var sceneModes = new JsonArray();
        for (var mode : SceneView.SceneMode.values()) sceneModes.add(mode.name());
        result.add("sceneModes", sceneModes);
        var drawModes = new JsonArray();
        for (var mode : SceneView.DrawMode.values()) drawModes.add(mode.name());
        result.add("drawModes", drawModes);
        return result;
    }

    private static JsonObject detail(IFXObject object) {
        var wrapper = object.serializeWrapper();
        if (wrapper == null) throw new IllegalStateException("Photon failed to serialize the object");
        var result = tagResult(wrapper);
        result.addProperty("id", object.id().toString());
        result.addProperty("name", object.getName());
        result.addProperty("type", object.name());
        result.add("dataSchema", NbtJson.describe(wrapper.getCompound("data")));
        result.add("transform", transform(object));
        return result;
    }

    private static JsonObject tagResult(CompoundTag tag) {
        var result = new JsonObject();
        result.add("json", NbtJson.json(tag));
        result.addProperty("snbt", tag.toString());
        return result;
    }

    private static JsonObject transform(IFXObject object) {
        var result = new JsonObject();
        result.add("position", vector(object.transform().localPosition()));
        result.add("scale", vector(object.transform().localScale()));
        var angles = object.transform().localRotation().getEulerAnglesXYZ(new Vector3f());
        angles.mul((float) (180 / Math.PI));
        result.add("rotation", vector(angles));
        result.addProperty("parent", object.transform().parent() == null ? "" : object.transform().parent().id().toString());
        return result;
    }

    private static JsonArray vector(Vector3f vector) {
        var result = new JsonArray();
        result.add(vector.x);
        result.add(vector.y);
        result.add(vector.z);
        return result;
    }

    private static JsonObject scene(FXEditor editor) {
        var result = state(editor);
        var objects = new JsonArray();
        for (var object : editor.runtime.objects.values()) {
            var entry = new JsonObject();
            entry.addProperty("id", object.id().toString());
            entry.addProperty("name", object.getName());
            entry.addProperty("type", object.name());
            entry.addProperty("visible", object.isVisible());
            entry.add("transform", transform(object));
            objects.add(entry);
        }
        result.add("objects", objects);
        var camera = new JsonObject();
        var preview = editor.sceneView.sceneEditor.scene;
        camera.addProperty("yaw", preview.getRotationYaw());
        camera.addProperty("pitch", preview.getRotationPitch());
        camera.addProperty("zoom", preview.getZoom());
        camera.add("center", vector(preview.getCenter()));
        result.add("camera", camera);
        return result;
    }

    private static IFXObject find(FXRuntime runtime, String reference) {
        if (reference.equals("root")) return runtime.root;
        var candidates = runtime.objects.values().stream()
                .filter(object -> object.id().toString().equals(reference) || object.getName().equals(reference)).toList();
        if (candidates.size() != 1) {
            throw new IllegalArgumentException("Expected one object for '" + reference + "', found " + candidates.size() + "; use UUIDs.");
        }
        return candidates.getFirst();
    }

    private static IFXObject authored(FXRuntime runtime, String reference) {
        var object = find(runtime, reference);
        if (object == runtime.root) throw new IllegalArgumentException("The synthetic root cannot be edited/deleted.");
        return object;
    }

    private static CompoundTag snapshot(FXProject project) {
        return project.getFx().serializeNBT(Platform.getFrozenRegistry()).copy();
    }

    private static FXRuntime draft(CompoundTag data) {
        var fx = new FX();
        fx.deserializeNBT(Platform.getFrozenRegistry(), data.copy());
        var runtime = fx.createInternalRuntime();
        runtime.root.updatePos(new Vector3f(0.5f, 2, 0.5f));
        return runtime;
    }

    private static void restore(FXEditor editor, FXProject project, CompoundTag data) {
        if (editor.getCurrentProject() != project) throw new IllegalStateException("Project changed while editing");
        var preview = editor.sceneView.sceneEditor.scene;
        var yaw = preview.getRotationYaw();
        var pitch = preview.getRotationPitch();
        var zoom = preview.getZoom();
        var center = new Vector3f(preview.getCenter());
        var pool = retainedObjects.computeIfAbsent(project, key -> new HashMap<>());
        for (var object : editor.runtime.objects.values()) {
            if (object != editor.runtime.root) pool.putIfAbsent(object.id(), object);
        }
        var desired = draft(data);
        var timeline = project.getFx().getFxData().timeline();
        var timelineTag = desired.fxData.timeline().serializeNBT(Platform.getFrozenRegistry());
        editor.sceneView.clearScene();
        editor.inspectorView.clear();
        editor.sceneView.sceneEditor.setTransformGizmoTarget(null);
        editor.runtime.destroy(true);
        var previousObjects = new ArrayList<>(editor.runtime.objects.values());
        previousObjects.remove(editor.runtime.root);
        for (var previous : previousObjects) previous.transform().parent(null, false);
        for (var previous : previousObjects) previous.setScene(null);
        editor.runtime.root.setScene(null);
        var objects = project.getFx().getFxData().objects();
        objects.clear();
        for (var candidate : desired.fxData.objects()) {
            var existing = pool.get(candidate.id());
            if (existing != null && existing.getFXObjectType() == candidate.getFXObjectType()) {
                var wrapper = candidate.serializeWrapper();
                PersistedParser.deserializeNBT(wrapper.getCompound("data"), existing, Platform.getFrozenRegistry());
                existing.transform().localPosition(new Vector3f(candidate.transform().localPosition()));
                existing.transform().localRotation(new Quaternionf(candidate.transform().localRotation()));
                existing.transform().localScale(new Vector3f(candidate.transform().localScale()));
                objects.add(existing);
            } else {
                var created = IFXObject.CODEC.parse(NbtOps.INSTANCE, candidate.serializeWrapper()).getOrThrow();
                objects.add(created);
                pool.put(created.id(), created);
            }
        }
        if (!timeline.serializeNBT(Platform.getFrozenRegistry()).equals(timelineTag)) {
            timeline.deserializeNBT(Platform.getFrozenRegistry(), timelineTag.copy());
        }
        editor.runtime = project.getFx().createInternalRuntime();
        editor.runtime.root.updatePos(new Vector3f(0.5f, 2, 0.5f));
        editor.hierarchyView.loadFXRuntime(editor.runtime);
        var rootNode = editor.hierarchyView.getRootNode();
        if (!rootNode.getChildren().isEmpty()) editor.hierarchyView.treeList.expandNodeAlongPath(rootNode.getChildren().getFirst());
        editor.timelineView.rebuild();
        editor.sceneView.loadScene();
        preview.setCenter(center).setZoom(zoom).setCameraYawAndPitch(yaw, pitch);
        editor.reloadEffect();
    }

    private static void commit(FXEditor editor, FXProject project, CompoundTag before, CompoundTag after, String label) {
        editor.historyView.pushHistory(Component.literal("MCP: " + label), EditAction.of(
                () -> restore(editor, project, after), () -> restore(editor, project, before)));
    }

    private static JsonObject mutate(FXEditor editor, FXProject project, String tool, JsonObject arguments) throws Exception {
        var before = snapshot(project);
        var runtime = draft(before);
        var result = edit(runtime, tool, arguments);
        var after = new CompoundTag();
        after.put("fxData", runtime.fxData.serializeNBT(Platform.getFrozenRegistry()));
        commit(editor, project, before, after, tool);
        if (result.has("id")) select(editor, result.get("id").getAsString());
        return result;
    }

    private static JsonObject edit(FXRuntime runtime, String tool, JsonObject arguments) throws Exception {
        IFXObject changed;
        switch (tool) {
            case "photon_create_object" -> {
                var type = PhotonRegistries.FX_OBJECTS.get(string(arguments, "type"));
                if (type == null) throw new IllegalArgumentException("Unknown object type");
                changed = patched(type.create(), arguments, false);
                if (arguments.has("name")) changed.setName(string(arguments, "name"));
                changed.transform()._setInternalID(UUID.randomUUID());
                var parent = arguments.has("parent") ? find(runtime, string(arguments, "parent")) : runtime.root;
                changed.transform().parent(parent.transform(), false);
                runtime.fxData.objects().add(changed);
                runtime.addSceneObject(changed);
            }
            case "photon_update_object" -> {
                var existing = authored(runtime, string(arguments, "object"));
                changed = patched(existing, arguments, true);
                var index = runtime.fxData.objects().indexOf(existing);
                var parent = existing.transform().parent();
                var siblingIndex = existing.transform().getSiblingIndex();
                var children = new ArrayList<>(existing.transform().children());
                existing.transform().parent(null, false);
                runtime.removeSceneObjectInternal(existing);
                runtime.fxData.objects().set(index, changed);
                changed.transform().parent(parent, false);
                runtime.addSceneObject(changed);
                for (var child : children) child.parent(changed.transform(), false);
                changed.transform().setSiblingIndex(siblingIndex);
            }
            case "photon_set_transform" -> {
                changed = authored(runtime, string(arguments, "object"));
                if (arguments.has("position")) changed.transform().localPosition(readVector(arguments, "position"));
                if (arguments.has("scale")) changed.transform().localScale(readVector(arguments, "scale"));
                if (arguments.has("rotation")) {
                    var angles = readVector(arguments, "rotation").mul((float) (Math.PI / 180));
                    changed.transform().localRotation(new Quaternionf().rotationXYZ(angles.x, angles.y, angles.z));
                }
            }
            case "photon_reparent_object" -> {
                changed = authored(runtime, string(arguments, "object"));
                var parent = find(runtime, string(arguments, "parent"));
                changed.transform().parent(parent.transform(), !arguments.has("keep_world") || arguments.get("keep_world").getAsBoolean());
            }
            case "photon_duplicate_object" -> {
                var original = authored(runtime, string(arguments, "object"));
                changed = original.copy(false);
                changed.transform()._setInternalID(UUID.randomUUID());
                changed.transform()._setInternalChildID(List.of());
                changed.setName(arguments.has("name") ? string(arguments, "name") : original.getName() + " Copy");
                changed.transform().parent(original.transform().parent(), false);
                runtime.fxData.objects().add(changed);
                runtime.addSceneObject(changed);
            }
            case "photon_delete_object" -> {
                var original = authored(runtime, string(arguments, "object"));
                var removed = new ArrayList<IFXObject>();
                original.executeAll(object -> removed.add((IFXObject) object));
                for (var object : removed.reversed()) {
                    object.transform().parent(null, false);
                    runtime.fxData.objects().remove(object);
                    runtime.removeSceneObjectInternal(object);
                }
                var result = new JsonObject();
                result.addProperty("deleted", removed.size());
                return result;
            }
            case "photon_update_timeline" -> {
                var previous = runtime.fxData.timeline().serializeNBT(Platform.getFrozenRegistry());
                var merged = patch(previous, arguments);
                validateTracks(merged);
                runtime.fxData.timeline().deserializeNBT(Platform.getFrozenRegistry(), merged);
                return tagResult(runtime.fxData.timeline().serializeNBT(Platform.getFrozenRegistry()));
            }
            default -> throw new IllegalArgumentException("Unknown/non-batch-edit tool: " + tool);
        }
        var result = new JsonObject();
        result.addProperty("id", changed.id().toString());
        result.addProperty("name", changed.getName());
        result.addProperty("type", changed.name());
        return result;
    }

    private static IFXObject patched(IFXObject original, JsonObject arguments, boolean preserveTransform) throws Exception {
        var wrapper = original.serializeWrapper();
        if (wrapper == null) throw new IllegalStateException("Cannot serialize object");
        var data = patch(wrapper.getCompound("data"), arguments);
        if (preserveTransform && wrapper.getCompound("data").contains("transform")) {
            data.put("transform", wrapper.getCompound("data").get("transform").copy());
        }
        wrapper.put("data", data);
        var changed = IFXObject.CODEC.parse(NbtOps.INSTANCE, wrapper).getOrThrow();
        if (preserveTransform) {
            changed.transform()._setInternalID(original.id());
            changed.transform().set(original.transform(), true);
        }
        return changed;
    }

    private static CompoundTag patch(CompoundTag base, JsonObject arguments) throws Exception {
        var merged = arguments.has("patch") ? NbtJson.merge(base, arguments.getAsJsonObject("patch")) : base.copy();
        if (arguments.has("snbt_patch")) merged.merge(TagParser.parseTag(string(arguments, "snbt_patch")));
        return merged;
    }

    private static void validateTracks(CompoundTag timeline) {
        for (var tag : timeline.getList("tracks", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (!(tag instanceof CompoundTag track) || PhotonRegistries.TIMELINE_TRACKS.get(track.getString("type")) == null) {
                throw new IllegalArgumentException("Unknown timeline track type");
            }
        }
    }

    private static JsonObject batch(FXEditor editor, FXProject project, JsonObject arguments) throws Exception {
        var before = snapshot(project);
        var runtime = draft(before);
        var aliases = new HashMap<String, String>();
        var results = new JsonArray();
        for (var entry : arguments.getAsJsonArray("operations")) {
            var operation = entry.getAsJsonObject();
            var nextArguments = operation.getAsJsonObject("arguments").deepCopy();
            for (var key : List.of("object", "parent")) {
                if (nextArguments.has(key)) {
                    var reference = nextArguments.get(key).getAsString();
                    if (aliases.containsKey(reference)) nextArguments.addProperty(key, aliases.get(reference));
                }
            }
            var result = edit(runtime, string(operation, "tool"), nextArguments);
            results.add(result);
            if (operation.has("alias") && result.has("id")) aliases.put(string(operation, "alias"), result.get("id").getAsString());
        }
        var after = new CompoundTag();
        after.put("fxData", runtime.fxData.serializeNBT(Platform.getFrozenRegistry()));
        commit(editor, project, before, after, "batch (" + results.size() + ")");
        var result = new JsonObject();
        result.add("results", results);
        return result;
    }

    private static void replaceProject(FXEditor editor, FXProject next, Path file) {
        var access = (EditorAccessor) editor;
        access.photonMcp$closeProject();
        access.photonMcp$loadProject(next, file == null ? null : file.toFile());
    }

    private static JsonObject save(FXEditor editor, FXProject project, JsonObject arguments) throws Exception {
        var file = arguments.has("path") ? path(arguments, "path") : editor.getCurrentProjectFile() == null ? null : editor.getCurrentProjectFile().toPath();
        if (file == null) throw new IllegalArgumentException("First save requires path");
        if (!file.toString().endsWith(".fxproj")) throw new IllegalArgumentException("Project file must end in .fxproj");
        Files.createDirectories(file.toAbsolutePath().getParent());
        FXProject.TYPE.saveProjectToFile(project, file.toFile());
        ((EditorAccessor) editor).photonMcp$setProjectFile(file.toFile());
        var result = new JsonObject();
        result.addProperty("path", file.toString());
        result.addProperty("bytes", Files.size(file));
        return result;
    }

    private static JsonObject export(FXProject project, JsonObject arguments) throws Exception {
        var file = path(arguments, "path");
        Files.createDirectories(file.getParent());
        var tag = snapshot(project);
        var format = arguments.has("format") ? string(arguments, "format") : "fx";
        if (format.equals("snbt")) Files.writeString(file, tag.toString());
        else if (format.equals("fx")) NbtIo.write(tag, file);
        else throw new IllegalArgumentException("format must be fx or snbt");
        var result = new JsonObject();
        result.addProperty("path", file.toString());
        result.addProperty("bytes", Files.size(file));
        return result;
    }

    private static JsonObject select(FXEditor editor, String reference) {
        var object = find(editor.runtime, reference);
        var root = editor.hierarchyView.getRootNode();
        var node = findNode(root, object.id());
        if (node == null) throw new IllegalStateException("Hierarchy node not available");
        var inspector = editor.inspectorView.inspector;
        var historyStack = inspector.getHistoryStack();
        inspector.setHistoryStack(null);
        try {
            editor.inspectorView.inspect(object, null, () -> {
                editor.hierarchyView.treeList.setSelected(Set.of(), true);
                editor.sceneView.sceneEditor.setTransformGizmoTarget(null);
            });
        } finally {
            inspector.setHistoryStack(historyStack);
        }
        editor.hierarchyView.treeList.expandNodeAlongPath(node);
        editor.hierarchyView.treeList.setSelected(Set.of(node), true);
        editor.sceneView.sceneEditor.setTransformGizmoTarget(object.transform(), () ->
                editor.historyView.recordSerializableObject(Component.literal("MCP: transform gizmo"), object.transform(), object));
        return detail(object);
    }

    private static FXObjectTreeNode findNode(FXObjectTreeNode node, UUID id) {
        if (node.getKey().id().equals(id)) return node;
        for (var child : node.getChildren()) {
            var found = findNode(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private static JsonObject playback(FXEditor editor, JsonObject arguments) {
        var manager = editor.sceneView.particleManager;
        switch (string(arguments, "action")) {
            case "play" -> manager.play();
            case "pause" -> manager.pause();
            case "stop" -> {
                simulatePreview(editor, 0);
                manager.pause();
            }
            case "restart" -> editor.reloadEffect();
            case "seek" -> {
                simulatePreview(editor, arguments.get("tick").getAsLong());
                manager.pause();
            }
            case "step" -> {
                simulatePreview(editor, Math.max(0, manager.getTime() + (arguments.has("ticks") ? arguments.get("ticks").getAsLong() : 1)));
                manager.pause();
            }
            default -> throw new IllegalArgumentException("Unknown playback action");
        }
        return state(editor);
    }

    private static void simulatePreview(FXEditor editor, long target) {
        if (target < 0) throw new IllegalArgumentException("tick must be nonnegative");
        var view = editor.sceneView;
        var current = view.particleManager.getTime();
        if (target <= current) {
            view.simulateTo(0);
            current = 0;
        }
        while (current < target) {
            var next = current + Math.min(target - current, 10000);
            view.simulateTo(next);
            current = view.particleManager.getTime();
        }
    }

    private static void view(FXEditor editor, JsonObject arguments) {
        var view = editor.sceneView;
        var preview = view.sceneEditor.scene;
        if (arguments.has("center")) preview.setCenter(readVector(arguments, "center"));
        if (arguments.has("zoom")) preview.setZoom(arguments.get("zoom").getAsFloat());
        if (arguments.has("yaw") || arguments.has("pitch")) {
            preview.setCameraYawAndPitch(arguments.has("yaw") ? arguments.get("yaw").getAsFloat() : preview.getRotationYaw(),
                    arguments.has("pitch") ? arguments.get("pitch").getAsFloat() : preview.getRotationPitch());
        }
        if (arguments.has("bloom")) view.setBloomEnabled(arguments.get("bloom").getAsBoolean());
        if (arguments.has("effects")) view.setEffectsEnabled(arguments.get("effects").getAsBoolean());
        if (arguments.has("helpers")) {
            var visible = arguments.get("helpers").getAsBoolean();
            view.setShapeVisible(visible);
            view.setCullBoxVisible(visible);
        }
        if (arguments.has("stats")) view.fxObjectInfoView.setDisplay(arguments.get("stats").getAsBoolean());
        if (arguments.has("scene_mode")) view.setSceneMode(SceneView.SceneMode.valueOf(string(arguments, "scene_mode")));
        if (arguments.has("draw_mode")) view.setDrawMode(SceneView.DrawMode.valueOf(string(arguments, "draw_mode")));
    }

    private static JsonObject describeTimeline(FXEditor editor) {
        var result = tagResult(editor.runtime.fxData.timeline().serializeNBT(Platform.getFrozenRegistry()));
        var types = new JsonObject();
        for (var type : PhotonRegistries.TIMELINE_TRACKS) {
            var data = type.create().writeData(Platform.getFrozenRegistry());
            var entry = tagResult(data);
            entry.add("schema", NbtJson.describe(data));
            types.add(type.name(), entry);
        }
        result.add("trackDefaults", types);
        return result;
    }

    private JsonObject checkpoint(FXEditor editor, FXProject project, JsonObject arguments) {
        var saved = checkpoints.computeIfAbsent(project, key -> new LinkedHashMap<>());
        var action = string(arguments, "action");
        if (action.equals("create")) saved.put(string(arguments, "name"), snapshot(project));
        else if (action.equals("restore")) {
            var name = string(arguments, "name");
            var data = saved.get(name);
            if (data == null) throw new IllegalArgumentException("Unknown checkpoint " + name);
            commit(editor, project, snapshot(project), data.copy(), "restore " + name);
        } else if (!action.equals("list")) throw new IllegalArgumentException("Unknown checkpoint action");
        var result = new JsonObject();
        var names = new JsonArray();
        saved.keySet().forEach(names::add);
        result.add("checkpoints", names);
        return result;
    }

    private static JsonObject resources(JsonObject arguments) throws Exception {
        var result = new JsonObject();
        var categories = new JsonArray();
        for (var resource : new FXProject().getResources().resources) {
            var entry = new JsonObject();
            entry.addProperty("name", resource.getName());
            entry.addProperty("extension", resource.getFileExtension());
            categories.add(entry);
        }
        result.add("categories", categories);
        result.add("entries", ResourceTools.entries());
        result.addProperty("assetsDirectory", LDLib2.getAssetsDir().getAbsolutePath());
        var directory = arguments.has("directory") ? path(arguments, "directory") : LDLib2.getAssetsDir().toPath();
        var files = new JsonArray();
        if (Files.isDirectory(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile).limit(1000).forEach(file -> files.add(file.toAbsolutePath().toString()));
            }
        }
        result.add("files", files);
        return result;
    }

    private static JsonObject importTexture(JsonObject arguments) throws Exception {
        var source = path(arguments, "path");
        var name = string(arguments, "name");
        var target = LDLib2.getAssetsDir().toPath().resolve("photon/textures/" + name + ".png");
        Files.createDirectories(target.getParent());
        var loaded = javax.imageio.ImageIO.read(source.toFile());
        if (loaded == null) throw new IllegalArgumentException("Cannot read texture image");
        javax.imageio.ImageIO.write(loaded, "png", target.toFile());
        var identifier = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("photon", "textures/" + name + ".png");
        try (var input = Files.newInputStream(target)) {
            var nativeImage = com.mojang.blaze3d.platform.NativeImage.read(input);
            var texture = new net.minecraft.client.renderer.texture.DynamicTexture(nativeImage);
            Minecraft.getInstance().getTextureManager().register(identifier, texture);
        }
        var result = new JsonObject();
        result.addProperty("path", target.toAbsolutePath().toString());
        result.addProperty("texture", identifier.toString());
        return result;
    }

    private static Vector3f readVector(JsonObject arguments, String key) {
        var values = arguments.getAsJsonArray(key);
        if (values.size() != 3) throw new IllegalArgumentException(key + " must have three components");
        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }

    private static JsonObject component(JsonObject arguments) {
        var name = string(arguments, "type");
        var category = string(arguments, "category");
        CompoundTag data;
        switch (category) {
            case "material" -> {
                var holder = PhotonRegistries.MATERIALS.get(name);
                if (holder == null) throw new IllegalArgumentException("Unknown material: " + name);
                data = holder.value().get().serializeWrapper();
            }
            case "shape" -> {
                var holder = PhotonRegistries.SHAPES.get(name);
                if (holder == null) throw new IllegalArgumentException("Unknown shape: " + name);
                data = holder.value().get().serializeWrapper();
            }
            case "number_function" -> {
                var holder = PhotonRegistries.NUMBER_FUNCTIONS.get(name);
                if (holder == null) throw new IllegalArgumentException("Unknown number function: " + name);
                data = holder.value().get().serializeWrapper();
            }
            default -> throw new IllegalArgumentException("category must be material, shape or number_function");
        }
        if (data == null) throw new IllegalStateException("Cannot serialize component");
        var result = tagResult(data);
        result.add("schema", NbtJson.describe(data));
        return result;
    }

    private static String string(JsonObject arguments, String key) {
        if (!arguments.has(key)) throw new IllegalArgumentException("Missing argument: " + key);
        return arguments.get(key).getAsString();
    }

    public final class ScriptApi {
        public JsonObject call(String name, Map<String, Object> arguments) throws Exception {
            if (name.equals("photon_execute_code") || name.equals("photon_api_lookup") || name.equals("photon_screenshot")) {
                throw new IllegalArgumentException("Invoke this tool separately, not inside synchronous code: " + name);
            }
            return dispatch(name, gson.toJsonTree(arguments).getAsJsonObject());
        }

        public IFXObject object(String reference) {
            return find(requireEditor().runtime, reference);
        }
    }

    private Map<String, Object> scriptContext(JsonObject arguments) {
        var context = new HashMap<String, Object>();
        var editor = activeEditor();
        var project = editor != null && editor.getCurrentProject() instanceof FXProject value ? value : null;
        context.put("client", Minecraft.getInstance());
        context.put("editor", editor);
        context.put("project", project);
        context.put("fx", project == null ? null : project.getFx());
        context.put("runtime", editor == null ? null : editor.runtime);
        context.put("scene", editor == null ? null : editor.sceneView.sceneEditor.scene);
        context.put("timeline", project == null ? null : project.getFx().getFxData().timeline());
        context.put("mcp", new ScriptApi());
        context.put("api", ApiIntrospection.class);
        context.put("args", arguments.has("args") ? gson.fromJson(arguments.get("args"), Map.class) : Map.of());
        return context;
    }

    private JsonObject executeCode(JsonObject arguments) throws Exception {
        if (arguments.has("reset_context") && arguments.get("reset_context").getAsBoolean()) codeRunner.reset();
        var editor = activeEditor();
        var project = editor != null && editor.getCurrentProject() instanceof FXProject value ? value : null;
        var before = project == null ? null : snapshot(project);
        var history = !arguments.has("record_history") || arguments.get("record_history").getAsBoolean();
        JsonObject result;
        try {
            result = codeRunner.execute(string(arguments, "code"), scriptContext(arguments));
        } catch (Exception exception) {
            if (history && before != null && editor.getCurrentProject() == project && !before.equals(snapshot(project))) {
                restore(editor, project, before);
            }
            throw exception;
        }
        var changed = before != null && editor.getCurrentProject() == project && !before.equals(snapshot(project));
        if (changed && history) {
            commit(editor, project, before, snapshot(project), "execute code");
        } else if (arguments.has("reload") && arguments.get("reload").getAsBoolean() && editor != null) {
            synchronizeHistory(editor);
            editor.timelineView.rebuild();
            editor.reloadEffect();
        }
        result.addProperty("fxChanged", changed);
        result.addProperty("historyRecorded", changed && history);
        return result;
    }

    private JsonObject lookupApi(JsonObject arguments) throws Exception {
        Class<?> type;
        if (arguments.has("class")) {
            type = Class.forName(string(arguments, "class"), false, EditorBridge.class.getClassLoader());
        } else if (arguments.has("expression")) {
            var expression = "def inspected = (" + string(arguments, "expression") + "); return api.describe(inspected instanceof Class ? inspected : inspected.getClass(), args.filter ?: '', args.inherited != false)";
            var contextArguments = new JsonObject();
            contextArguments.add("args", arguments);
            return codeRunner.execute(expression, scriptContext(contextArguments)).getAsJsonObject("result");
        } else throw new IllegalArgumentException("Provide class or expression");
        return ApiIntrospection.describe(type, arguments.has("filter") ? string(arguments, "filter") : "",
                !arguments.has("inherited") || arguments.get("inherited").getAsBoolean());
    }

    private static JsonObject fxpack(FXEditor editor, JsonObject arguments) throws Exception {
        var file = path(arguments, "path").toFile();
        if (!file.getName().endsWith(".fxpack")) throw new IllegalArgumentException("Expected .fxpack path");
        var action = string(arguments, "action");
        var result = new JsonObject();
        result.addProperty("path", file.getAbsolutePath());
        if (action.equals("export")) {
            var exported = com.lowdragmc.photon.client.fx.fxpack.FXPackExporter.exportInto(file,
                    string(arguments, "namespace"), string(arguments, "name"), project(editor).getFx(), Platform.getFrozenRegistry());
            result.addProperty("fxId", exported.fxId().toString());
            result.addProperty("fileCount", exported.fileCount());
            result.add("warnings", gson.toJsonTree(exported.warnings()));
            if (arguments.has("include_assets")) {
                var embedded = new JsonArray();
                try (var archive = java.nio.file.FileSystems.newFileSystem(file.toPath(), Map.of())) {
                    for (var requested : arguments.getAsJsonArray("include_assets")) {
                        var id = net.minecraft.resources.ResourceLocation.parse(requested.getAsString());
                        var resource = Minecraft.getInstance().getResourceManager().getResource(id)
                                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + id));
                        var destination = archive.getPath("/assets/" + id.getNamespace() + "/" + id.getPath());
                        Files.createDirectories(destination.getParent());
                        try (var source = resource.open()) {
                            Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        embedded.add(id.toString());
                    }
                    try (var entries = Files.walk(archive.getPath("/assets"))) {
                        result.addProperty("fileCount", entries.filter(Files::isRegularFile).count());
                    }
                }
                result.add("embeddedAssets", embedded);
            }
        } else if (action.equals("remove")) {
            com.lowdragmc.photon.client.fx.fxpack.FXPacks.removeFx(file, net.minecraft.resources.ResourceLocation.parse(string(arguments, "fx_id")));
        } else if (!action.equals("list")) throw new IllegalArgumentException("Unknown fxpack action");
        var effects = new JsonArray();
        com.lowdragmc.photon.client.fx.fxpack.FXPacks.listFx(file).forEach(id -> effects.add(id.toString()));
        result.add("effects", effects);
        if (!action.equals("list")) com.lowdragmc.photon.client.fx.FXHelper.clearCache();
        return result;
    }

    private static JsonObject editorAction(FXEditor editor, JsonObject arguments) {
        var result = new JsonObject();
        if (string(arguments, "action").equals("list")) {
            var actions = new JsonArray();
            for (var action : editor.keymap.getActions()) {
                var item = new JsonObject();
                item.addProperty("id", action.id().toString());
                item.addProperty("name", action.displayName().getString());
                item.addProperty("category", action.category());
                actions.add(item);
            }
            result.add("actions", actions);
            return result;
        }
        if (!string(arguments, "action").equals("invoke")) throw new IllegalArgumentException("Unknown editor action");
        var action = editor.keymap.getAction(net.minecraft.resources.ResourceLocation.parse(string(arguments, "id")))
                .orElseThrow(() -> new IllegalArgumentException("Unknown native action"));
        com.lowdragmc.lowdraglib2.gui.ui.UIElement focused = arguments.has("focus") ? switch (string(arguments, "focus")) {
            case "hierarchy" -> editor.hierarchyView;
            case "scene" -> editor.sceneView;
            case "timeline" -> editor.timelineView;
            case "inspector" -> editor.inspectorView;
            case "resources" -> editor.resourceView;
            default -> throw new IllegalArgumentException("Unknown focus panel");
        } : editor;
        var context = new com.lowdragmc.lowdraglib2.editor.keymap.KeymapContext() {
            public com.lowdragmc.lowdraglib2.editor.keymap.KeyChord chord() { return action.defaultPrimary(); }
            public boolean isFocusWithin(Class<?> type) { return type.isInstance(focused); }
            public boolean isTextInputFocused() { return false; }
            public boolean hasProject() { return editor.getCurrentProject() != null; }
        };
        result.addProperty("invoked", action.when().test(context) && action.handler().run(context));
        return result;
    }

    private static Path path(JsonObject arguments, String key) {
        var path = Path.of(string(arguments, key));
        return (path.isAbsolute() ? path : Minecraft.getInstance().gameDirectory.toPath().resolve(path)).toAbsolutePath().normalize();
    }

    public static void releaseProject(FXEditor editor) {
        if (editor.getCurrentProject() instanceof FXProject project) retainedObjects.remove(project);
    }

    public static void synchronizeHistory(FXEditor editor) {
        if (!(editor.getCurrentProject() instanceof FXProject project) || editor.runtime == null) return;
        var objects = project.getFx().getFxData().objects();
        if (objects.size() + 1 != editor.runtime.objects.size()
                || objects.stream().anyMatch(object -> editor.runtime.objects.get(object.id()) != object)) {
            restore(editor, project, snapshot(project));
        }
    }
}
