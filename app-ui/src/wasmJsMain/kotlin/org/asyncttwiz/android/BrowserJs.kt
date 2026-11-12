@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlin.js.Promise

// The browser APIs the storage, card records and pictures use (web client step 3a), as small JavaScript functions: IndexedDB,
// fetch, DecompressionStream with SHA-256, the Cache API and persistent storage. Values cross as text (JSON), numbers and
// opaque handles (JsAny); the logic above them is client-storage's common code.

// IndexedDB

/** Opens database [name] at [version] with the object stores [stores] (comma-separated), creating missing ones. */
internal fun idbOpen(name: String, version: Int, stores: String): Promise<JsAny> = js("""
    new Promise((resolve, reject) => {
        if (typeof indexedDB === 'undefined') { reject(new Error('This browser has no IndexedDB')); return; }
        const request = indexedDB.open(name, version);
        request.onupgradeneeded = () => {
            const db = request.result;
            for (const store of stores.split(',')) if (!db.objectStoreNames.contains(store)) db.createObjectStore(store);
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('IndexedDB open failed'));
        request.onblocked = () => reject(new Error('IndexedDB open blocked by another tab'));
    })""")

/** Every text value of [store] as a JSON object of key to text. */
internal fun idbEntriesJson(db: JsAny, store: String): Promise<JsString> = js("""
    new Promise((resolve, reject) => {
        const tx = db.transaction(store, 'readonly');
        const objects = tx.objectStore(store);
        const keys = objects.getAllKeys();
        const values = objects.getAll();
        tx.oncomplete = () => {
            const entries = {};
            keys.result.forEach((key, index) => { if (typeof values.result[index] === 'string') entries[String(key)] = values.result[index]; });
            resolve(JSON.stringify(entries));
        };
        tx.onerror = () => reject(tx.error || new Error('IndexedDB read failed'));
        tx.onabort = () => reject(tx.error || new Error('IndexedDB read aborted'));
    })""")

/** Applies [changesJson] (a JSON object of key to text, or null to delete) to [store] in one transaction. */
internal fun idbWriteJson(db: JsAny, store: String, changesJson: String): Promise<JsAny?> = js("""
    new Promise((resolve, reject) => {
        const tx = db.transaction(store, 'readwrite');
        const objects = tx.objectStore(store);
        const changes = JSON.parse(changesJson);
        for (const key of Object.keys(changes)) {
            if (changes[key] === null) objects.delete(key); else objects.put(changes[key], key);
        }
        tx.oncomplete = () => resolve(null);
        tx.onerror = () => reject(tx.error || new Error('IndexedDB write failed'));
        tx.onabort = () => reject(tx.error || new Error('IndexedDB write aborted'));
    })""")

/** The value of [key] in [store] (any kind: text or a Blob), or null. */
internal fun idbGet(db: JsAny, store: String, key: String): Promise<JsAny?> = js("""
    new Promise((resolve, reject) => {
        const tx = db.transaction(store, 'readonly');
        const request = tx.objectStore(store).get(key);
        tx.oncomplete = () => resolve(request.result === undefined ? null : request.result);
        tx.onerror = () => reject(tx.error || new Error('IndexedDB read failed'));
    })""")

/** Empties [store], then puts [value] under [key] and [marker] under [markerKey], in one transaction. */
internal fun idbReplace(db: JsAny, store: String, key: String, value: JsAny, markerKey: String, marker: String): Promise<JsAny?> = js("""
    new Promise((resolve, reject) => {
        const tx = db.transaction(store, 'readwrite');
        const objects = tx.objectStore(store);
        objects.clear();
        objects.put(value, key);
        objects.put(marker, markerKey);
        tx.oncomplete = () => resolve(null);
        tx.onerror = () => reject(tx.error || new Error('IndexedDB write failed'));
        tx.onabort = () => reject(tx.error || new Error('IndexedDB write aborted'));
    })""")

