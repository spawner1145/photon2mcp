import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const bridge = spawn(process.execPath, [fileURLToPath(new URL('../bridge/photon-mcp.mjs', import.meta.url)), '--port', process.argv[2] ?? '8765'], { stdio: ['pipe', 'pipe', 'pipe'] });
let output = '';
let errors = '';
bridge.stdout.on('data', chunk => { output += chunk; });
bridge.stderr.on('data', chunk => { errors += chunk; });
const exited = new Promise((resolve, reject) => {
  bridge.on('error', reject);
  bridge.on('exit', (code, signal) => resolve({ code, signal }));
});
bridge.stdin.end([
  { jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2025-11-25', capabilities: {}, clientInfo: { name: 'bridge-smoke', version: '0.2.0' } } },
  { jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} },
].map(message => JSON.stringify(message)).join('\n') + '\n');
const exit = await exited;
assert.equal(exit.code, 0, errors);
assert.equal(exit.signal, null, errors);
const messages = output.trim().split('\n').map(line => JSON.parse(line));
assert.equal(messages.find(message => message.id === 1).result.serverInfo.version, '0.2.1');
assert.equal(messages.find(message => message.id === 2).result.tools.length, 37);
console.log('PASS: stdio initialize, 37 tools and clean EOF shutdown (exit 0).');
