package org.asyncttwiz.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.asyncttwiz.storage.*
import java.io.File
import java.util.concurrent.Executors

data class CatalogUiState(val installed: CatalogMetadata? = null, val available: CatalogMetadata? = null,
    val size: Long? = null, val metered: Boolean = true, val busy: Boolean = false,
    val status: String? = null, val error: Boolean = false, val progress: CatalogProgress? = null)

/** Volatile, process-wide consent deferral, deliberately not preferences or saved instance state. */
internal class CatalogPromptPolicy {
    private var dismissed by mutableStateOf(emptySet<String>())
    private fun key(state: CatalogUiState): String? {
        val available = state.available ?: return null // Wait for actionable metadata, never offer a dead Download action.
        if (!CatalogUpdatePolicy.changed(state.installed, available)) return null
        return if (state.installed == null) "missing" else "${available.date}/${available.version}/${available.indexVersion}"
    }
    fun shouldPrompt(state: CatalogUiState): Boolean = !state.busy && key(state)?.let { it !in dismissed } == true
    fun dismiss(state: CatalogUiState) { key(state)?.let { dismissed = dismissed + it } }
}

private val processCatalogPrompt = CatalogPromptPolicy()

/** Public downloads are independent of login; no account tokens, callbacks or private data enter this worker. */
class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    internal val promptPolicy get() = processCatalogPrompt
    var state by mutableStateOf(CatalogUiState())
        private set
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "public-catalog") }
    private val connectivity = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val directory = AndroidCatalogStorage.directory(application)
    internal val repository = AndroidCatalogStorage.repository(application)
    private val cadence = CatalogCadence(File(directory, "cadence.properties"))
    private val preferences = application.getSharedPreferences("public-catalog-update", Context.MODE_PRIVATE)
    private val source = if (BuildConfig.DEBUG && BuildConfig.CATALOG_TEST_ORIGIN.isNotEmpty())
        HttpCatalogSource(BuildConfig.CATALOG_BASE, BuildConfig.CATALOG_TEST_ORIGIN)
        else HttpCatalogSource()
    private var cleared = false
    private var working = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            main.post { foreground() }
        }
    }
    init { connectivity.registerDefaultNetworkCallback(callback) }

    fun foreground(force: Boolean = false) {
        if (cleared || working) return
        working = true
        val metered = connectivity.isActiveNetworkMetered
        state = state.copy(busy = true, metered = metered, status = "Checking card records…", error = false, progress = null)
        worker.execute {
            var installed: CatalogMetadata? = null
            var available: CatalogMetadata? = null
            var size: Long? = null
            try {
                installed = repository.metadata()
                available = preferences.getString("date", null)?.let { date ->
                    CatalogMetadata(date, preferences.getString("version", "")!!)
                }
                size = preferences.getLong("size", -1).takeIf { it > 0 }
                if (force || cadence.due(System.currentTimeMillis())) {
                    try {
                        available = source.metadata()
                        // HEAD is small metadata, not consent to a bulk download.
                        size = if (CatalogUpdatePolicy.changed(installed, available)) source.size() else null
                        check(preferences.edit().putString("date", available.date).putString("version", available.version)
                            .putLong("size", size ?: -1).commit())
                        cadence.checked(System.currentTimeMillis(), true)
                    } catch (error: Exception) { cadence.checked(System.currentTimeMillis(), false); throw error }
                }
                val changed = available?.takeIf { CatalogUpdatePolicy.changed(installed, it) }
                if (changed != null && CatalogUpdatePolicy.download(installed, changed, metered, consent = false)) {
                    installed = install(changed, size)
                    publish(CatalogUiState(installed = installed, metered = metered, status = "Card records are up to date."))
                } else publish(CatalogUiState(installed, changed, size, metered, status =
                    if (changed == null && installed != null) "Card records are up to date." else null))
            } catch (_: Exception) {
                // Failed automatic installation still leaves an actionable update/consent offer.
                publish(CatalogUiState(installed = installed,
                    available = available?.takeIf { CatalogUpdatePolicy.changed(installed, it) }, size = size,
                    metered = metered, error = true,
                    status = "Card records could not be updated. ${if (installed != null) "Your installed records are still available." else "Check your connection and free storage, then retry."}"))
            }
        }
    }

    /** Only the explicit native consent action calls this for initial/metered bulk acquisition. */
    fun consent() {
        val current = state
        val available = current.available ?: return
        if (working || cleared) return
        if (!CatalogUpdatePolicy.download(current.installed, available, current.metered, consent = true)) return
        working = true
        val progress = CatalogProgress(CatalogPhase.DOWNLOADING, 0, current.size)
        state = current.copy(busy = true, error = false, status = progress.label(), progress = progress)
        worker.execute {
            try { publish(CatalogUiState(installed = install(available, current.size), metered = current.metered,
                status = "Card records are up to date.")) }
            catch (_: Exception) { publish(current.copy(busy = false, error = true,
                status = "Download failed. Check your connection and free storage, then retry. Your previous records have not changed.")) }
        }
    }

    private fun install(meta: CatalogMetadata, size: Long?): CatalogMetadata {
        var last = 0L
        var phase: CatalogPhase? = null
        return CatalogInstaller(directory, repository, source).install(meta, size) { progress ->
            val now = System.nanoTime()
            if (phase != progress.phase || now - last >= 250_000_000 || progress.done == progress.total) {
                last = now
                phase = progress.phase
                main.post { if (!cleared) state = state.copy(progress = progress, status = progress.label()) }
            }
        }
    }
    private fun publish(value: CatalogUiState) { main.post { if (!cleared) { state = value; working = false } } }
    override fun onCleared() {
        cleared = true
        connectivity.unregisterNetworkCallback(callback)
        worker.shutdownNow()
    }
}
