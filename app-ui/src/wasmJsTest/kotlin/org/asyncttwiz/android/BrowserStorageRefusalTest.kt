@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.ArtworkResponse
import org.asyncttwiz.storage.ArtworkStored
import org.asyncttwiz.storage.AsyncArtworkTransport
import org.asyncttwiz.storage.BrowserArtworkRepository
import org.asyncttwiz.storage.MirroredStore

/**
 * The browser refusing a storage request (storage full, over its quota, a private window) on the browser-only storage code:
 * IndexedDB (the card records' file, the session and account data) and the Cache API (pictures). The browser's refusal
 * isn't an Exception; here it comes out as [BrowserStorageRefused], which the callers handle (kept for this tab or visit
 * only, downloaded again) instead of reporting a crash.
 */
class BrowserStorageRefusalTest {
    @AfterTest fun restore() = realCaches()

    // WEB-009: a card records file the browser refuses to keep is a failure the installer handles (used for this visit only)
    @Test fun aRefusedIndexedDbRequestIsAFailureNotACrash() = runTest {
        val files = IndexedDbCatalogFiles(refusingDatabase(reads = false))
        val install = assertFailsWith<BrowserStorageRefused> { files.install("a".repeat(64), jsString("file")) }
        assertTrue("QuotaExceededError" in install.message.orEmpty(), install.message)
        assertFailsWith<BrowserStorageRefused> { files.installed() }
        assertFailsWith<BrowserStorageRefused> { files.read("a".repeat(64)) }
        assertFailsWith<BrowserStorageRefused> { files.clear() }
    }

    // WEB-009: a session or account write the browser refuses stays for this tab only, logged; nothing escapes as a crash
    @Test fun aRefusedTextStoreWriteStaysInMemoryForThisTab() = runTest {
        assertFailsWith<BrowserStorageRefused> { IndexedDbTextStore(refusingDatabase(reads = false), "session").entries() }
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, failure -> uncaught += failure })
        try {
            val logged = mutableListOf<Throwable>()
            val store = MirroredStore.open(IndexedDbTextStore(refusingDatabase(reads = true), "session"), scope) { _, failure -> logged += failure }
            store.put("current", "kept for now")
            store.flush()
            assertEquals("kept for now", store["current"])
            assertEquals(1, logged.size)
            assertTrue(logged.single() is BrowserStorageRefused, "${logged.single()}")
            assertEquals(emptyList(), uncaught)
        } finally { scope.cancel() }
    }

    // WEB-009, LOB-097: a picture the browser refuses to keep is still shown (downloaded again next time); a refused read
    // finds nothing kept
    @Test fun aPictureTheBrowserRefusesToKeepIsStillShown() = runTest {
        refusingCaches()
        val store = CacheApiArtworkStoreOf("atw-refusal-test")
        assertNull(store.read("image|x"))
        assertFailsWith<BrowserStorageRefused> { store.write("image|x", ArtworkStored("image/jpeg", jpeg)) }
        val id = "00000000-0000-4000-8000-000000000001"
        val card = """{"object":"card","id":"$id","layout":"normal","image_uris":{"normal":"https://cards.example/n.jpg"}}"""
        val transport = object : AsyncArtworkTransport {
            var requests = 0
            override suspend fun get(url: String, maximum: Long): ArtworkResponse {
                requests++
                return if (url.endsWith(id)) ArtworkResponse(200, card.encodeToByteArray(), "application/json")
                    else ArtworkResponse(200, jpeg, "image/jpeg")
            }
        }
        val repository = BrowserArtworkRepository(store, transport, now = { 0L }, apiSpacing = 0)
        assertContentEquals(jpeg, repository.load(ArtworkReference(id, 0)))
        assertEquals(2, transport.requests)
    }

    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1, 2, 3)
}

private fun jsString(value: String): JsAny = value.toJsString()

/** An IndexedDB database whose write transactions the browser aborts as over its quota; its read transactions too unless
 * [reads] (then they complete with nothing kept). */
private fun refusingDatabase(reads: Boolean): JsAny = js("""({
    transaction(store, mode) {
        const request = () => ({ result: [] });
        const tx = {
            objectStore: () => ({ get: request, getAll: request, getAllKeys: request, put() {}, delete() {}, clear() {} }),
        };
        setTimeout(() => {
            if (reads && mode === 'readonly') { if (tx.oncomplete) tx.oncomplete(); return; }
            tx.error = new DOMException('The quota has been exceeded.', 'QuotaExceededError');
            if (tx.onerror) tx.onerror(); else if (tx.onabort) tx.onabort();
        }, 0);
        return tx;
    }
})""")

/** Replaces the Cache API with one whose every read, write and delete the browser refuses as over its quota. */
private fun refusingCaches(): Unit = js("""{
    if (!('__realCaches' in globalThis)) globalThis.__realCaches = globalThis.caches;
    const refuse = () => Promise.reject(new DOMException('The quota has been exceeded.', 'QuotaExceededError'));
    Object.defineProperty(globalThis, 'caches', { configurable: true, writable: true,
        value: { open: () => Promise.resolve({ match: refuse, put: refuse, delete: refuse }) } });
}""")

private fun realCaches(): Unit = js("""{
    if ('__realCaches' in globalThis) {
        Object.defineProperty(globalThis, 'caches', { configurable: true, writable: true, value: globalThis.__realCaches });
        delete globalThis.__realCaches;
    }
}""")
