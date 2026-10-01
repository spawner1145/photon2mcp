import readline from 'node:readline';

let host = '127.0.0.1';
let port = 8765;
for (let index = 2; index < process.argv.length; index += 2) {
  const flag = process.argv[index];
  const value = process.argv[index + 1];
  if (flag === '--host') host = value;
  else if (flag === '--port') port = Number(value);
  else throw new Error(`Unknown argument ${flag}; use --host and --port`);
}
const address = host.includes(':') ? `[${host}]` : host;
const endpoint = `http://${address}:${port}/mcp`;
let session;
let protocol;
const requests = new Set();

function emit(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

async function forward(message) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' };
  if (session) headers['Mcp-Session-Id'] = session;
  if (protocol) headers['MCP-Protocol-Version'] = protocol;
  try {
    const response = await fetch(endpoint, { method: 'POST', headers, body: JSON.stringify(message) });
    const nextSession = response.headers.get('Mcp-Session-Id');
    if (nextSession) session = nextSession;
    if (response.status === 202 || response.status === 204) return;
    const body = await response.json();
    if (message.method === 'initialize' && body.result) protocol = body.result.protocolVersion;
    if (message.id !== undefined) emit(body);
  } catch (error) {
    if (message.id !== undefined) {
      emit({ jsonrpc: '2.0', id: message.id, error: { code: -32000, message: `Photon MCP connection failed (${endpoint}): ${error.message}` } });
    } else {
      process.stderr.write(`Photon MCP: ${error.message}\n`);
    }
  }
}

const input = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
input.on('line', line => {
  if (!line.trim()) return;
  let message;
  try {
    message = JSON.parse(line);
  } catch {
    emit({ jsonrpc: '2.0', id: null, error: { code: -32700, message: 'Invalid JSON' } });
    return;
  }
  if (!message || typeof message !== 'object' || Array.isArray(message)) {
    emit({ jsonrpc: '2.0', id: null, error: { code: -32600, message: 'JSON-RPC request must be an object' } });
    return;
  }
  const pending = forward(message);
  requests.add(pending);
  pending.finally(() => requests.delete(pending));
});

async function close() {
  await Promise.allSettled([...requests]);
  if (session) {
    await fetch(endpoint, { method: 'DELETE', headers: { 'Mcp-Session-Id': session } }).catch(() => {});
  }
}
input.on('close', () => {
  close().catch(error => {
    process.stderr.write(`Photon MCP shutdown: ${error.message}\n`);
    process.exitCode = 1;
  });
});
process.on('SIGINT', () => { input.close(); });
process.on('SIGTERM', () => { input.close(); });
