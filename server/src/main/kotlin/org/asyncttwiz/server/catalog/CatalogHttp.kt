package org.asyncttwiz.server.catalog

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.asyncttwiz.catalog.CatalogVersion

/**
 * The card catalog's endpoints (docs/specs/web-client.md section 3.4), open without sign-in: the first download comes
 * before sign-in (LOB-091). `GET /v1/catalog` says which catalog is current ([CatalogVersion]); `GET /v1/catalog/{checksum}`
 * is that file (gzip-compressed lines), or the one kept before it, cached for good since its name is its content.
 */
class CatalogHttp {
    private val json = Json

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val path = request.requestURI
        if (path != "/v1/catalog" && !path.startsWith("/v1/catalog/")) return false
        response.setHeader("X-Content-Type-Options", "nosniff")
        if (request.method != "GET") {
            response.setHeader("Cache-Control", "no-store")
            response.setHeader("Allow", "GET")
            error(response, 405, "method_not_allowed")
            return true
        }
        if (path == "/v1/catalog") {
            response.setHeader("Cache-Control", "no-store")
            val current = ServerCatalogs.current() ?: return true.also { error(response, 404, "no_catalog") }
            response.status = 200
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(json.encodeToString(current.version))
            return true
        }
        val checksum = path.removePrefix("/v1/catalog/")
        val file = ServerCatalogs.file(checksum)
        if (file == null) {
            response.setHeader("Cache-Control", "no-store")
            error(response, 404, "not_found")
            return true
        }
        val tag = "\"$checksum\""
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable")
        response.setHeader("ETag", tag)
        if (request.getHeader("If-None-Match") == tag) { response.status = 304; return true }
        response.status = 200
        response.contentType = "application/gzip"
        response.setContentLengthLong(file.length())
        file.inputStream().use { it.copyTo(response.outputStream) }
        return true
    }

    private fun error(response: HttpServletResponse, status: Int, code: String) {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"code":"$code"}""") // fixed codes only, never request input
    }
}
