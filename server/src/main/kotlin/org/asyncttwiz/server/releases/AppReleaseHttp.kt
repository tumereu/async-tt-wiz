package org.asyncttwiz.server.releases

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long

/** Public distribution is independent of accounts and database readiness. APKs are immutable. */
class AppReleaseHttp(private val directory: Path?) {
    constructor() : this(System.getenv("APP_RELEASE_DIR")?.takeIf(String::isNotBlank)?.let(Path::of))

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val route = request.requestURI
        if (route != "/app/latest" && route != "/app/apk") return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        if (request.method != "GET") {
            response.status = 405; response.setHeader("Allow", "GET"); return true
        }
        val release = runCatching {
            val root = directory ?: error("Not configured")
            val metadata = root.resolve("latest.json")
            require(Files.size(metadata) in 1..16_384)
            val bytes = Files.readAllBytes(metadata)
            val json = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            require(json.getValue("versionCode").jsonPrimitive.int > 0)
            require(json.getValue("versionName").jsonPrimitive.content.isNotBlank())
            val hash = json.getValue("sha256").jsonPrimitive.content
            require(hash.matches(Regex("[0-9a-f]{64}")))
            // Optional GitHub Releases mirror the app offers as a second browser download.
            json["githubUrl"]?.let { github ->
                val url = (github as JsonPrimitive).also { require(it.isString) }.content
                require(url.length <= 500 && URI(url).let { it.scheme == "https" && !it.host.isNullOrEmpty() })
            }
            val apk = root.resolve("$hash.apk")
            require(Files.isRegularFile(apk) && Files.size(apk) == json.getValue("size").jsonPrimitive.long && Files.size(apk) > 0)
            Triple(bytes, apk, json.getValue("versionName").jsonPrimitive.content)
        }.getOrNull()
        if (release == null) { response.status = 404; return true }
        if (route == "/app/latest") {
            response.contentType = "application/json"
            response.setContentLengthLong(release.first.size.toLong())
            response.outputStream.write(release.first)
        } else {
            val size = Files.size(release.second)
            val tag = "\"${release.second.fileName.toString().removeSuffix(".apk")}\""
            response.contentType = "application/vnd.android.package-archive"
            response.setHeader("Accept-Ranges", "bytes")
            response.setHeader("ETag", tag)
            // Browsers save the fallback download under a recognizable name.
            val name = release.third.replace(Regex("[^0-9A-Za-z._-]"), "_")
            response.setHeader("Content-Disposition", "attachment; filename=\"async-tabletop-wizards-$name.apk\"")
            // Only an open-ended range of this same immutable APK resumes; anything else gets the whole file.
            val start = request.getHeader("Range")?.takeIf { request.getHeader("If-Range").let { it == null || it == tag } }
                ?.let { Regex("bytes=(\\d{1,18})-").matchEntire(it.trim())?.groupValues?.get(1)?.toLong() }
            if (start != null && start >= size) {
                response.status = 416; response.setHeader("Content-Range", "bytes */$size"); return true
            }
            val offset = start ?: 0L
            if (start != null) {
                response.status = 206
                response.setHeader("Content-Range", "bytes $start-${size - 1}/$size")
            }
            response.setContentLengthLong(size - offset)
            Files.newInputStream(release.second).use { it.skipNBytes(offset); it.copyTo(response.outputStream) }
        }
        return true
    }
}
