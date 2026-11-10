package org.asyncttwiz.android


import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.asyncttwiz.storage.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The card records (docs/specs/web-client.md sections 3.5, 3.6): [installed] the records in use once they are the app's
 * catalog ([ready]); [progress] while it installs; [failed] when the download failed twice (Retry).
 */
data class CatalogUiState(val installed: CatalogMetadata? = null, val ready: Boolean = false,
    val progress: CatalogProgress? = null, val failed: Boolean = false) {
    /** What the screens show: controls reading card records are greyed until they are [CardRecords.Ready]. */
    val records: CardRecords get() = when {
        ready -> CardRecords.Ready
        failed -> CardRecords.Failed
        else -> CardRecords.Updating(progress?.percent ?: 0)
    }
}

/**
 * The app's card records are the server's card catalog its build names ([expected], the built-in checksum; null in local
 * and test builds: the server's current one): on start ([start]) it compares the installed records with it and, when they
 * differ, downloads and installs the catalog while the app stays usable. A failed attempt is tried once more by itself,
 * then the screens say so with Retry ([retry]). Public data only: no account or session enters this worker.
 */
internal class CatalogController(private val directory: File, val repository: CatalogRepository,
    private val source: () -> CatalogSource, private val expected: String?, private val worker: ExecutorService,
    private val post: (() -> Unit) -> Unit, private val retryPause: Long = 2_000) {
    var state by mutableStateOf(CatalogUiState())
        private set
    private var working = false
    private var closed = false

    /** Starts the check (and an installation when needed) unless it is running or the records are ready. Main thread. */
    fun start() {
        if (working || closed || state.ready) return
        working = true
        state = state.copy(failed = false)
        worker.execute(::work)
    }

    fun retry() = start()

    /** Tests: shows [value] as the state. */
    internal fun seed(value: CatalogUiState) { state = value }

    fun close() { closed = true; worker.shutdownNow() }

    private fun work() {
        try {
            val installed = repository.metadata()
            post { if (!closed) state = state.copy(installed = installed) }
            val wanted = expected ?: try { source().current().checksum } catch (failure: Exception) {
                if (installed != null) {
                    AppLog.warn("catalog", "The server's card catalog couldn't be read; keeping the installed ${installed.checksum}", failure)
                    return finish(installed)
                }
                null
            }
            if (wanted != null && installed != null && installed.checksum == wanted) {
                AppLog.info("catalog", "Card records are the app's catalog $wanted (${installed.records} printings)")
                return finish(installed)
            }
            AppLog.info("catalog", "Card records ${installed?.checksum ?: "not installed"}; installing ${wanted ?: "the server's current catalog"}")
            finish(retriedOnce { install(wanted) })
        } catch (failure: Exception) {
            AppLog.warn("catalog", "Card records couldn't be installed", failure)
            post { if (!closed) { working = false; state = state.copy(failed = true, progress = null) } }
        }
    }

    private fun finish(installed: CatalogMetadata) = post {
        if (!closed) { working = false; state = CatalogUiState(installed, ready = true) }
    }

    /** A failed download is tried once more by itself before the screens say so (LOB-091). Worker only. */
    private fun <T> retriedOnce(block: () -> T): T = try { block() } catch (failure: Exception) {
        if (closed || failure is InterruptedException) throw failure
        AppLog.warn("catalog", "Card records download failed; trying once more", failure)
        Thread.sleep(retryPause)
        block()
    }

    private fun install(checksum: String?): CatalogMetadata {
        var last = 0L
        var phase: CatalogPhase? = null
        return CatalogInstaller(directory, repository, source(), log = { AppLog.info("catalog", it) }).install(checksum) { progress ->
            val now = System.nanoTime()
            if (phase != progress.phase || now - last >= 250_000_000) {
                last = now
                phase = progress.phase
                post { if (!closed) state = state.copy(progress = progress, failed = false) }
            }
        }
    }
}

/** The app's [CatalogController] (MainActivity starts it on resume, [foreground]); [custom]: another one (end-to-end
 * tests: a phone's own directory and the test server). */
class CatalogViewModel internal constructor(application: Application, custom: CatalogController?) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, null)
    private val main = Handler(Looper.getMainLooper())
    internal val repository = custom?.repository ?: AndroidCatalogStorage.repository(application)
    internal val controller = custom ?: CatalogController(AndroidCatalogStorage.directory(application), repository,
        { HttpCatalogSource(BuildConfig.ACCOUNT_ORIGIN) }, BuildConfig.CATALOG_CHECKSUM.ifEmpty { null },
        Executors.newSingleThreadExecutor { Thread(it, "public-catalog") }, { action -> main.post { action() } })
    val state: CatalogUiState get() = controller.state

    fun foreground() = controller.start()
    fun retry() = controller.retry()
    internal fun seed(value: CatalogUiState) = controller.seed(value)

    override fun onCleared() = controller.close()
}
