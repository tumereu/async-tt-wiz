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
    val status: String? = null, val error: Boolean = false)

/** Public downloads are independent of login; no account tokens, callbacks or private data enter this worker. */
class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(CatalogUiState())
        private set
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "public-catalog") }
    private val connectivity = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val directory = AndroidCatalogStorage.directory(application)
    private val repository = AndroidCatalogStorage.repository(application)
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
        state = state.copy(busy = true, metered = metered, status = "Checking card records…", error = false)
        worker.execute {
            var installed: CatalogMetadata? = null
            try {
                installed = repository.metadata()
                var available = preferences.getString("date", null)?.let { date ->
                    CatalogMetadata(date, preferences.getString("version", "")!!)
                }
                var size = preferences.getLong("size", -1).takeIf { it > 0 }
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
                publish(CatalogUiState(installed = installed, metered = metered, error = true,
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
        state = current.copy(busy = true, error = false, status = "Downloading card records…")
        worker.execute {
            try { publish(CatalogUiState(installed = install(available, current.size), metered = current.metered,
                status = "Card records are up to date.")) }
            catch (_: Exception) { publish(current.copy(busy = false, error = true,
                status = "Download failed. Check your connection and free storage, then retry. Your previous records have not changed.")) }
        }
    }

    private fun install(meta: CatalogMetadata, size: Long?): CatalogMetadata {
        var last = 0L
        return CatalogInstaller(directory, repository, source).install(meta, size) { status ->
            val now = System.nanoTime()
            if (now - last >= 250_000_000) {
                last = now
                main.post { if (!cleared) state = state.copy(status = status) }
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
