@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

// Stand-ins for the browser's own objects (WebSocket, Worker, fetch, Web Audio, vibration) for the browser-only code's
// tests in headless Chrome: each replaces the page's global for one test and puts the real one back after it. What the
// code under test did with them is read back as JSON.

/** Replaces `WebSocket` with a recording stand-in: each socket made is kept in `globalThis.__fakeSockets`. */
internal fun fakeWebSocket(): Unit = js("""{
    globalThis.__realWebSocket = globalThis.__realWebSocket || globalThis.WebSocket;
    globalThis.__fakeSockets = [];
    globalThis.WebSocket = class {
        constructor(url, protocols) {
            this.url = url; this.protocols = protocols; this.readyState = 0; this.sent = []; this.closedWith = null;
            this.binaryType = 'blob';
            globalThis.__fakeSockets.push(this);
        }
        send(text) { this.sent.push(text); }
        close(code) { this.closedWith = code === undefined ? 'plain' : code; this.readyState = 3; }
    };
}""")

internal fun realWebSocket(): Unit = js("{ if (globalThis.__realWebSocket) globalThis.WebSocket = globalThis.__realWebSocket; }")

/** The fake sockets made so far, as JSON: url, protocols, binaryType, sent, closedWith. */
internal fun fakeSocketsJson(): String = js("""JSON.stringify(globalThis.__fakeSockets.map(s =>
    ({ url: s.url, protocols: s.protocols, binaryType: s.binaryType, sent: s.sent, closedWith: s.closedWith })))""")

/** The fake socket [index] opens (readyState 1 and its onopen). */
internal fun fakeSocketOpen(index: Int): Unit = js("""{
    const s = globalThis.__fakeSockets[index]; s.readyState = 1; if (s.onopen) s.onopen({});
}""")

/** A text message from the server on fake socket [index]. */
internal fun fakeSocketText(index: Int, text: String): Unit = js("""{
    const s = globalThis.__fakeSockets[index]; if (s.onmessage) s.onmessage({ data: text });
}""")

/** A binary message from the server on fake socket [index]. */
internal fun fakeSocketBinary(index: Int): Unit = js("""{
    const s = globalThis.__fakeSockets[index]; if (s.onmessage) s.onmessage({ data: new ArrayBuffer(4) });
}""")

/** Fake socket [index] closes with [code], [reason], cleanly or not. */
internal fun fakeSocketClosed(index: Int, code: Int, reason: String, clean: Boolean): Unit = js("""{
    const s = globalThis.__fakeSockets[index]; s.readyState = 3;
    if (s.onclose) s.onclose({ code: code, reason: reason, wasClean: clean });
}""")

/** Sets fake socket [index]'s readyState (0 connecting, 1 open, 2 closing, 3 closed) without an event. */
internal fun fakeSocketState(index: Int, state: Int): Unit = js("{ globalThis.__fakeSockets[index].readyState = state; }")

/** Whether fake socket [index] still has its handlers (a cancelled socket reports nothing more). */
internal fun fakeSocketListened(index: Int): Boolean = js("""(() => {
    const s = globalThis.__fakeSockets[index]; return !!(s.onopen || s.onmessage || s.onclose);
})()""")

/** Replaces `fetch` with a stand-in answering every request with [status] and [body]; the requests are kept in
 * `globalThis.__fakeRequests` (url, method, headers, body, redirect, credentials, cache). */
internal fun fakeFetch(status: Int, body: String): Unit = js("""{
    globalThis.__realFetch = globalThis.__realFetch || globalThis.fetch;
    globalThis.__fakeRequests = [];
    globalThis.fetch = async (input, init) => {
        const request = (typeof input === 'string' || input instanceof URL) ? new Request(input, init) : new Request(input, init);
        const headers = {};
        request.headers.forEach((value, name) => { headers[name] = value; });
        globalThis.__fakeRequests.push({ url: request.url, method: request.method, headers: headers,
            body: init && typeof init.body === 'string' ? init.body : null,
            redirect: init ? init.redirect || null : null, credentials: init ? init.credentials || null : null,
            cache: init ? init.cache || null : null });
        return new Response(body, { status: status, headers: { 'Content-Type': 'application/json' } });
    };
}""")

internal fun realFetch(): Unit = js("{ if (globalThis.__realFetch) globalThis.fetch = globalThis.__realFetch; }")

/** The requests the fake fetch got, as JSON. */
internal fun fakeRequestsJson(): String = js("JSON.stringify(globalThis.__fakeRequests)")

/** Replaces `Worker` with a recording stand-in: each worker made is kept in `globalThis.__fakeWorkers`. */
internal fun fakeWorker(): Unit = js("""{
    globalThis.__realWorker = globalThis.__realWorker || globalThis.Worker;
    globalThis.__fakeWorkers = [];
    globalThis.Worker = class {
        constructor(url) { this.url = url; this.posted = []; globalThis.__fakeWorkers.push(this); }
        postMessage(data) { this.posted.push(data); }
    };
}""")

