package org.asyncttwiz.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
            if (response.code == 404) return null
            check(response.isSuccessful)
            val text = response.body!!.source().use { source ->
                source.request(16_385)
                source.readByteArray(minOf(source.buffer.size, 16_385L)).toString(Charsets.UTF_8)
            }
            return AppRelease.parse(text) to text
        }
    }

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
            val fetched = runCatching { fetch() }.getOrNull()
            main.post { accept(fetched); checking = false; initialCheck = false }
        }
    }

    fun requireForRules() {
        incompatible = true
        preferences.edit().putInt("incompatible-version", BuildConfig.VERSION_CODE).apply()
        foreground()
    }

    fun update() {
        if (downloading) return
        downloading = true; progress = 0f; problem = null
        val cached = release
        worker.execute {
            val fetched = runCatching { fetch() }.getOrNull()
            val target = newestAppRelease(cached, fetched?.first)
            val result = runCatching {
                if (target == null || target.versionCode <= BuildConfig.VERSION_CODE)
                    throw AppUpdateFailure("Update not available yet. Try again in a moment.", browserFallback = false)
                downloadAppApk(client, "$origin/app/apk", target, File(getApplication<Application>().cacheDir, "updates")) { percent ->
                    main.post { progress = percent / 100f }
                }
            }
            main.post {
                accept(fetched); downloading = false; initialCheck = false
                result.fold({ installation = it }, { problem = appUpdateProblem(it) })
            }
        }
    }

    fun consumeInstallation() { installation = null }
    fun installerFailed() { problem = AppUpdateProblem("Could not open the installer.") }
    fun installerReturned() { problem = AppUpdateProblem("The update wasn't installed. Tap Update to try again.") }
    fun browserFailed() { problem = AppUpdateProblem("Could not open a browser for the download.", browserFallback = false) }
    val browserDownload get() = "$origin/app/apk"
    fun awaitingPermission(value: Boolean) { preferences.edit().putBoolean("awaiting-permission", value).apply() }
    fun permissionReturned(allowed: Boolean) {
        val awaiting = preferences.getBoolean("awaiting-permission", false)
        awaitingPermission(false)
        if (awaiting && allowed) update()
    }
    fun resumePermission(allowed: Boolean) {
        if (allowed && preferences.getBoolean("awaiting-permission", false)) permissionReturned(true)
    }

    override fun onCleared() {
        client.dispatcher.cancelAll()
        worker.shutdownNow()
        client.connectionPool.evictAll()
    }
}
