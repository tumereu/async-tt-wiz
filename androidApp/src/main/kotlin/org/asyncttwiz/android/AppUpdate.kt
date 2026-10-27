package org.asyncttwiz.android

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long

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
