// Replaces the current project. Run in the isolated instance started by launch-test.ps1.
import assert from 'node:assert/strict';

const endpoint = process.argv[2] ?? 'http://127.0.0.1:8766/mcp';
let sequence = 0;
let calls = 0;
async function rpc(method, params = {}) {
  const response = await fetch(endpoint, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', id: ++sequence, method, params }),
  });
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.error, undefined, JSON.stringify(body));
  return body.result;
}
async function tool(name, args = {}) {
  const result = await rpc('tools/call', { name, arguments: args });
  assert.equal(result.isError, false, JSON.stringify(result));
  calls++;
  return result.structuredContent;
}
async function invoke(id, focus) {
  assert.equal((await tool('photon_editor_action', { action: 'invoke', id, focus })).invoked, true);
}
async function count(expected) {
  const scene = await tool('photon_get_scene_info');
  assert.equal(scene.objectCount, expected);
  return scene;
}

const initialized = await rpc('initialize', {
  protocolVersion: '2025-11-25', capabilities: {},
  clientInfo: { name: 'photon-2.2.8-regression', version: '1' },
});
assert.equal(initialized.serverInfo.version, '0.2.2');
await tool('photon_open_editor');
await tool('photon_new_project');
const types = await tool('photon_list_object_types');
assert.ok(types.objects.includes('light'));
assert.ok(types.objects.includes('fog_volume'));
assert.ok(types.materials.includes('kila_material'));
for (const type of ['light', 'fog_volume']) {
  assert.ok((await tool('photon_describe_object_type', { type })).dataSchema.fields.config);
}
const kila = await tool('photon_describe_component', { category: 'material', type: 'kila_material' });
assert.equal(kila.json.type, 'kila_material');
assert.ok(kila.schema);

const group = await tool('photon_create_object', { type: 'empty', name: 'Upgrade Group' });
const light = await tool('photon_create_object', {
  type: 'light', name: 'Upgrade Light', parent: group.id,
  patch: { config: { type: 'Spot', intensity: { data: { number: 12 } } } },
});
const fog = await tool('photon_create_object', {
  type: 'fog_volume', name: 'Upgrade Fog', parent: group.id,
  patch: { config: { shape: 'Sphere', density: { data: { number: 0.25 } } } },
});
const emitter = await tool('photon_create_object', {
  type: 'particle_emitter', name: 'Upgrade Kila', parent: group.id,
});
await tool('photon_update_object', { object: fog.id, patch: { config: { edgeFalloff: 0.5 } } });
assert.equal((await tool('photon_get_object', { object: light.id })).json.data.config.type, 'Spot');
assert.equal((await tool('photon_get_object', { object: fog.id })).json.data.config.edgeFalloff, 0.5);
const native = await tool('photon_execute_code', { code: `
import com.lowdragmc.photon.client.gameobject.emitter.data.material.kila.KilaPresets
def target = mcp.object('${emitter.id}')
target.config.renderer.materials[0].material = KilaPresets.GLOW.create()
def track = PhotonRegistries.TIMELINE_TRACKS.get('animation').create()
track.targetId(mcp.object('${light.id}').id())
def property = PositionPropertyType.INSTANCE.create(mcp.object('${light.id}'))
property.putKey(0, 0f, 0f)
property.putKey(0, 20f, 2f)
track.properties().add(property)
timeline.tracks().add(track)
return target.config.renderer.materials[0].material.serializeWrapper().getString('type')
` });
assert.equal(native.result, 'kila_material');
assert.equal(native.historyRecorded, true);
const original = await tool('photon_get_object', { object: emitter.id });
assert.match(original.snbt, /kila_material/);

// Copy does not create objects. Paste/duplicate must reach the requested real UI panel.
await tool('photon_select_object', { object: group.id });
await invoke('ldlib2:editor.copy', 'hierarchy');
await count(4);
await invoke('ldlib2:editor.paste', 'scene');
await count(8);
const clipboard = await tool('photon_execute_code', { record_history: false, code: `
def targets = timeline.tracks().collect { it.targetId() }
assert targets.size() == 2
assert targets.toSet().size() == 2
assert targets.every { runtime.objects.containsKey(it) }
return targets.collect { it.toString() }
` });
assert.ok(clipboard.result.includes(light.id));
await invoke('ldlib2:editor.undo', 'scene');
await count(4);
await invoke('ldlib2:editor.redo', 'hierarchy');
await count(8);
await tool('photon_select_object', { object: group.id });
await invoke('ldlib2:editor.duplicate', 'hierarchy');
await count(12);
await invoke('ldlib2:editor.undo', 'hierarchy');
await count(8);
await invoke('ldlib2:editor.redo', 'scene');
await count(12);

// Verify the new objects/material and remapped timeline survive native file serialization.
await tool('photon_playback', { action: 'stop' });
const before = await tool('photon_get_project');
await tool('photon_save_project', { path: 'mcp-upgrade/upgrade-2.2.8.fxproj' });
await tool('photon_export_effect', { path: 'mcp-upgrade/upgrade-2.2.8.fx' });
await tool('photon_new_project');
await tool('photon_open_project', { path: 'mcp-upgrade/upgrade-2.2.8.fxproj' });
await tool('photon_playback', { action: 'stop' });
const after = (await tool('photon_get_project')).json;
// Native timeline interpolation can introduce float rounding in transform components.
// Keep identity, hierarchy, all configuration and timeline data exact.
for (let i = 0; i < before.json.fxData.fxObjects.length; i++) {
  const expected = before.json.fxData.fxObjects[i].data.transform;
  const actual = after.fxData.fxObjects[i].data.transform;
  for (const key of ['localPosition', 'localRotation', 'localScale']) {
    assert.equal(actual[key].length, expected[key].length);
    actual[key].forEach((value, component) => assert.ok(Math.abs(value - expected[key][component]) < 1e-5));
    actual[key] = expected[key];
  }
}
assert.deepEqual(after, before.json);
await count(12);
await tool('photon_playback', { action: 'seek', tick: 10 });
const image = await rpc('tools/call', {
  name: 'photon_screenshot', arguments: { region: 'editor', max_size: 1280, path: 'mcp-upgrade/editor-2.2.8.png' },
});
assert.equal(image.isError, false, JSON.stringify(image));
assert.equal(image.content.find(item => item.type === 'image').mimeType, 'image/png');
console.log(`PASS: ${calls} upgrade tool calls; lights, fog volumes, Kila preset, native clipboard focus, subtree/timeline remapping, undo/redo, project roundtrip and GPU PNG.`);
