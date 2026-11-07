import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';

const endpoint = process.argv[2] ?? 'http://127.0.0.1:8765/mcp';
let sequence = 0;
const report = { passed: false, sections: [], offscreen: null };
async function tool(name, args = {}) {
    const response = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ jsonrpc: '2.0', id: ++sequence, method: 'tools/call', params: { name, arguments: args } }) });
    const body = await response.json();
    assert.ok(!body.error && !body.result.isError, JSON.stringify(body));
    return body.result.structuredContent;
}
const execute = async code => (await tool('photon_execute_code', { code, record_history: false, reload: false })).result;
await tool('photon_open_editor');
await tool('photon_new_project');
await tool('photon_playback', { action: 'pause' });
try {
    const created = await tool('photon_create_object', { type: 'particle_emitter', name: 'Sections Probe', patch: { config: {
        duration: 100, startLifetime: { type: 'constant', data: { number: 8 } }, lifetimeMode: 'Loop',
        startSpeed: { type: 'constant', data: { number: 0 } }, maxParticles: 1,
        emission: { emissionRate: { type: 'constant', data: { number: 0 } }, bursts: { uid: 1, payload: [{ time: 0, cycles: 1, interval: 1, probability: 1, count: { type: 'constant', data: { number: 1 } } }] } },
    } } });
    const described = await tool('photon_describe_timeline');
    assert.ok(described.features.exitModes.includes('DRAIN'));
    assert.ok(described.features.particleLifetimeModes.includes('PingPong'));
    const setup = await execute(`def track=com.lowdragmc.photon.PhotonRegistries.TIMELINE_TRACKS.get('control').create();
        track.clips().add(new Clip(0d,30d,1f).targetId(mcp.object('Sections Probe').id()).exitMode(Clip.ExitMode.DRAIN).seamMode(Clip.SeamMode.RESTART));
        def value=new Timeline();value.tracks().add(track);
        value.sections().add(new TimelineSection('start',0d));
        value.sections().add(new TimelineSection('hold',5d).repeat(0));
        value.sections().add(new TimelineSection('end',20d).then(TimelineSection.Then.END));
        value.releaseSection('end');value.releaseNow(true);
        return value.serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()).toString()`);
    await tool('photon_update_timeline', { snbt_patch: setup });
    await tool('photon_playback', { action: 'seek', tick: 6 });
    const inspected = await tool('photon_timeline_sections', { action: 'inspect' });
    assert.deepEqual(inspected.names, ['start', 'hold', 'end']);
    assert.ok(inspected.hasRelease);
    const invalid = await tool('photon_timeline_sections', { action: 'jump', section: 'missing' });
    assert.equal(invalid.accepted, false);
    assert.equal((await tool('photon_get_object', { object: created.id })).json.data.config.lifetimeMode, 'Loop');
    await tool('photon_playback', { action: 'play' });
    await tool('photon_screenshot', { region: 'scene', max_size: 640, path: 'mcp-upgrade/sections-live-warmup.png' });
    const jump = await tool('photon_timeline_sections', { action: 'jump', section: 'hold' });
    assert.equal(jump.accepted, true);
    report.sections.push({ action: 'jump', accepted: jump.accepted });
    const queue = await tool('photon_timeline_sections', { action: 'queue', section: 'end' });
    assert.equal(queue.accepted, true);
    report.sections.push({ action: 'queue', accepted: queue.accepted });
    const advance = await tool('photon_timeline_sections', { action: 'advance' });
    assert.equal(advance.accepted, true);
    report.sections.push({ action: 'advance', accepted: advance.accepted });
    const release = await tool('photon_timeline_sections', { action: 'release' });
    assert.equal(release.accepted, true);
    assert.ok(release.released);
    report.sections.push({ action: 'release', accepted: release.accepted });
    const afterTick = await execute(`runtime.timelinePlayer.setFollowSections(true);runtime.timelinePlayer.tick();
        return [section:runtime.sections().current(),time:runtime.timelinePlayer.localTime()]`);
    assert.equal(afterTick.section, 'end');
    await tool('photon_playback', { action: 'pause' });
    await tool('photon_save_project', { path: 'mcp-upgrade/upgrade-2.2.11.fxproj' });
    await tool('photon_open_project', { path: 'mcp-upgrade/upgrade-2.2.11.fxproj' });
    await tool('photon_playback', { action: 'pause' });
    const roundtrip = await tool('photon_describe_timeline');
    assert.equal(roundtrip.json.sections.length, 3);
    assert.equal(roundtrip.json.sections[1].repeat, 0);
    assert.equal(roundtrip.json.release.section, 'end');
    assert.equal(roundtrip.json.tracks[0].data.clips[0].exit, 'drain');
    assert.equal(roundtrip.json.tracks[0].data.clips[0].seam, 'restart');
    report.lifetimeModes = [];
    for (const mode of ['Loop', 'PingPong']) {
        await tool('photon_update_object', { object: 'Sections Probe', patch: { config: { lifetimeMode: mode } } });
        await tool('photon_playback', { action: 'seek', tick: 16 });
        const particle = await execute(`def object=mcp.object('Sections Probe');return [mode:object.config.lifetimeMode.name(),count:object.particleAmount,age:object.age]`);
        assert.equal(particle.mode, mode);
        assert.equal(particle.count, 1, 'Particle was removed instead of cycling beyond its 8-tick lifetime');
        report.lifetimeModes.push(particle);
    }
    await tool('photon_playback', { action: 'seek', tick: 4 });
    const popped = await execute(`def parent=editor.parent;
        while(parent!=null && !(parent instanceof com.lowdragmc.lowdraglib2.editor.ui.EditorWindow))parent=parent.parent;
        assert parent!=null;
        return parent.popOutToOsWindow()`);
    report.offscreen = { opened: popped };
    if (popped) {
        try {
            const detached = await tool('photon_get_editor_state');
            assert.ok(detached.editorOpen && detached.projectLoaded);
            const capture = await tool('photon_screenshot', { region: 'editor', max_size: 1280, path: 'mcp-upgrade/editor-2.2.11-detached.png' });
            assert.equal(capture.captureSource, 'editor_window');
            const sceneCapture = await tool('photon_screenshot', { region: 'scene', max_size: 1280, path: 'mcp-upgrade/scene-2.2.11-detached.png' });
            assert.equal(sceneCapture.captureSource, 'editor_window');
            report.offscreen.capture = true;
            report.offscreen.captureSource = capture.captureSource;
        } finally {
            await execute(`def parent=editor.parent;
                while(parent!=null && !(parent instanceof com.lowdragmc.lowdraglib2.editor.ui.EditorWindow))parent=parent.parent;
                return parent.dockIntoGameWindow()`);
        }
    }
    report.passed = true;
} finally {
    await tool('photon_new_project');
    await tool('photon_playback', { action: 'pause' });
    await mkdir('build/qa', { recursive: true });
    await writeFile('build/qa/upgrade-2.2.11.json', JSON.stringify(report, null, 2));
}
console.log(JSON.stringify(report));
