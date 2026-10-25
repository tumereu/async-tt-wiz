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
    val status: String? = null, val error: Boolean = false, val progress: CatalogProgress? = null,
    val background: Boolean = false, val plan: CatalogUpdatePlan? = null,
    val fullRefresh: CatalogUpdatePlan? = null, val fullSize: Long? = null)

/** Volatile, process-wide consent deferral, deliberately not preferences or saved instance state. */
internal class CatalogPromptPolicy {
    private var dismissed by mutableStateOf(emptySet<String>())
    private fun key(state: CatalogUiState): String? {
        val available = state.available ?: return null // Wait for actionable metadata, never offer a dead Download action.
        if (state.plan == null && !CatalogUpdatePolicy.changed(state.installed, available)) return null
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
    private val source: CatalogSource = if (BuildConfig.DEBUG && BuildConfig.CATALOG_TEST_ORIGIN.isNotEmpty())
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
        if (cleared || working || state.fullRefresh != null) return
        working = true
        val metered = connectivity.isActiveNetworkMetered
        state = state.copy(busy = true, metered = metered, status = "Checking card records…", error = false, progress = null,
            background = !force)
        worker.execute {
            var installed: CatalogMetadata? = null
            var available: CatalogMetadata? = null
            var size: Long? = null
            var pending: CatalogUpdatePlan? = null
            try {
                installed = repository.metadata()
                available = preferences.getString("date", null)?.let { date ->
                    CatalogMetadata(date, preferences.getString("version", "")!!)
                }
                size = preferences.getLong("size", -1).takeIf { it > 0 }
                var sets = preferences.getStringSet("sets", emptySet()).orEmpty().map { value ->
                    val (code, date) = value.split('|')
                    CatalogSet(code, date)
                }
                if (force || available == null || CatalogUpdatePolicy.changed(installed, available) || cadence.due(System.currentTimeMillis())) {
                    val list = source.setList()
                    available = list.metadata
                    sets = list.sets
                    // HEAD is small metadata, not consent to a bulk download.
                    size = if (CatalogUpdatePolicy.changed(installed, available)) source.size() else null
                    check(preferences.edit().putString("date", available.date).putString("version", available.version)
                        .putLong("size", size ?: -1).putStringSet("sets", sets.map { "${it.code}|${it.releaseDate}" }.toSet()).commit())
                }
                // Only plan a recent-set refresh when due, not on every connectivity/foreground callback.
                val plan = CatalogUpdatePolicy.plan(installed, repository.installedSets(), CatalogSetList(available, sets))
                pending = plan
                val due = force || cadence.due(System.currentTimeMillis()) || CatalogUpdatePolicy.changed(installed, available)
                if (due && CatalogUpdatePolicy.canDownload(installed, plan, metered, consent = false)) {
                    installed = install(checkNotNull(plan), size)
                    cadence.checked(System.currentTimeMillis(), true)
                    publish(CatalogUiState(installed = installed, metered = metered, background = !force,
                        status = if (force) "Card records are up to date." else null))
                } else {
                    if (due && plan == null) cadence.checked(System.currentTimeMillis(), true)
                    publish(CatalogUiState(installed, plan?.metadata?.takeIf { due }, size.takeIf { plan?.full == true }, metered, status =
                        if (force && plan == null && installed != null) "Card records are up to date." else null,
                        background = !force, plan = plan?.takeIf { due }))
                }
            } catch (_: Exception) {
                cadence.checked(System.currentTimeMillis(), false)
                // Failed automatic installation still leaves an actionable update/consent offer.
                publish(CatalogUiState(installed = installed,
                    available = pending?.metadata ?: available?.takeIf { CatalogUpdatePolicy.changed(installed, it) }, size = size,
                    plan = pending,
                    metered = metered, error = true,
                    status = "Card records could not be updated. ${if (installed != null) "Your installed records are still available." else "Check your connection and free storage, then retry."}"))
            }
        }
    }

    /** Only the explicit native consent action calls this for initial/metered bulk acquisition. */
    fun consent() {
        val current = state
        val plan = current.plan ?: current.available?.takeIf { CatalogUpdatePolicy.changed(current.installed, it) }
            ?.let { CatalogUpdatePlan(it, full = true) } ?: return
        if (working || cleared) return
        startInstall(plan, current.size)
    }

    fun requestFullRefresh() {
        if (working || cleared) return
        working = true
        state = state.copy(busy = true, status = "Checking full catalog size…", error = false, background = false, progress = null)
        val current = state
        worker.execute {
            try {
                val meta = source.metadata()
                val size = source.size()
                publish(current.copy(busy = false, status = null, fullRefresh = CatalogUpdatePolicy.fullRefresh(meta), fullSize = size))
            } catch (_: Exception) {
                publish(current.copy(busy = false, error = true, status = "Could not check the full catalog. Check your connection, then retry."))
            }
        }
    }

    fun dismissFullRefresh() { state = state.copy(fullRefresh = null, fullSize = null) }
    fun confirmFullRefresh() {
        val plan = state.fullRefresh ?: return
        if (working || cleared) return
        val size = state.fullSize
        dismissFullRefresh()
        startInstall(plan, size)
    }

    private fun startInstall(plan: CatalogUpdatePlan, size: Long?) {
        val current = state
        working = true
        val progress = CatalogProgress(CatalogPhase.DOWNLOADING, 0, size)
        state = current.copy(busy = true, error = false, status = progress.label(), progress = progress, background = false)
        worker.execute {
            try {
                val installed = install(plan, size)
                cadence.checked(System.currentTimeMillis(), true)
                publish(CatalogUiState(installed = installed, metered = current.metered, status = "Card records are up to date."))
            }
            catch (_: Exception) { publish(current.copy(busy = false, error = true,
                status = "Download failed. Check your connection and free storage, then retry. Your previous records have not changed.")) }
        }
    }

    private fun install(plan: CatalogUpdatePlan, size: Long?): CatalogMetadata {
        var last = 0L
        var phase: CatalogPhase? = null
        return CatalogInstaller(directory, repository, source).install(plan, size) { progress ->
            val now = System.nanoTime()
            if (phase != progress.phase || now - last >= 250_000_000 || progress.done == progress.total) {
                last = now
                phase = progress.phase
                main.post { if (!cleared) state = state.copy(progress = progress, status = progress.label(), background = false) }
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
