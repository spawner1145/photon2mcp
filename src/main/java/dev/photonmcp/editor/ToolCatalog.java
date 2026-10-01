package dev.photonmcp.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ToolCatalog {
    private ToolCatalog() {}

    public static JsonArray tools() {
        var tools = new JsonArray();
        add(tools, "photon_get_server_status", "MCP listener status and recently active client sessions.", "{}", "", true);
        add(tools, "photon_get_editor_state", "Find the currently open Photon editor, project, playback and selection.", "{}", "", true);
        add(tools, "photon_open_editor", "Open the Photon editor screen, using the same EditorWindow entry point as /photon_editor.", "{}", "", false);
        add(tools, "photon_execute_code", "Execute unrestricted Groovy/JVM code on the game main thread, equivalent to Blender execute code. Bindings: client, editor, project, fx, runtime, scene, timeline, mcp (call/object), api (reflection), args. Variables persist until reset_context. Returns stdout and result. Native Photon/LDLib/KilaGraph APIs are accessible, not only named MCP tools. Default record_history captures FX changes; files/GPU/world side effects cannot roll back. Do not block the game thread or await screenshots here.", "{\"code\":{\"type\":\"string\"},\"args\":{\"type\":\"object\"},\"reset_context\":{\"type\":\"boolean\"},\"record_history\":{\"type\":\"boolean\"},\"reload\":{\"type\":\"boolean\"}}", "code", false);
        add(tools, "photon_api_lookup", "Query real JVM fields, annotations, method overloads, parameter types, constructors and enums. class is a fully qualified type; alternatively expression is Groovy referencing live bindings, e.g. editor.sceneView or mcp.object('Sparks'). Use before native code calls rather than guessing APIs.", "{\"class\":{\"type\":\"string\"},\"expression\":{\"type\":\"string\"},\"filter\":{\"type\":\"string\"},\"inherited\":{\"type\":\"boolean\"}}", "", true);
        add(tools, "photon_list_api_classes", "Discover actual JVM classes from installed mods and embedded libraries, including Photon, LDLib and KilaGraph. Defaults to com.lowdragmc.photon.; optionally filter names or change package prefix.", "{\"package_prefix\":{\"type\":\"string\"},\"filter\":{\"type\":\"string\"}}", "", true);
        add(tools, "photon_editor_action", "List or invoke registered native editor actions such as copy/paste, keymap, views and settings. Optional focus identifies hierarchy/scene/timeline/inspector/resources. Invoke uses the native context predicate; dialogs are not automatically completed. Use direct tools/code for parameterized automation.", "{\"action\":{\"type\":\"string\",\"enum\":[\"list\",\"invoke\"]},\"id\":{\"type\":\"string\"},\"focus\":{\"type\":\"string\",\"enum\":[\"hierarchy\",\"scene\",\"timeline\",\"inspector\",\"resources\"]}}", "action", false);
        add(tools, "photon_fxpack", "Export current effect with referenced assets using Photon's native FXPackExporter, list contained effects, or remove an effect. export takes namespace/name; remove takes fx_id. include_assets optionally embeds explicit resource IDs, even mod-provided assets the native exporter skips. Resource reload mounts exported packs. External files are not FX undoable.", "{\"action\":{\"type\":\"string\",\"enum\":[\"export\",\"list\",\"remove\"]},\"path\":{\"type\":\"string\"},\"namespace\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"},\"fx_id\":{\"type\":\"string\"},\"include_assets\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}", "action,path", false);
        add(tools, "photon_list_object_types", "Discover installed Photon object, shape, number function, material and timeline track type names.", "{}", "", true);
        add(tools, "photon_describe_component", "Get default JSON/SNBT and schema for a material, shape or number function, including curves, gradients and shader materials. Use these wrappers inside emitter data patches.", "{\"category\":{\"type\":\"string\",\"enum\":[\"material\",\"shape\",\"number_function\"]},\"type\":{\"type\":\"string\"}}", "category,type", true);
        add(tools, "photon_describe_object_type", "Get default object JSON, exact typed SNBT and a recursive NBT field schema. Never guess emitter property names.", "{\"type\":{\"type\":\"string\"}}", "type", true);
        add(tools, "photon_get_scene_info", "List authored objects and hierarchy, UUIDs, types and transforms, plus preview camera state.", "{}", "", true);
        add(tools, "photon_get_object", "Inspect complete object wrapper JSON/SNBT and configuration schema. UUID or unique name is accepted.", "{\"object\":{\"type\":\"string\"}}", "object", true);
        add(tools, "photon_create_object", "Create an emitter/beam/trail/force field/group. patch deep-merges the object's data, not its wrapper. Returns object UUID. Undoable.", "{\"type\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"},\"parent\":{\"type\":\"string\"},\"patch\":{\"type\":\"object\"},\"snbt_patch\":{\"type\":\"string\"}}", "type", false);
        add(tools, "photon_update_object", "Deep-merge object data JSON or typed SNBT. Arrays replace; JSON null removes a key. Object identity and hierarchy are retained; use transform/reparent tools for structure. Undoable.", "{\"object\":{\"type\":\"string\"},\"patch\":{\"type\":\"object\"},\"snbt_patch\":{\"type\":\"string\"}}", "object", false);
        add(tools, "photon_set_transform", "Set parent-local position/scale and Euler rotation in degrees (XYZ). Undoable.", "{\"object\":{\"type\":\"string\"},\"position\":{\"type\":\"array\",\"items\":{\"type\":\"number\"},\"minItems\":3,\"maxItems\":3},\"rotation\":{\"type\":\"array\",\"items\":{\"type\":\"number\"},\"minItems\":3,\"maxItems\":3},\"scale\":{\"type\":\"array\",\"items\":{\"type\":\"number\"},\"minItems\":3,\"maxItems\":3}}", "object", false);
        add(tools, "photon_reparent_object", "Move an object under a parent UUID/name (root allowed). keep_world defaults to true. Undoable.", "{\"object\":{\"type\":\"string\"},\"parent\":{\"type\":\"string\"},\"keep_world\":{\"type\":\"boolean\"}}", "object,parent", false);
        add(tools, "photon_duplicate_object", "Copy one object (not its children) with a new UUID, keeping its settings and local transform. Undoable.", "{\"object\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}", "object", false);
        add(tools, "photon_delete_object", "Delete one object and its descendants. Undoable.", "{\"object\":{\"type\":\"string\"}}", "object", false);
        add(tools, "photon_select_object", "Select an authored object and display its inspector and transform gizmo.", "{\"object\":{\"type\":\"string\"}}", "object", false);
        add(tools, "photon_playback", "Control editor simulation. seek uses integer Minecraft ticks (20 ticks/second); seek leaves playback paused.", "{\"action\":{\"type\":\"string\",\"enum\":[\"play\",\"pause\",\"stop\",\"restart\",\"seek\",\"step\"]},\"tick\":{\"type\":\"integer\",\"minimum\":0},\"ticks\":{\"type\":\"integer\"}}", "action", false);
        add(tools, "photon_set_view", "Set scene preview camera (degrees), zoom, center, bloom, effect visibility, helpers, stats overlay, scene mode and draw mode. Does not modify saved FX data.", "{\"yaw\":{\"type\":\"number\"},\"pitch\":{\"type\":\"number\"},\"zoom\":{\"type\":\"number\",\"exclusiveMinimum\":0},\"center\":{\"type\":\"array\",\"items\":{\"type\":\"number\"},\"minItems\":3,\"maxItems\":3},\"bloom\":{\"type\":\"boolean\"},\"effects\":{\"type\":\"boolean\"},\"helpers\":{\"type\":\"boolean\"},\"stats\":{\"type\":\"boolean\"},\"scene_mode\":{\"type\":\"string\"},\"draw_mode\":{\"type\":\"string\"}}", "", false);
        add(tools, "photon_screenshot", "Capture a newly rendered frame as MCP PNG image content. region: scene (default), editor, or screen. Optional path saves the PNG; max_size defaults to 1280. No disk file is required.", "{\"region\":{\"type\":\"string\",\"enum\":[\"scene\",\"editor\",\"screen\"]},\"max_size\":{\"type\":\"integer\",\"minimum\":64,\"maximum\":4096},\"path\":{\"type\":\"string\"}}", "", true);
        add(tools, "photon_new_project", "Replace current project with an empty Photon project. Save first if needed; no modal dialog is opened.", "{}", "", false);
        add(tools, "photon_open_project", "Open an existing .fxproj or .fx file. Replaces current project without a modal save prompt.", "{\"path\":{\"type\":\"string\"}}", "path", false);
        add(tools, "photon_save_project", "Save .fxproj directly to path or the current project file, without file dialogs.", "{\"path\":{\"type\":\"string\"}}", "", false);
        add(tools, "photon_export_effect", "Write Photon .fx NBT or SNBT to a file. Use photon_fxpack to export dependent assets.", "{\"path\":{\"type\":\"string\"},\"format\":{\"type\":\"string\",\"enum\":[\"fx\",\"snbt\"]}}", "path", false);
        add(tools, "photon_get_project", "Inspect complete current FX data (objects plus timeline) as JSON and lossless SNBT.", "{}", "", true);
        add(tools, "photon_describe_timeline", "Inspect current timeline and defaults/schema for each installed timeline track type.", "{}", "", true);
        add(tools, "photon_update_timeline", "Deep-merge timeline JSON/SNBT, preserving numeric NBT types. tracks/markers arrays replace. Undoable.", "{\"patch\":{\"type\":\"object\"},\"snbt_patch\":{\"type\":\"string\"}}", "", false);
        add(tools, "photon_list_resources", "List Photon resource categories and accepted file extensions, and optionally available project/global files.", "{\"directory\":{\"type\":\"string\"}}", "", true);
        add(tools, "photon_import_texture", "Copy an image into the LDLib Photon assets directory, register its game texture identifier and refresh resources. Returns the texture ID for material configuration.", "{\"path\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}}", "path,name", false);
        add(tools, "photon_read_resource", "Inspect a builtin/file material, shader graph, render graph, curve, gradient or mesh as lossless SNBT and JSON. Find category/path with photon_list_resources.", "{\"category\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"}}", "category,path", true);
        add(tools, "photon_write_resource", "Create or overwrite a Photon resource file. source optionally copies an existing builtin/file resource; snbt supplies a full compound; patch deep-merges. Returns the portable resourcePath usable by emitter materials. File writes are not part of FX undo/checkpoints.", "{\"category\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"},\"source\":{\"type\":\"string\"},\"snbt\":{\"type\":\"string\"},\"patch\":{\"type\":\"object\"}}", "category,path", false);
        add(tools, "photon_batch", "Atomically apply create/update/transform/reparent/duplicate/delete/timeline operations with one undo step and one preview reload. Created UUIDs may be referenced using an operation's alias. All steps roll back on error.", "{\"operations\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"tool\":{\"type\":\"string\"},\"arguments\":{\"type\":\"object\"},\"alias\":{\"type\":\"string\"}},\"required\":[\"tool\",\"arguments\"]}}}", "operations", false);
        add(tools, "photon_checkpoint", "Create/list/restore an in-memory FX checkpoint. Checkpoints are editor-project-specific and disappear on game exit. Restore is undoable.", "{\"action\":{\"type\":\"string\",\"enum\":[\"create\",\"list\",\"restore\"]},\"name\":{\"type\":\"string\"}}", "action", false);
        add(tools, "photon_undo_redo", "Use the same history as the in-game editor. action: undo or redo.", "{\"action\":{\"type\":\"string\",\"enum\":[\"undo\",\"redo\"]}}", "action", false);
        return tools;
    }

    private static void add(JsonArray tools, String name, String description, String properties, String required, boolean readOnly) {
        var tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", JsonParser.parseString(properties));
        var requiredFields = new JsonArray();
        if (!required.isEmpty()) for (var field : required.split(",")) requiredFields.add(field);
        schema.add("required", requiredFields);
        tool.add("inputSchema", schema);
        var annotations = new JsonObject();
        annotations.addProperty("readOnlyHint", readOnly);
        annotations.addProperty("destructiveHint", !readOnly);
        annotations.addProperty("openWorldHint", name.equals("photon_execute_code") || name.equals("photon_api_lookup"));
        tool.add("annotations", annotations);
        tools.add(tool);
    }
}
