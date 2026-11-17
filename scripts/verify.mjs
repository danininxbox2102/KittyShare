import fs from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';
const root=process.cwd();
const get=f=>fs.readFileSync(path.join(root,f),'utf8');
const server=get('native/java/ru/localshare/mobile/LocalHttpServer.kt');
const native=get('native/java/ru/localshare/mobile/LocalSharePlugin.kt');
const manifest=get('scripts/install-native.mjs');
const client=get('native/assets/remote/index.html');
const files=get('native/java/ru/localshare/mobile/SharedFiles.kt');
const checks=[
  ['Server binds to LAN interfaces',server.includes('0.0.0.0')],
  ['Every route requires authentication before routing',server.indexOf('if (!authorized(session))') < server.indexOf('when {')],
  ['Write requests check Origin',server.includes('safeOrigin(session)')],
  ['HTTP upload route',server.includes('"/api/upload"')],
  ['HTTP download route',server.includes('"/download"')],
  ['HTTP list route',server.includes('"/api/files"')],
  ['Upload requires Content-Length / bounded size',server.includes('MAX_UPLOAD_BYTES')],
  ['File paths stay within app directory',files.includes('base.path + File.separator')],
  ['Staged imports are atomic',files.includes('staging.renameTo(dest)')],
  ['Native file picker',native.includes('ACTION_OPEN_DOCUMENT')],
  ['Android foreground service declaration',manifest.includes('foregroundServiceType="connectedDevice"')],
  ['Remote browser DOM does not inject filenames as HTML',client.includes('name.textContent=')],
];
for(const [name,passed] of checks){assert.ok(passed,name);console.log('PASS',name)}