internal fun realWorker(): Unit = js("{ if (globalThis.__realWorker) globalThis.Worker = globalThis.__realWorker; }")

/** The fake workers made so far, as JSON: url and the messages the page posted to each. */
internal fun fakeWorkersJson(): String = js("JSON.stringify(globalThis.__fakeWorkers.map(w => ({ url: w.url, posted: w.posted })))")

/** A message from fake worker [index] to the page. */
internal fun fakeWorkerSays(index: Int, text: String): Unit = js("""{
    const w = globalThis.__fakeWorkers[index]; if (w.onmessage) w.onmessage({ data: text });
}""")

/** Fake worker [index] fails with [message]. */
internal fun fakeWorkerFails(index: Int, message: String): Unit = js("""{
    const w = globalThis.__fakeWorkers[index]; if (w.onerror) w.onerror({ message: message });
}""")

/** Replaces Web Audio's `AudioContext` with a recording stand-in starting in [state] ("suspended" or "running"); what it
 * was asked is kept in `globalThis.__fakeAudio`. Without [available] the browser has no Web Audio at all. */
internal fun fakeAudio(state: String, available: Boolean): Unit = js("""{
    if (!('__realAudioContext' in globalThis)) {
        globalThis.__realAudioContext = globalThis.AudioContext;
        globalThis.__realWebkitAudioContext = globalThis.webkitAudioContext;
    }
    delete globalThis.__atwAudio;
    const log = globalThis.__fakeAudio = { made: 0, resumed: 0, buffers: [], started: 0 };
    globalThis.webkitAudioContext = undefined;
    globalThis.AudioContext = !available ? undefined : class {
        constructor() { log.made++; this.state = state; this.destination = {}; }
        resume() { log.resumed++; this.state = 'running'; return Promise.resolve(); }
        createBuffer(channels, length, rate) {
            const buffer = { channels: channels, length: length, rate: rate, samples: null,
                copyToChannel(samples, channel) { buffer.samples = Array.from(samples); buffer.channel = channel; } };
            log.buffers.push(buffer);
            return buffer;
        }
        createBufferSource() {
            return { buffer: null, connected: false, connect(to) { this.connected = true; }, start() { log.started++; } };
        }
    };
}""")

internal fun realAudio(): Unit = js("""{
    if ('__realAudioContext' in globalThis) {
        globalThis.AudioContext = globalThis.__realAudioContext;
        globalThis.webkitAudioContext = globalThis.__realWebkitAudioContext;
    }
    delete globalThis.__atwAudio;
}""")

/** What the fake audio context was asked, as JSON: made, resumed, started and the buffers (length, rate, samples). */
internal fun fakeAudioJson(): String = js("""JSON.stringify({ made: globalThis.__fakeAudio.made, resumed: globalThis.__fakeAudio.resumed,
    started: globalThis.__fakeAudio.started, buffers: globalThis.__fakeAudio.buffers.map(b =>
        ({ channels: b.channels, length: b.length, rate: b.rate, samples: b.samples })) })""")

/** A tap (pointerdown) or key press (keydown, [key] true) on the page. */
internal fun pageInput(key: Boolean): Unit = js("""{
    window.dispatchEvent(key ? new KeyboardEvent('keydown', { key: 'a' }) : new PointerEvent('pointerdown'));
}""")

/** Replaces the browser's vibration ([vibrates]: whether it has `navigator.vibrate`) and whether the page has had a tap
 * ([active]); the vibrations asked for are kept in `globalThis.__fakeVibrations`. */
internal fun fakeVibration(vibrates: Boolean, active: Boolean): Unit = js("""{
    globalThis.__fakeVibrations = [];
    Object.defineProperty(navigator, 'vibrate', { configurable: true,
        value: vibrates ? ((millis) => { globalThis.__fakeVibrations.push(millis); return true; }) : undefined });
    Object.defineProperty(navigator, 'userActivation', { configurable: true, value: { hasBeenActive: active, isActive: active } });
}""")

internal fun realVibration(): Unit = js("""{
    delete navigator.vibrate;
    delete navigator.userActivation;
}""")

/** The vibrations asked for, as JSON milliseconds. */
internal fun fakeVibrationsJson(): String = js("JSON.stringify(globalThis.__fakeVibrations)")

/** Deletes the IndexedDB database [name] (a test's own storage starts empty). */
internal fun deleteDatabase(name: String): kotlin.js.Promise<JsAny?> = js("""new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase(name);
    request.onsuccess = () => resolve(null);
    request.onerror = () => reject(request.error);
    request.onblocked = () => resolve(null);
})""")

/** Closes the IndexedDB connection [database] (as a closed page does). */
internal fun closeDatabase(database: JsAny): Unit = js("database.close()")
