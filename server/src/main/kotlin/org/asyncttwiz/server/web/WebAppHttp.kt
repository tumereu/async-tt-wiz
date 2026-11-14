package org.asyncttwiz.server.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.file.Files
import java.nio.file.Path

/**
 * The web client, served beside the API from the same origin (docs/specs/web-client.md §2.1, WEB-001, WEB-005, WEB-006),
 * from [directory] (WEB_APP_DIR: webApp's `webDist` output): `index.html` for every app address (never cached), the
 * build's files under `/web/<build>/` (named by their content's hash, so cached for good), `/web/build`, the build
 * open tabs compare theirs with, and the installable app's files at the top (WEB-054): the service worker `/sw.js`, the
 * manifest `/manifest.webmanifest` and its icons `/icons/<name>.png`, which keep their addresses across builds. Without a
 * directory holding a build, nothing here answers: the server serves no web app.
 */
class WebAppHttp(private val directory: Path?) {
    constructor() : this(System.getenv("WEB_APP_DIR")?.takeIf(String::isNotBlank)?.let(Path::of))

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val root = directory ?: return false
        if (request.method != "GET" && request.method != "HEAD") return false
        val path = request.requestURI
        val index = root.resolve("index.html")
        if (!Files.isRegularFile(index)) return false
        response.setHeader("X-Content-Type-Options", "nosniff")
        when {
            path == "/web/build" -> {
                val build = runCatching { Files.readString(root.resolve("build")).trim() }.getOrNull()
                    ?.takeIf { BUILD.matches(it) } ?: return false
                response.setHeader("Cache-Control", "no-store")
                send(request, response, "text/plain;charset=UTF-8", build.toByteArray())
            }
            path in ROOT_FILES || ICON.matches(path) -> {
                val file = root.resolve(path.removePrefix("/")).normalize()
                if (!file.startsWith(root.normalize()) || !Files.isRegularFile(file)) return false
                // Asked for again on every visit (a new build's service worker and manifest are found at once)
                response.setHeader("Cache-Control", "no-cache")
                send(request, response, type(file.fileName.toString()), Files.readAllBytes(file))
            }
            path.startsWith("/web/") -> {
                val file = file(root, path.removePrefix("/web/"))
                if (file == null) {
                    response.setHeader("Cache-Control", "no-store")
                    response.status = 404
                    return true
                }
                response.setHeader("Cache-Control", "public, max-age=31536000, immutable")
                send(request, response, type(file.fileName.toString()), Files.readAllBytes(file))
            }
            appAddress(path) -> {
                response.setHeader("Cache-Control", "no-store")
                response.setHeader("X-Frame-Options", "DENY")
                response.setHeader("Referrer-Policy", "no-referrer")
                send(request, response, "text/html;charset=UTF-8", Files.readAllBytes(index))
            }
            else -> return false
        }
        return true
    }

    /** The build's file at [relative] (`<build>/<path>`), null unless it is one: no other folder, no climbing out. */
    private fun file(root: Path, relative: String): Path? {
        val parts = relative.split('/')
        if (parts.size < 2 || !BUILD.matches(parts[0]) || parts.drop(1).any { !SEGMENT.matches(it) || it.trim('.').isEmpty() }) return null
        val file = root.resolve(relative).normalize()
        return file.takeIf { it.startsWith(root.resolve(parts[0]).normalize()) && Files.isRegularFile(it) }
    }

    private fun send(request: HttpServletRequest, response: HttpServletResponse, type: String, body: ByteArray) {
        response.status = 200
        response.contentType = type
        response.setContentLengthLong(body.size.toLong())
        if (request.method != "HEAD") response.outputStream.write(body)
    }

    companion object {
        private val BUILD = Regex("[0-9a-f]{16}")
        private val SEGMENT = Regex("[A-Za-z0-9._-]{1,128}")
        private val ID = "[A-Za-z0-9_-]{1,64}"
        private val TABS = "draft|build|stats|games"
        /** WEB-054: the installable app's files at the top of the site (a service worker's reach is its own folder's). */
        private val ROOT_FILES = setOf("/sw.js", "/manifest.webmanifest")
        private val ICON = Regex("/icons/[a-z0-9-]{1,64}\\.png")

        /** The web client's addresses (its WebRoute), each answered with the page. */
        private val ADDRESSES = listOf("/", "/games", "/games/$ID", "/pods", "/pods/$ID", "/pods/$ID/($TABS)", "/decks",
            "/decks/import", "/decks/$ID", "/decks/$ID/edit", "/cubes/new", "/cubes/$ID", "/contacts", "/settings", "/stats")
            .map { Regex(it) }

        fun appAddress(path: String): Boolean = path.removeSuffix("/").ifEmpty { "/" }.let { address -> ADDRESSES.any { it.matches(address) } }

        /** Media types by extension; browsers compile WebAssembly as it streams in only as application/wasm. */
        fun type(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html;charset=UTF-8"
            "js", "mjs" -> "text/javascript;charset=UTF-8"
            "wasm" -> "application/wasm"
            "json", "map" -> "application/json;charset=UTF-8"
            "webmanifest" -> "application/manifest+json;charset=UTF-8"
            "css" -> "text/css;charset=UTF-8"
            "txt", "md" -> "text/plain;charset=UTF-8"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff2" -> "font/woff2"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }
}
