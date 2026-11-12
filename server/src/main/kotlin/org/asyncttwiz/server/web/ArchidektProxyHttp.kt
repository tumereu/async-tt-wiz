package org.asyncttwiz.server.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.util.concurrent.Semaphore

/**
 * The web client's Archidekt deck import (LOB-061, docs/specs/web-client.md §2.3): Archidekt allows no browser requests,
 * so `GET /v1/import/archidekt/{id}` reads that deck's public JSON from Archidekt's API for the page. Open without sign-in
 * (the Android app reads Archidekt directly, also without credentials), but only a deck by its number, from the fixed
 * [upstream], without redirects, at most [MAX_BODY] bytes, two at a time and [PER_MINUTE] a minute in all, so the server
 * can't be used to reach anything else or to flood Archidekt.
 */
class ArchidektProxyHttp(private val upstream: String = "https://archidekt.com", private val clock: Clock = Clock.systemUTC()) {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5)).build()
    private val concurrent = Semaphore(2)
    private var window = 0L
    private var used = 0

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val path = request.requestURI
        if (!path.startsWith(PREFIX)) return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        if (request.method != "GET") {
            response.setHeader("Allow", "GET")
            return error(response, 405, "method_not_allowed")
        }
        val id = path.removePrefix(PREFIX)
        if (!ID.matches(id)) return error(response, 404, "not_found")
        if (!admit()) return error(response, 429, "busy")
        if (!concurrent.tryAcquire()) return error(response, 429, "busy")
        try {
            val upstreamResponse = client.send(HttpRequest.newBuilder(URI("$upstream/api/decks/$id/")).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").header("User-Agent", "ATW deck import").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream())
            upstreamResponse.body().use { body ->
                when (upstreamResponse.statusCode()) {
                    200 -> {
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = body.read(buffer)
                            if (read < 0) break
                            bytes.write(buffer, 0, read)
                            if (bytes.size() > MAX_BODY) return error(response, 502, "too_large")
                        }
                        response.status = 200
                        response.contentType = "application/json;charset=UTF-8"
                        response.setContentLengthLong(bytes.size().toLong())
                        bytes.writeTo(response.outputStream)
                    }
                    404 -> error(response, 404, "not_found")
                    else -> error(response, 502, "upstream_failed")
                }
            }
        } catch (_: java.io.IOException) {
            error(response, 502, "upstream_failed")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            error(response, 502, "upstream_failed")
        } finally {
            concurrent.release()
        }
        return true
    }

    /** One more import this minute, unless [PER_MINUTE] went already. */
    @Synchronized private fun admit(): Boolean {
        val minute = clock.millis() / 60_000
        if (minute != window) { window = minute; used = 0 }
        return (used < PER_MINUTE).also { if (it) used++ }
    }

    private fun error(response: HttpServletResponse, status: Int, code: String): Boolean {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"code":"$code"}""") // fixed codes only, never request input
        return true
    }

    companion object {
        const val PREFIX = "/v1/import/archidekt/"
        const val MAX_BODY = 16 * 1024 * 1024
        const val PER_MINUTE = 30
        private val ID = Regex("[1-9][0-9]{0,17}")
    }
}
