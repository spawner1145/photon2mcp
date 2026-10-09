import assert from 'node:assert/strict';

const endpoint = process.argv[2] ?? 'http://127.0.0.1:8765/mcp';
let sequence = 0;
let session;
let assertions = 0;

async function rpc(method, params = {}) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' };
  if (session) headers['Mcp-Session-Id'] = session;
  const response = await fetch(endpoint, { method: 'POST', headers, body: JSON.stringify({ jsonrpc: '2.0', id: ++sequence, method, params }) });
  session = response.headers.get('Mcp-Session-Id') ?? session;
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.error, undefined, JSON.stringify(body));
  return body.result;
}

async function tool(name, argumentsValue = {}) {
  const result = await rpc('tools/call', { name, arguments: argumentsValue });
  assert.equal(result.isError, false, JSON.stringify(result));
  assertions++;
  return result.structuredContent;
}

try {
  await rpc('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'integration-test', version: '1' } });
  const listed = await rpc('tools/list');
  assert.ok(listed.tools.length >= 30);
  await tool('photon_open_editor');
  await tool('photon_new_project');
  const types = await tool('photon_list_object_types');
  assert.ok(types.objects.includes('particle_emitter'));
  const defaults = await tool('photon_describe_object_type', { type: 'particle_emitter' });
  assert.ok(defaults.dataSchema.fields.config);
  for (const [category, type] of [['material', 'texture'], ['shape', 'sphere'], ['number_function', 'curve']]) {
    const component = await tool('photon_describe_component', { category, type });
    assert.equal(component.json.type, type);
  }
  const group = await tool('photon_create_object', { type: 'empty', name: 'Smoke Group' });
  const emitter = await tool('photon_create_object', { type: 'particle_emitter', name: 'Smoke Emitter', parent: group.id });
  await tool('photon_set_transform', { object: emitter.id, position: [1, 2, 3], rotation: [0, 45, 0], scale: [0.75, 0.75, 0.75] });
  await tool('photon_update_object', { object: emitter.id, patch: { name: 'Updated Emitter', config: { maxParticles: 128, emission: { emissionRate: { data: { number: 2 } } } } } });
  const updated = await tool('photon_get_object', { object: emitter.id });
  assert.equal(updated.name, 'Updated Emitter');
  assert.deepEqual(updated.transform.position, [1, 2, 3]);
  assert.equal(updated.transform.parent, group.id);
  assert.equal(updated.json.data.config.maxParticles, 128);
  await tool('photon_create_object', { type: 'empty', name: 'Child', parent: emitter.id });
  const duplicate = await tool('photon_duplicate_object', { object: emitter.id, name: 'Duplicate' });
  assert.notEqual(duplicate.id, emitter.id);
  await tool('photon_reparent_object', { object: duplicate.id, parent: 'root', keep_world: true });
  await tool('photon_select_object', { object: emitter.id });
  assert.equal((await tool('photon_get_editor_state')).inspectedObject, emitter.id);
  await tool('photon_playback', { action: 'seek', tick: 20 });
  assert.equal((await tool('photon_get_editor_state')).playing, false);
  await tool('photon_checkpoint', { action: 'create', name: 'before-delete' });
  const deleted = await tool('photon_delete_object', { object: group.id });
  assert.equal(deleted.deleted, 3);
  await tool('photon_undo_redo', { action: 'undo' });
  assert.equal((await tool('photon_get_scene_info')).objectCount, 4);
  await tool('photon_undo_redo', { action: 'redo' });
  assert.equal((await tool('photon_get_scene_info')).objectCount, 1);
  await tool('photon_checkpoint', { action: 'restore', name: 'before-delete' });
  const failedBatch = await rpc('tools/call', { name: 'photon_batch', arguments: { operations: [
    { tool: 'photon_create_object', arguments: { type: 'empty', name: 'Must Not Persist' } },
    { tool: 'photon_set_transform', arguments: { object: 'missing', position: [0, 0, 0] } },
  ] } });
  assert.equal(failedBatch.isError, true);
  assert.equal((await tool('photon_get_scene_info')).objectCount, 4);
  const batch = await tool('photon_batch', { operations: [
    { tool: 'photon_create_object', alias: 'created', arguments: { type: 'empty', name: 'Batch Object' } },
    { tool: 'photon_set_transform', arguments: { object: 'created', position: [4, 5, 6] } },
  ] });
  const created = await tool('photon_get_object', { object: batch.results[0].id });
  assert.deepEqual(created.transform.position, [4, 5, 6]);
  const timeline = await tool('photon_describe_timeline');
  await tool('photon_update_timeline', { patch: { tracks: [{ type: 'speed', data: timeline.trackDefaults.speed.json }], markers: [{ tick: 5, name: 'Test marker' }] } });
  assert.equal((await tool('photon_describe_timeline')).json.markers[0].name, 'Test marker');
  await tool('photon_set_view', { yaw: -135, pitch: 25, zoom: 5, bloom: true, helpers: false });
  const saved = await tool('photon_save_project', { path: 'mcp-smoke/project.fxproj' });
  assert.ok(saved.bytes > 0);
  await tool('photon_export_effect', { path: 'mcp-smoke/effect.fx' });
  await tool('photon_export_effect', { path: 'mcp-smoke/effect.snbt', format: 'snbt' });
  await tool('photon_open_project', { path: saved.path });
  assert.equal((await tool('photon_get_scene_info')).objectCount, 5);
  await tool('photon_create_object', { type: 'empty', name: 'Baseline Undo Probe' });
  await tool('photon_undo_redo', { action: 'undo' });
  assert.equal((await tool('photon_get_scene_info')).objectCount, 5);
  await tool('photon_undo_redo', { action: 'redo' });
  assert.equal((await tool('photon_get_scene_info')).objectCount, 6);
  await tool('photon_undo_redo', { action: 'undo' });
  await tool('photon_playback', { action: 'seek', tick: 20 });
  const screenshot = await rpc('tools/call', { name: 'photon_screenshot', arguments: { region: 'editor', max_size: 1280, path: 'mcp-smoke/editor.png' } });
  assert.equal(screenshot.isError, false, JSON.stringify(screenshot));
  const image = screenshot.content.find(content => content.type === 'image');
  assert.equal(image.mimeType, 'image/png');
  assert.equal(Buffer.from(image.data, 'base64').subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  const texture = await tool('photon_import_texture', { path: 'mcp-smoke/editor.png', name: 'mcp_smoke_import' });
  assert.equal(texture.texture, 'photon:textures/mcp_smoke_import.png');
  const resources = await tool('photon_list_resources');
  const shaderTemplate = resources.entries.find(entry => entry.category === 'shader_graph');
  assert.ok(shaderTemplate);
  const shader = await tool('photon_read_resource', { category: 'shader_graph', path: shaderTemplate.resourcePath });
  assert.ok(shader.snbt.length > 0);
  const written = await tool('photon_write_resource', { category: 'shader_graph', source: shaderTemplate.resourcePath, path: 'ldlib2/assets/photon/mcp-smoke.shader_graph.nbt' });
  const copiedShader = await tool('photon_read_resource', { category: 'shader_graph', path: written.resourcePath });
  assert.deepEqual(copiedShader.json, written.json);
  console.log(`PASS: ${assertions} successful game tool calls; hierarchy, patches, batch rollback, history, timeline, persistence, texture import and PNG capture verified.`);
} finally {
  if (session) await fetch(endpoint, { method: 'DELETE', headers: { 'Mcp-Session-Id': session } }).catch(() => {});
}