/** Empties [store]. */
internal fun idbClear(db: JsAny, store: String): Promise<JsAny?> = js("""
    new Promise((resolve, reject) => {
        const tx = db.transaction(store, 'readwrite');
        tx.objectStore(store).clear();
        tx.oncomplete = () => resolve(null);
        tx.onerror = () => reject(tx.error || new Error('IndexedDB clear failed'));
    })""")

internal fun jsText(value: JsAny): String? = js("typeof value === 'string' ? value : null")

// Persistent storage (§1 item 4): the browser keeps the card records and personal data instead of evicting them

/** Asks the browser to keep this site's storage; true when it will. Never throws. */
internal fun storagePersist(): Promise<JsBoolean> = js("""
    (async () => {
        try {
            if (!navigator.storage || !navigator.storage.persist) return false;
            if (await navigator.storage.persisted()) return true;
            return await navigator.storage.persist();
        } catch (e) { return false; }
    })()""")

// fetch

/** GET [url] as text of at most [maxBytes] bytes, no cache, no redirects; rejects unless the answer is 200. */
internal fun fetchText(url: String, maxBytes: Double): Promise<JsString> = js("""
    (async () => {
        const response = await fetch(url, { cache: 'no-store', redirect: 'error', credentials: 'omit' });
        if (response.status !== 200) throw new Error('HTTP ' + response.status + ' for ' + url);
        const text = await response.text();
        if (text.length > maxBytes) throw new Error('Answer too large: ' + url);
        return text;
    })()""")

/** GET [url] as a Blob of at most [maxBytes] bytes, telling [progress] the bytes so far; no cache, no redirects. */
internal fun fetchBlob(url: String, maxBytes: Double, progress: (Double) -> Unit): Promise<JsAny> = js("""
    (async () => {
        const response = await fetch(url, { cache: 'no-store', redirect: 'error', credentials: 'omit' });
        if (response.status !== 200) throw new Error('Card catalog unavailable: HTTP ' + response.status + ' for ' + url);
        const declared = response.headers.get('Content-Encoding') ? -1 : Number(response.headers.get('Content-Length') || -1);
        if (declared > maxBytes) throw new Error('Card catalog too large');
        const reader = response.body.getReader();
        const parts = [];
        let count = 0;
        for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            count += value.length;
            if (count > maxBytes) { reader.cancel(); throw new Error('Card catalog too large'); }
            parts.push(value);
            progress(count);
        }
        if (declared >= 0 && count !== declared) throw new Error('Card catalog download cut short');
        return new Blob(parts);
    })()""")

/** Unpacks gzip [blob] whole and works out the SHA-256 of what it holds: a handle for [unpackedChecksum],
 * [unpackedSize], [unpackedRead] and [unpackedNext]. */
internal fun unpackGzip(blob: JsAny): Promise<JsAny> = js("""
    (async () => {
        const stream = blob.stream().pipeThrough(new DecompressionStream('gzip'));
        const buffer = await new Response(stream).arrayBuffer();
        const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', buffer));
        const checksum = Array.from(digest, b => b.toString(16).padStart(2, '0')).join('');
        return { checksum, buffer, offset: 0, done: false, decoder: new TextDecoder('utf-8') };
    })()""")

internal fun unpackedChecksum(state: JsAny): String = js("state.checksum")
internal fun unpackedSize(state: JsAny): Double = js("state.buffer.byteLength")
internal fun unpackedRead(state: JsAny): Double = js("state.offset")

/** The next megabyte of the unpacked text (UTF-8 decoded across part boundaries), null after the last. */
internal fun unpackedNext(state: JsAny): String? = js("""
    (() => {
        if (state.offset >= state.buffer.byteLength) {
            if (state.done) return null;
            state.done = true;
            const tail = state.decoder.decode();
            state.buffer = new ArrayBuffer(0);
            return tail.length ? tail : null;
        }
        const end = Math.min(state.offset + 1048576, state.buffer.byteLength);
        const text = state.decoder.decode(new Uint8Array(state.buffer, state.offset, end - state.offset), { stream: true });
        state.offset = end;
        return text;
    })()""")

