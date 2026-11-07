// Web spike (throwaway): drives Chrome over the DevTools protocol and prints what the spike pages measured.
//   node webspike/measure.mjs [--cdp 9333] [--base http://localhost:18765] [--launch headless|headed] [--only bench-main,bench-worker,table,table-input]
// Local: --launch starts /usr/bin/google-chrome with remote debugging. Phone: `adb.exe -P 15138 forward tcp:9222
// localabstract:chrome_devtools_remote` and `adb.exe -P 15138 reverse tcp:18765 tcp:18765`, then run with --cdp 9222 (no --launch).
import { spawn } from 'node:child_process';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const args = Object.fromEntries(process.argv.slice(2).reduce((a, v, i, all) => (v.startsWith('--') ? [...a, [v.slice(2), all[i + 1]?.startsWith('--') ? true : all[i + 1] ?? true]] : a), []));
const cdpPort = Number(args.cdp ?? 9333);
const base = args.base ?? 'http://localhost:18765';
const only = (args.only ?? 'bench-main,bench-worker,table,table-input').split(',');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let chrome;
if (args.launch) {
  // --chrome/--profile: e.g. Windows Chrome from WSL (mirrored networking): --chrome '/mnt/c/Program Files/Google/Chrome/Application/chrome.exe' --profile 'C:\\Temp\\spike-chrome'
  const profile = args.profile ?? mkdtempSync(join(args.profileDir ?? tmpdir(), 'spike-chrome-'));
  const flags = [`--remote-debugging-port=${cdpPort}`, `--user-data-dir=${profile}`, '--no-first-run', '--no-default-browser-check',
    '--window-size=1600,1000', '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--disable-backgrounding-occluded-windows'];
  if (args.launch === 'headless') flags.push('--headless=new');
  if (args.gpuFlags) flags.push(...String(args.gpuFlags).split(' '));
  chrome = spawn(args.chrome ?? '/usr/bin/google-chrome', [...flags, 'about:blank'], { stdio: 'ignore' });
  for (let i = 0; i < 100; i++) { try { await fetch(`http://127.0.0.1:${cdpPort}/json/version`); break; } catch { await sleep(200); } }
}

async function openPage() {
  const r = await fetch(`http://127.0.0.1:${cdpPort}/json/new?about:blank`, { method: 'PUT' });
  const target = await r.json();
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  let id = 0;
  const waiting = new Map();
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && waiting.has(msg.id)) { waiting.get(msg.id)(msg); waiting.delete(msg.id); }
    if (msg.method === 'Runtime.consoleAPICalled' && args.console) console.log('  console: ' + msg.params.args.map((a) => a.value ?? a.description).join(' ').slice(0, 300));
    if (msg.method === 'Runtime.exceptionThrown') console.log('  exception: ' + JSON.stringify(msg.params.exceptionDetails).slice(0, 600));
  };
  const send = (method, params = {}) => new Promise((res) => { const i = ++id; waiting.set(i, res); ws.send(JSON.stringify({ id: i, method, params })); });
  const evaluate = async (expression) => (await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })).result?.result?.value;
  const waitFor = async (expression, timeoutMs) => {
    const end = Date.now() + timeoutMs;
    while (Date.now() < end) { const v = await evaluate(expression); if (v) return v; await sleep(250); }
    return null;
  };
  await send('Runtime.enable');
  return { send, evaluate, waitFor, close: () => fetch(`http://127.0.0.1:${cdpPort}/json/close/${target.id}`) };
}

const version = await (await fetch(`http://127.0.0.1:${cdpPort}/json/version`)).json();
console.log(`# ${version.Browser} ${version['User-Agent']}`);

if (only.includes('gpu')) {
  const p = await openPage();
  await p.send('Page.navigate', { url: 'chrome://gpu' });
  await sleep(2500);
  console.log(await p.evaluate("(document.querySelector('info-view')?.shadowRoot?.textContent ?? document.body.innerText).replace(/\\s+/g,' ').slice(0, 1500)"));
  await p.close();
}

if (only.includes('raf')) {
  // The display's own frame rate: a plain requestAnimationFrame loop on the (light) index page
  const p = await openPage();
  await p.send('Page.navigate', { url: `${base}/` });
  await sleep(1500);
  console.log('## ' + (await p.evaluate("new Promise(res => { const d = []; let last = 0; function f(t) { if (last) d.push(t - last); last = t; if (d.length < 180) requestAnimationFrame(f); else res('plain rAF avg ' + (d.reduce((a,b)=>a+b)/d.length).toFixed(2) + ' ms, max ' + Math.max(...d).toFixed(1) + ' ms'); } requestAnimationFrame(f); })")));
  await p.close();
}

