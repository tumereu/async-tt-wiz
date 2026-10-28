package org.asyncttwiz.android

import java.io.File
import java.io.EOFException
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request

data class AppRelease(val versionCode: Int, val versionName: String, val sha256: String, val size: Long) {
    companion object {
        fun parse(text: String): AppRelease {
            require(text.length <= 16_384)
            val json = Json.parseToJsonElement(text).jsonObject
            return AppRelease(json.getValue("versionCode").jsonPrimitive.int,
                json.getValue("versionName").jsonPrimitive.content,
                json.getValue("sha256").jsonPrimitive.content,
                json.getValue("size").jsonPrimitive.long).also {
                require(it.versionCode > 0 && it.versionName.isNotBlank() && it.versionName.length <= 100)
                require(it.sha256.matches(Regex("[0-9a-f]{64}")) && it.size in 1..536_870_912L)
            }
        }
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
    sleep: (Long) -> Unit = Thread::sleep, progress: (Int) -> Unit): File {
    directory.mkdirs()
    val apk = File(directory, "${target.sha256}.apk")
    // Also verify cache hits before every handoff to the system installer.
    if (verifyAppApk(apk, target)) return apk
    val part = File(directory, "${target.sha256}.part")
    directory.listFiles()?.filter { it != part }?.forEach { it.delete() }
    if (part.length() > target.size) part.delete()
    if (directory.usableSpace < target.size - part.length() + 16_000_000L)
        throw AppUpdateFailure("Not enough free storage for the update (needs about ${target.size / 1_000_000 + 16} MB). " +
            "Free some space and try again.")
    // Mobile connections drop: retry a few times, each attempt continuing from what is already saved.
    var attempt = 0
    while (part.length() < target.size) {
        try { downloadAppApkPart(client, url, target, part, progress) }
        catch (failure: IOException) {
            if (++attempt == 3) throw failure
            sleep(2_000L * attempt)
            continue
        }
        if (part.length() < target.size && ++attempt == 3) throw EOFException("Download ended early")
    }
    if (!verifyAppApk(part, target)) {
        part.delete()
        throw AppUpdateFailure("The downloaded update was damaged. Tap Update to try again.")
    }
    check(part.renameTo(apk))
    return apk
}

private fun downloadAppApkPart(client: OkHttpClient, url: String, target: AppRelease, part: File, progress: (Int) -> Unit) {
    val offset = part.length()
    val request = Request.Builder().url(url).apply {
        // If-Range: a newer release published meanwhile is sent whole instead of appended.
        if (offset > 0) header("Range", "bytes=$offset-").header("If-Range", "\"${target.sha256}\"")
    }.build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful)
            throw AppUpdateFailure("The update server isn't responding (error ${response.code}). Try again later.")
        val start = if (response.code == 206 && response.header("Content-Range").orEmpty().startsWith("bytes $offset-")) offset else 0L
        val body = response.body!!
        if (body.contentLength() != -1L && body.contentLength() != target.size - start) {
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
                    if (total > target.size) { part.delete(); throw AppUpdateFailure("The downloaded update was damaged. Tap Update to try again.") }
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
