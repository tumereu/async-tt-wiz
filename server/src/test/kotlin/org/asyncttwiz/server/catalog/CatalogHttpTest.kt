package org.asyncttwiz.server.catalog

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import org.asyncttwiz.catalog.CardCatalogFiles
import org.asyncttwiz.catalog.CatalogVersion
import org.asyncttwiz.server.EphemeralPorts
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.startBound
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The card catalog's endpoints (docs/specs/web-client.md section 3.4), open without sign-in, on the test cards' catalog
 * the server tests run with (CATALOG_DIR, built by :catalog:testCardsCatalog). */
class CatalogHttpTest {
    private val directory = File(checkNotNull(System.getProperty("atw.catalogDir")))

    // SYS-081
    @Test fun `the current catalog's version and its file are served without sign-in, the file cached for good`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = EphemeralPorts.settings(db.settings)
            SpringService(config).use { service ->
                val settings = service.startBound(config)
                val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                fun get(path: String, method: String = "GET", vararg headers: String): HttpResponse<ByteArray> {
                    val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path")).timeout(Duration.ofSeconds(30))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                    headers.toList().chunked(2).forEach { (name, value) -> builder.header(name, value) }
                    return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                }
                val checksum = File(directory, "current").readText().trim()
                val file = File(directory, checksum + CardCatalogFiles.SUFFIX)

                val current = get("/v1/catalog")
                assertEquals(200, current.statusCode())
                assertEquals("no-store", current.headers().firstValue("Cache-Control").orElse(""))
                val version = Json.decodeFromString(CatalogVersion.serializer(), current.body().decodeToString())
                val catalog = CardCatalogFiles.read(file)
                assertEquals(CatalogVersion(checksum, file.length(), "5.3.0+20261031", "2026-10-31", catalog.header.sets,
                    catalog.header.faces, catalog.header.printings), version)

                val download = get("/v1/catalog/$checksum")
                assertEquals(200, download.statusCode())
                assertArrayEquals(file.readBytes(), download.body())
                assertEquals("public, max-age=31536000, immutable", download.headers().firstValue("Cache-Control").orElse(""))
                assertEquals("application/gzip", download.headers().firstValue("Content-Type").orElse(""))
                assertEquals(304, get("/v1/catalog/$checksum", "GET", "If-None-Match", "\"$checksum\"").statusCode())

                assertEquals(404, get("/v1/catalog/${"0".repeat(64)}").statusCode())
                assertEquals(404, get("/v1/catalog/current").statusCode())
                assertEquals(405, get("/v1/catalog", "POST").statusCode())
            }
        }
    }
}
