@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.asyncttwiz.catalog.CatalogVersion
import org.asyncttwiz.storage.BrowserCardRecords
import org.asyncttwiz.storage.BrowserCatalogInstaller
import org.asyncttwiz.storage.CatalogDownloads
import org.asyncttwiz.storage.CatalogFiles
import org.asyncttwiz.storage.CatalogPhase
import org.asyncttwiz.storage.CubeImportResolver
import org.asyncttwiz.storage.CubeResolver
import org.asyncttwiz.storage.DeckImportResolver
import org.asyncttwiz.storage.DeckResolver
import org.asyncttwiz.storage.UnpackedCatalog

/**
 * The browser's card records (docs/specs/web-client.md §3.5, §3.6, §9.3), as [CatalogController] on Android: on start
 * ([start]) the kept catalog is read into memory when it is the one the app expects ([expected], built in; null in local
 * and test builds: the server's current one), else downloaded from the server at [origin], checked and kept. Meanwhile
 * [state] says how far it is, so whatever reads card records stays greyed ("Card records updating · 43%"); a failed attempt
 * is tried once more by itself, then the screens say so with Retry ([retry]). Persistent storage is asked for when a
 * download starts. Runs in [scope], the page's.
 */
class BrowserCatalogController(storage: BrowserStorage, origin: String, private val expected: String?,
    private val scope: CoroutineScope) : CardRecordsController {
    private val records = BrowserCardRecords()
    override val repository: BrowserCardRecords get() = records
    private val installer = BrowserCatalogInstaller(records, BrowserCatalogDownloads(origin),
        storage.database?.let { IndexedDbCatalogFiles(it) } ?: MemoryCatalogFiles(), ::unpackCatalog,
        log = { AppLog.info("catalog", it) })
    override var state by mutableStateOf(CatalogUiState())
        private set
    private var job: Job? = null
    private var closed = false

    override fun start() {
        if (job?.isActive == true || closed || state.ready) return
        state = state.copy(failed = false)
        job = scope.launch { work() }
    }

    override fun retry() = start()

    override fun seed(value: CatalogUiState) { state = value }

    override fun close() { closed = true; job?.cancel() }

    override fun deckResolver(): DeckResolver = DeckImportResolver(records)

    override fun cubeResolver(): CubeResolver = CubeImportResolver(records)

    private suspend fun work() {
        val clock = TimeSource.Monotonic
        var last = clock.markNow()
        var phase: CatalogPhase? = null
        var persisting = false
        try {
            state = state.copy(installed = records.metadata())
            val installed = installer.run(expected) { progress ->
                if (progress.phase == CatalogPhase.DOWNLOADING && !persisting) {
                    persisting = true
                    scope.launch {
                        val persisted = storagePersist().await<JsBoolean>().toBoolean()
                        AppLog.info("catalog", "Persistent storage ${if (persisted) "granted" else "not granted"}")
                    }
                }
                if (phase != progress.phase || last.elapsedNow().inWholeMilliseconds >= 250) {
                    last = clock.markNow()
                    phase = progress.phase
                    if (!closed) state = state.copy(progress = progress, failed = false)
                }
            }
            if (!closed) state = CatalogUiState(installed, ready = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLog.warn("catalog", "Card records couldn't be installed", failure)
            if (!closed) state = state.copy(failed = true, progress = null)
        }
    }
}

/** The ATW server's catalog endpoints at [origin] (the page's own; empty: relative addresses). */
internal class BrowserCatalogDownloads(private val origin: String) : CatalogDownloads<JsAny> {
    init { require(!origin.endsWith('/')) { "Not a catalog origin" } }

    override suspend fun current(): CatalogVersion {
        val text = fetchText("$origin/v1/catalog", 64.0 * 1024).await<JsString>().toString()
        return json.decodeFromString(CatalogVersion.serializer(), text).also {
            check(BrowserCatalogInstaller.validChecksum(it.checksum)) { "Not a catalog checksum" }
        }
    }

    override suspend fun download(checksum: String, progress: (Long) -> Unit): JsAny {
        require(BrowserCatalogInstaller.validChecksum(checksum))
        return fetchBlob("$origin/v1/catalog/$checksum", MAX_BYTES.toDouble()) { progress(it.toLong()) }.await()
    }

    private companion object {
        /** Far above the catalog's ~13 MB (section 3.3), as on Android. */
        const val MAX_BYTES = 512L * 1024 * 1024
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** The kept catalog file in IndexedDB: the gzip file as a Blob under its checksum, and which one is installed. */
internal class IndexedDbCatalogFiles(private val database: JsAny) : CatalogFiles<JsAny> {
    override suspend fun installed(): String? = idbGet(database, BrowserStorage.CATALOG, INSTALLED).await<JsAny?>()?.let(::jsText)
    override suspend fun read(checksum: String): JsAny? = idbGet(database, BrowserStorage.CATALOG, FILE + checksum).await()
    override suspend fun install(checksum: String, file: JsAny) {
        idbReplace(database, BrowserStorage.CATALOG, FILE + checksum, file, INSTALLED, checksum).await<JsAny?>()
    }
    override suspend fun clear() { idbClear(database, BrowserStorage.CATALOG).await<JsAny?>() }

    private companion object {
        const val INSTALLED = "installed"
        const val FILE = "file:"
    }
}

/** Without IndexedDB: the catalog file kept for this tab only. */
internal class MemoryCatalogFiles : CatalogFiles<JsAny> {
    private var kept: Pair<String, JsAny>? = null
    override suspend fun installed(): String? = kept?.first
    override suspend fun read(checksum: String): JsAny? = kept?.takeIf { it.first == checksum }?.second
    override suspend fun install(checksum: String, file: JsAny) { kept = checksum to file }
    override suspend fun clear() { kept = null }
}

/** A kept or downloaded catalog file unpacked by the browser (DecompressionStream), with its SHA-256. */
internal suspend fun unpackCatalog(file: JsAny): UnpackedCatalog {
    val state = unpackGzip(file).await<JsAny>()
    return object : UnpackedCatalog {
        override val checksum: String = unpackedChecksum(state)
        override val size: Long = unpackedSize(state).toLong()
        override val read: Long get() = unpackedRead(state).toLong()
        override suspend fun next(): String? = unpackedNext(state)
    }
}
