package org.asyncttwiz.android

import java.io.File
import java.io.EOFException
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request

/** [githubUrl] is the same APK on GitHub Releases: the app downloads it from there, the server copy is the fallback. */
data class AppRelease(val versionCode: Int, val versionName: String, val sha256: String, val size: Long,
    val githubUrl: String? = null) {
    companion object {
        fun parse(text: String): AppRelease {
            require(text.length <= 16_384)
            val json = Json.parseToJsonElement(text).jsonObject
            return AppRelease(json.getValue("versionCode").jsonPrimitive.int,
                json.getValue("versionName").jsonPrimitive.content,
                json.getValue("sha256").jsonPrimitive.content,
                json.getValue("size").jsonPrimitive.long,
                // A bad GitHub link only drops it; the update then comes from the server.
                (json["githubUrl"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(::isHttpsUrl)).also {
                require(it.versionCode > 0 && it.versionName.isNotBlank() && it.versionName.length <= 100)
                require(it.sha256.matches(Regex("[0-9a-f]{64}")) && it.size in 1..536_870_912L)
            }
        }

        private fun isHttpsUrl(url: String) = url.length <= 500 &&
            runCatching { URI(url).let { it.scheme == "https" && !it.host.isNullOrEmpty() } }.getOrDefault(false)
    }
}

/** A failed/404 check or a server rollback cannot erase an already known required update. */
fun newestAppRelease(cached: AppRelease?, fetched: AppRelease?): AppRelease? =
    listOfNotNull(cached, fetched).maxByOrNull { it.versionCode }

fun appUpdateRequired(installed: Int, release: AppRelease?, incompatibleRules: Boolean = false): Boolean =
    incompatibleRules || (release?.versionCode ?: 0) > installed

fun verifyAppApk(file: File, release: AppRelease): Boolean {
    if (!file.isFile || file.length() != release.size) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == release.sha256
}

/** Expected update failures carry a player-facing message; [browserFallback] is off when a browser can't help either. */
class AppUpdateFailure(message: String, val browserFallback: Boolean = true) : Exception(message)

/** [detail] names the underlying error so a player can pass it on when reporting a problem. */
data class AppUpdateProblem(val message: String, val detail: String? = null, val browserFallback: Boolean = true)

fun appUpdateProblem(failure: Throwable): AppUpdateProblem {
    val detail = (failure.message?.let { "${failure.javaClass.simpleName}: $it" } ?: failure.javaClass.simpleName).take(160)
    return when {
        failure is AppUpdateFailure -> AppUpdateProblem(failure.message!!, browserFallback = failure.browserFallback)
        failure is UnknownHostException || failure is ConnectException || failure is NoRouteToHostException ->
            AppUpdateProblem("Can't reach the update server. Check your internet connection and try again.", detail)
        failure is IOException && failure.message.orEmpty().contains("ENOSPC") ->
            AppUpdateProblem("Not enough free storage for the update. Free some space and try again.", detail)
        failure is IOException -> AppUpdateProblem("The download was interrupted. Tap Update to continue where it stopped.", detail)
        else -> AppUpdateProblem("Update failed.", detail)
    }
}

/** Keeps a partial download across failures and app restarts, and resumes it on the next attempt. */
fun downloadAppApk(client: OkHttpClient, url: String, target: AppRelease, directory: File,
    sleep: (Long) -> Unit = Thread::sleep, ifRange: Boolean = true, progress: (Int) -> Unit): File {
    directory.mkdirs()
    val apk = File(directory, "${target.sha256}.apk")
    // Also verify cache hits before every handoff to the system installer.
    if (verifyAppApk(apk, target)) { AppLog.info("update", "Using verified download of ${target.versionName}"); return apk }
    val part = File(directory, "${target.sha256}.part")
    directory.listFiles()?.filter { it != part }?.forEach { it.delete() }
    if (part.length() > target.size) part.delete()
    AppLog.info("update", "Downloading ${target.versionName} (${target.versionCode}) from ${url.substringBefore('?')}, ${target.size} bytes, " +
        "${part.length()} already saved, ${directory.usableSpace} bytes free")
    if (directory.usableSpace < target.size - part.length() + 16_000_000L)
        throw AppUpdateFailure("Not enough free storage for the update (needs about ${target.size / 1_000_000 + 16} MB). " +
            "Free some space and try again.")
    // The public tunnel cuts long downloads every 10-30 MB, and mobile connections drop: keep continuing
    // from what is already saved while attempts make progress; give up after 3 attempts in a row without any.
    var attempt = 0
    while (part.length() < target.size) {
        val before = part.length()
        val failure = try { downloadAppApkPart(client, url, target, part, ifRange, progress); null } catch (failure: IOException) { failure }
        AppLog.info("update", "Attempt ended at ${part.length()} / ${target.size} bytes (${part.length() - before} new)")
        if (part.length() >= target.size) break
        if (part.length() > before) attempt = 0
        if (++attempt == 3) throw (failure ?: EOFException("Download ended early"))
            .also { AppLog.warn("update", "Giving up after 3 attempts without progress", it) }
        AppLog.warn("update", "Download interrupted, retrying in ${2 * attempt} s (no progress $attempt/3)", failure)
        sleep(2_000L * attempt)
    }
    if (!verifyAppApk(part, target)) {
        AppLog.warn("update", "Downloaded file failed verification (${part.length()} bytes)")
        part.delete()
        throw AppUpdateFailure("The downloaded update was damaged. Tap Update to try again.")
    }
    check(part.renameTo(apk))
    AppLog.info("update", "Download verified")
    return apk
}

private fun downloadAppApkPart(client: OkHttpClient, url: String, target: AppRelease, part: File, ifRange: Boolean,
    progress: (Int) -> Unit) {
    val offset = part.length()
    val request = Request.Builder().url(url).apply {
        if (offset > 0) header("Range", "bytes=$offset-")
        // Our server tags the APK by its SHA-256, so a newer release published meanwhile is sent whole instead of
        // appended. Other hosts use their own tags; there a mixed file is caught by the final checksum.
        if (offset > 0 && ifRange) header("If-Range", "\"${target.sha256}\"")
    }.build()
    AppLog.info("update", "Requesting from offset $offset")
    client.newCall(request).execute().use { response ->
        AppLog.info("update", "HTTP ${response.code}, Content-Range ${response.header("Content-Range")}, " +
            "length ${response.body?.contentLength()}, protocol ${response.protocol}")
        if (!response.isSuccessful)
            throw AppUpdateFailure("The download server isn't responding (error ${response.code}). Try again later.")
        val start = if (response.code == 206 && response.header("Content-Range").orEmpty().startsWith("bytes $offset-")) offset else 0L
        val body = response.body!!
        if (body.contentLength() != -1L && body.contentLength() != target.size - start) {
            AppLog.warn("update", "Unexpected length ${body.contentLength()} from $start; discarding partial download")
            part.delete()
            throw AppUpdateFailure("A newer update appeared during the download. Tap Update to get it.")
        }
        body.byteStream().use { input ->
            FileOutputStream(part, start > 0).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = start
                var lastPercent = -1
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    if (total > target.size) { AppLog.warn("update", "Received more than ${target.size} bytes"); part.delete(); throw AppUpdateFailure("The downloaded update was damaged. Tap Update to try again.") }
                    output.write(buffer, 0, count)
                    val percent = (total * 100 / target.size).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        progress(percent)
                    }
                }
            }
        }
    }
}
