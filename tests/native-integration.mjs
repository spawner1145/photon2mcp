import assert from 'node:assert/strict';

const endpoint = process.argv[2] ?? 'http://127.0.0.1:8765/mcp';
let sequence = 0;
let calls = 0;
async function rpc(method, params) {
  const response = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', id: ++sequence, method, params }) });
  const body = await response.json();
  assert.ok(!body.error, JSON.stringify(body));
  return body.result;
}
async function tool(name, args = {}) {
  const result = await rpc('tools/call', { name, arguments: args });
  assert.equal(result.isError, false, JSON.stringify(result));
  calls++;
  return result.structuredContent;
}

assert.equal((await rpc('tools/list', {})).tools.length, 39);
await tool('photon_open_editor');
await tool('photon_new_project');
const created = await tool('photon_create_object', { type: 'particle_emitter', name: 'Native Probe' });
const fields = await tool('photon_api_lookup', { expression: "mcp.object('Native Probe').config", filter: 'startLifetime' });
assert.ok(fields.fields.some(field => field.name === 'startLifetime'));
const api = await tool('photon_api_lookup', { class: 'com.lowdragmc.photon.client.fx.timeline.AnimatedProperty', filter: 'Key' });
assert.ok(api.methods.some(method => method.name === 'putKey'));
const types = await tool('photon_list_api_classes', { filter: 'postfx.graph.nodes.' });
assert.ok(types.count > 5);
const actions = await tool('photon_editor_action', { action: 'list' });
assert.ok(actions.actions.some(action => action.id === 'ldlib2:editor.undo'));
const execution = await tool('photon_execute_code', { code: "remembered = 41; println 'native edit'; mcp.object('Native Probe').config.maxParticles = 321; return args.answer + 1", args: { answer: 41 } });
assert.equal(execution.result, 42);
assert.equal(execution.historyRecorded, true);
assert.match(execution.stdout, /native edit/);
assert.equal((await tool('photon_get_object', { object: created.id })).json.data.config.maxParticles, 321);
assert.equal((await tool('photon_editor_action', { action: 'invoke', id: 'ldlib2:editor.undo' })).invoked, true);
assert.equal((await tool('photon_get_object', { object: created.id })).json.data.config.maxParticles, 2000);
await tool('photon_undo_redo', { action: 'redo' });
assert.equal((await tool('photon_execute_code', { code: 'return remembered + 1', record_history: false })).result, 42);
const failure = await rpc('tools/call', { name: 'photon_execute_code', arguments: { code: "mcp.object('Native Probe').config.maxParticles = 7; assert false : 'rollback probe'" } });
assert.equal(failure.isError, true);
assert.equal((await tool('photon_get_object', { object: created.id })).json.data.config.maxParticles, 321);
const keys = await tool('photon_execute_code', { code: `
def target = mcp.object('Native Probe')
def track = PhotonRegistries.TIMELINE_TRACKS.get('animation').create()
track.targetId(target.id())
def property = PositionPropertyType.INSTANCE.create(target)
property.putKey(0, 0f, 0f)
property.putKey(0, 20f, 2f)
property.putKey(0, 30f, 3f)
property.removeKey(0, 2)
track.properties().add(property)
timeline.tracks().add(track)
timeline.insertTime(10d, 5d)
return [times: property.keyframeTimes(), tracks: timeline.tracks().size()]
` });
assert.deepEqual(keys.result.times, [0, 25]);
await tool('photon_playback', { action: 'seek', tick: 25 });
const sampled = await tool('photon_execute_code', { code: "def evaluated = api.read(runtime.timelinePlayer, 'lastEvalTime'); return [x: mcp.object('Native Probe').transform().localPosition().x, expected: runtime.fxData.timeline().tracks()[0].properties()[0].sampleChannelValue(0, (float) evaluated), evaluationTick: evaluated, tick: editor.sceneView.particleManager.time]", record_history: false });
assert.equal(sampled.result.tick, 25);
assert.ok(Math.abs(sampled.result.x - sampled.result.expected) < 0.01, JSON.stringify(sampled));
const pack = await tool('photon_fxpack', { action: 'export', path: 'mcp-smoke/native.fxpack', namespace: 'mcp', name: 'native_probe', include_assets: ['photon_mcp:shaders/core/vergil_gate.json'] });
assert.deepEqual(pack.embeddedAssets, ['photon_mcp:shaders/core/vergil_gate.json']);
assert.equal(pack.fileCount, 2);
assert.deepEqual((await tool('photon_fxpack', { action: 'list', path: 'mcp-smoke/native.fxpack' })).effects, ['mcp:native_probe']);
await tool('photon_fxpack', { action: 'remove', path: 'mcp-smoke/native.fxpack', fx_id: 'mcp:native_probe' });
assert.deepEqual((await tool('photon_fxpack', { action: 'list', path: 'mcp-smoke/native.fxpack' })).effects, []);
await tool('photon_playback', { action: 'seek', tick: 10020 });
assert.equal((await tool('photon_get_editor_state')).tick, 10020);
await tool('photon_playback', { action: 'seek', tick: 25 });
assert.equal((await tool('photon_get_editor_state')).timelineEvaluationTick, 23);
console.log(`PASS: ${calls} native integration calls; live JVM code, reflection, state, rollback, undo/redo, keyframe add/remove/ripple/seek and FXPack lifecycle.`);