for (const mode of ['main', 'worker']) {
  if (!only.includes(`bench-${mode}`)) continue;
  const p = await openPage();
  await p.send('Page.navigate', { url: `${base}/bench/?mode=${mode}` });
  await p.waitFor("document.getElementById('mode') && document.getElementById('mode').textContent.length > 0", 60000);
  await sleep(1500);
  await p.evaluate("document.getElementById('run').click()");
  const result = await p.waitFor('globalThis.benchResult', 600000);
  console.log(`## bench ${mode}\n` + (await p.evaluate("document.getElementById('log').textContent")));
  console.log('BENCHJSON ' + result);
  if (mode === 'worker') {
    await p.evaluate("document.getElementById('roundtrip').click()");
    const rt = await p.waitFor('globalThis.roundtripResult', 300000);
    console.log('## round trips\n' + rt + '\n' + (await p.evaluate("document.getElementById('frames').textContent")));
  }
  await p.close();
}

if (only.includes('table')) {
  const p = await openPage();
  await p.send('Page.navigate', { url: `${base}/table/?auto${args.small ? '&size=small' : ''}` });
  const t0 = Date.now();
  const loaded = await p.waitFor('globalThis.imagesLoaded', 60000);
  console.log(`## table (auto-pan) images: ${loaded}; page to images ${Date.now() - t0} ms`);
  for (let i = 0; i < 4; i++) { await sleep(4000); console.log('  ' + (await p.evaluate('globalThis.frameStats'))); }
  await p.close();
}

if (only.includes('table-input')) {
  const p = await openPage();
  await p.send('Page.navigate', { url: `${base}/table/` });
  await p.waitFor('globalThis.imagesLoaded', 60000);
  await sleep(1500);
  const camera = () => p.evaluate('globalThis.camera');
  const mouse = (type, x, y, extra = {}) => p.send('Input.dispatchMouseEvent', { type, x, y, ...extra });
  const out = [];
  await mouse('mouseMoved', 150, 186); await sleep(300);
  out.push(`hover over card #16 -> hovered=${await p.evaluate('globalThis.hovered')}`);
  const touch0 = (type, points) => p.send('Input.dispatchTouchEvent', { type, touchPoints: points.map(([x, y], i) => ({ x, y, id: i })) });
  const cp = await camera();
  await touch0('touchStart', [[500, 400], [700, 400]]);
  for (let i = 1; i <= 15; i++) { await touch0('touchMove', [[500 - i * 8, 400], [700 + i * 8, 400]]); await sleep(16); }
  await touch0('touchEnd', []); await sleep(300);
  out.push(`touch pinch out (fresh) -> camera ${cp} => ${await camera()}`);
  const c0 = await camera();
  await mouse('mouseWheel', 400, 300, { deltaX: 0, deltaY: -100 }); await sleep(300);
  out.push(`wheel up -> camera ${c0} => ${await camera()}`);
  await mouse('mousePressed', 150, 186, { button: 'right', buttons: 2, clickCount: 1 }); await mouse('mouseReleased', 150, 186, { button: 'right', buttons: 0, clickCount: 1 }); await sleep(300);
  out.push(`right-click -> ${await p.evaluate('globalThis.rightClicked')}`);
  const c1 = await camera();
  await mouse('mousePressed', 600, 400, { button: 'left', buttons: 1, clickCount: 1 });
  for (let i = 1; i <= 20; i++) { await mouse('mouseMoved', 600 + i * 10, 400 + i * 5, { button: 'left', buttons: 1 }); await sleep(16); }
  await mouse('mouseReleased', 800, 500, { button: 'left', buttons: 0, clickCount: 1 }); await sleep(300);
  out.push(`mouse drag (200,100) px -> camera ${c1} => ${await camera()}`);
  const c2 = await camera();
  const touch = (type, points) => p.send('Input.dispatchTouchEvent', { type, touchPoints: points.map(([x, y], i) => ({ x, y, id: i })) });
  await touch('touchStart', [[500, 400], [700, 400]]);
  for (let i = 1; i <= 15; i++) { await touch('touchMove', [[500 + i * 5, 400], [700 - i * 5, 400]]); await sleep(16); }
  await touch('touchEnd', []); await sleep(300);
  out.push(`touch pinch in -> camera ${c2} => ${await camera()}`);
  const c3 = await camera();
  await touch('touchStart', [[500, 400]]);
  for (let i = 1; i <= 15; i++) { await touch('touchMove', [[500 - i * 10, 400 - i * 4]]); await sleep(16); }
  await touch('touchEnd', []); await sleep(300);
  out.push(`touch one-finger pan -> camera ${c3} => ${await camera()}`);
  console.log('## table input\n' + out.map((l) => '  ' + l).join('\n'));
  await p.close();
}

if (chrome) {
  // The browser target closes the whole browser (also a Windows Chrome started through WSL interop)
  try { const b = new WebSocket(version.webSocketDebuggerUrl); await new Promise((r) => { b.onopen = r; }); b.send(JSON.stringify({ id: 1, method: 'Browser.close' })); await sleep(500); } catch {}
  chrome.kill();
}
process.exit(0);
