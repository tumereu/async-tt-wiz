package org.asyncttwiz.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("app-update", 0)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "app-update") }
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(false).followSslRedirects(false).build()
    // GitHub answers release downloads with a redirect to its file host; https only.
    private val github = client.newBuilder().followRedirects(true).followSslRedirects(false).build()
    private val origin = BuildConfig.ACCOUNT_ORIGIN
    var release by mutableStateOf(runCatching { AppRelease.parse(preferences.getString("latest", "")!!) }.getOrNull())
        private set
    private var incompatible by mutableStateOf(preferences.getInt("incompatible-version", -1) == BuildConfig.VERSION_CODE)
    var initialCheck by mutableStateOf(release == null && origin.isNotBlank())
        private set
    var downloading by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    /** Stays on screen until the next attempt, so a player can read and report it. */
    var problem by mutableStateOf<AppUpdateProblem?>(null)
        private set
    var installation by mutableStateOf<File?>(null)
        private set
    private var checking = false
    val required get() = appUpdateRequired(BuildConfig.VERSION_CODE, release, incompatible)

    private fun fetch(): Pair<AppRelease, String>? {
        if (origin.isBlank()) return null
        val request = Request.Builder().url("$origin/app/latest").build()
        client.newCall(request).apply { timeout().timeout(5, TimeUnit.SECONDS) }.execute().use { response ->
            if (response.code == 404) return null.also { AppLog.info("update", "No published release") }
            check(response.isSuccessful) { "Release check failed with HTTP ${response.code}" }
            val text = response.body!!.source().use { source ->
                source.request(16_385)
                source.readByteArray(minOf(source.buffer.size, 16_385L)).toString(Charsets.UTF_8)
            }
            return (AppRelease.parse(text) to text).also {
                AppLog.info("update", "Latest release ${it.first.versionName} (${it.first.versionCode}), installed ${BuildConfig.VERSION_CODE}")
            }
        }
    }

    private fun checked(): Pair<AppRelease, String>? =
        runCatching { fetch() }.onFailure { AppLog.warn("update", "Release check failed", it) }.getOrNull()

    private fun accept(fetched: Pair<AppRelease, String>?) {
        if (fetched == null) return
        val newest = newestAppRelease(release, fetched.first)
        if (newest == fetched.first) {
            preferences.edit().putString("latest", fetched.second).apply()
            release = newest
        }
    }

    fun foreground() {
        if (checking || downloading) return
        checking = true
        worker.execute {
            val fetched = checked()
            main.post { accept(fetched); checking = false; initialCheck = false }
        }
    }

    fun requireForRules() {
        AppLog.info("update", "Server requires a newer app for this game")
        incompatible = true
        preferences.edit().putInt("incompatible-version", BuildConfig.VERSION_CODE).apply()
        foreground()
    }

    fun update() {
        if (downloading) return
        downloading = true; progress = 0f; problem = null; reports.reset()
        val cached = release
        AppLog.info("update", "Update tapped (cached ${cached?.versionCode})")
        worker.execute {
            val fetched = checked()
            val target = newestAppRelease(cached, fetched?.first)
            val result = runCatching {
                if (target == null || target.versionCode <= BuildConfig.VERSION_CODE)
                    throw AppUpdateFailure("Update not available yet. Try again in a moment.", browserFallback = false)
                // GitHub is the main source; the server's copy stays a browser fallback, used here only for
                // releases published without a GitHub link.
                val mirror = target.githubUrl
                downloadAppApk(if (mirror != null) github else client, mirror ?: "$origin/app/apk", target,
                    File(getApplication<Application>().cacheDir, "updates"), ifRange = mirror == null) { percent ->
                    main.post { progress = percent / 100f }
                }
            }
            main.post {
                accept(fetched); downloading = false; initialCheck = false
                result.fold({ installation = it }, {
                    problem = appUpdateProblem(it)
                    AppLog.warn("update", "Update failed: ${problem?.message}", it)
                })
            }
        }
    }

    fun consumeInstallation() { installation = null }
    fun installerFailed(failure: Throwable? = null) {
        AppLog.warn("update", "Could not open the installer", failure)
        problem = AppUpdateProblem("Could not open the installer.")
    }
    fun installerReturned() {
        AppLog.warn("update", "Returned from the installer without updating")
        problem = AppUpdateProblem("The update wasn't installed. Tap Update to try again.")
    }
    fun browserFailed(failure: Throwable? = null) {
        AppLog.warn("update", "Could not open a browser", failure)
        problem = AppUpdateProblem("Could not open a browser for the download.", browserFallback = false)
    }
    val browserDownload get() = "$origin/app/apk"
    val githubDownload get() = release?.githubUrl
    fun awaitingPermission(value: Boolean) { preferences.edit().putBoolean("awaiting-permission", value).apply() }
    fun permissionReturned(allowed: Boolean) {
        val awaiting = preferences.getBoolean("awaiting-permission", false)
        if (awaiting) AppLog.info("update", "Returned from install permission settings, allowed=$allowed")
        awaitingPermission(false)
        if (awaiting && allowed) update()
    }
    val reports = ProblemReportSender(viewModelScope)
    fun sendReport() = reports.send(problem = problem?.let { listOfNotNull(it.message, it.detail).joinToString("\n") })

    fun resumePermission(allowed: Boolean) {
        if (allowed && preferences.getBoolean("awaiting-permission", false)) permissionReturned(true)
    }

    override fun onCleared() {
        client.dispatcher.cancelAll()
        worker.shutdownNow()
        client.connectionPool.evictAll()
    }
}