// Pictures: fetch and the Cache API

/** GET [url] as bytes of at most [maxBytes]: a handle for [responseStatus], [responseType], [responseRetryAfter] and
 * [bytesLength]/[byteAt] of its body (empty unless 200). No credentials, no redirects followed. */
internal fun fetchBytes(url: String, maxBytes: Double, accept: String): Promise<JsAny> = js("""
    (async () => {
        const response = await fetch(url, { redirect: 'follow', credentials: 'omit', headers: { 'Accept': accept } });
        const type = (response.headers.get('Content-Type') || '').split(';')[0].trim();
        const retryAfter = response.headers.get('Retry-After') || '';
        let bytes = new Uint8Array(0);
        if (response.status === 200) {
            const declared = Number(response.headers.get('Content-Length') || -1);
            if (declared > maxBytes) throw new Error('Too large: ' + url);
            bytes = new Uint8Array(await response.arrayBuffer());
            if (bytes.length > maxBytes) throw new Error('Too large: ' + url);
        }
        return { status: response.status, type, retryAfter, bytes };
    })()""")

internal fun responseStatus(response: JsAny): Int = js("response.status")
internal fun responseType(response: JsAny): String = js("response.type")
internal fun responseRetryAfter(response: JsAny): String = js("response.retryAfter")
internal fun responseBytes(response: JsAny): JsAny = js("response.bytes")
internal fun bytesLength(bytes: JsAny): Int = js("bytes.length")
internal fun byteAt(bytes: JsAny, index: Int): Int = js("bytes[index]")
internal fun newBytes(size: Int): JsAny = js("new Uint8Array(size)")
internal fun setByte(bytes: JsAny, index: Int, value: Int): Unit = js("{ bytes[index] = value; }")

/** The kept entry [key] of cache [cache] (type and bytes, as [fetchBytes] gives them), or null. */
internal fun cacheRead(cache: String, key: String): Promise<JsAny?> = js("""
    (async () => {
        if (typeof caches === 'undefined') return null;
        const response = await (await caches.open(cache)).match('https://artwork.invalid/' + encodeURIComponent(key));
        if (!response) return null;
        return { status: 200, type: response.headers.get('Content-Type') || '', retryAfter: '', bytes: new Uint8Array(await response.arrayBuffer()) };
    })()""")

/** Keeps [bytes] of [type] as entry [key] of cache [cache]. */
internal fun cacheWrite(cache: String, key: String, type: String, bytes: JsAny): Promise<JsAny?> = js("""
    (async () => {
        if (typeof caches === 'undefined') return null;
        await (await caches.open(cache)).put('https://artwork.invalid/' + encodeURIComponent(key),
            new Response(bytes, { headers: { 'Content-Type': type } }));
        return null;
    })()""")

/** Drops entry [key] of cache [cache]. */
internal fun cacheDelete(cache: String, key: String): Promise<JsAny?> = js("""
    (async () => {
        if (typeof caches === 'undefined') return null;
        await (await caches.open(cache)).delete('https://artwork.invalid/' + encodeURIComponent(key));
        return null;
    })()""")

/** Calls [online] each time the browser comes back online. */
internal fun onOnline(online: () -> Unit): Unit = js("{ if (typeof window !== 'undefined') window.addEventListener('online', () => online()); }")

/** Bytes from the browser's side ([bytesLength]/[byteAt]) as a ByteArray. */
internal fun JsAny.toByteArray(): ByteArray = ByteArray(bytesLength(this)) { byteAt(this, it).toByte() }

/** A ByteArray as the browser's bytes (a Uint8Array). */
internal fun ByteArray.toJsBytes(): JsAny = newBytes(size).also { bytes -> forEachIndexed { index, value -> setByte(bytes, index, value.toInt() and 0xff) } }
