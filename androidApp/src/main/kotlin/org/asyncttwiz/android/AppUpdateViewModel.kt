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
    var error by mutableStateOf<String?>(null)
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
        downloading = true; progress = 0f; error = null
        val cached = release
        worker.execute {
            val fetched = runCatching { fetch() }.getOrNull()
            val target = newestAppRelease(cached, fetched?.first)
            val result = runCatching {
                check(target != null && target.versionCode > BuildConfig.VERSION_CODE) { "Update not available" }
                val directory = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
                val apk = File(directory, "${target.sha256}.apk")
                if (!verifyAppApk(apk, target)) {
                    val part = File(directory, "download.part")
                    try {
                        client.newCall(Request.Builder().url("$origin/app/apk").build()).execute().use { response ->
                            check(response.isSuccessful)
                            val body = response.body!!
                            check(body.contentLength() == target.size)
                            body.byteStream().use { input ->
                                part.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var total = 0L
                                    var lastPercent = -1
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count == -1) break
                                        total += count
                                        check(total <= target.size)
                                        output.write(buffer, 0, count)
                                        val percent = (total * 100 / target.size).toInt()
                                        if (percent != lastPercent) {
                                            lastPercent = percent
                                            main.post { progress = percent / 100f }
                                        }
                                    }
                                }
                            }
                        }
                        check(verifyAppApk(part, target))
                        check(part.renameTo(apk))
                    } finally { part.delete() }
                }
                // Also verify cache hits before every handoff to the system installer.
                check(verifyAppApk(apk, target))
                apk
            }
            main.post {
                accept(fetched); downloading = false; initialCheck = false
                result.fold({ installation = it }, { error = if (target == null || target.versionCode <= BuildConfig.VERSION_CODE)
                    "Update not available" else "Update failed" })
            }
        }
    }

    fun consumeInstallation() { installation = null }
    fun dismissError() { error = null }
    fun installerFailed() { error = "Could not open installer" }
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
