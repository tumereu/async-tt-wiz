package org.asyncttwiz.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ArtworkRetryTest {
    @TempDir lateinit var temporary: Path

    @Test fun failuresBackOffForeverAtThirtySecondsAndConnectivityWakesEveryPendingImage() {
        var clock = 0L
        var connection = 0L
        val first = ArtworkRetrySchedule({ clock }, { connection })
        val second = ArtworkRetrySchedule({ clock }, { connection })
        for (wait in listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L) + List(100) { 30_000L }) {
            first.failed()
            assertEquals(wait, first.remainingMillis())
            clock += wait - 1
            assertEquals(1, first.remainingMillis())
            clock++
            assertEquals(0, first.remainingMillis())
        }
        first.failed(); second.failed()
        connection++
        assertEquals(0, first.remainingMillis())
        assertEquals(0, second.remainingMillis())
        first.failed()
        assertEquals(30_000, first.remainingMillis(), "reconnect must not reset failures into an aggressive loop")
        assertEquals(1_000, ArtworkRetrySchedule({ clock }, { connection }).apply { failed() }.remainingMillis())
    }

    @Test fun reconnectBypassesNegativeMetadataAndImageCachesButNotSuccessfulBytes() {
        var clock = 0L
        var calls = 0
        var status = 503
        val origin = "http://127.0.0.1:12345"
        val id = "2487c5bd-8d7f-5925-af7a-a2f6e5eb6b7a"
        val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val repository = HttpArtworkRepository(ArtworkDiskCache(temporary.resolve("art").toFile(), now = { clock }),
            ArtworkTransport { url, _, _, _ ->
                calls++
                if (status != 200) ArtworkResponse(status) else if (url.endsWith(id)) ArtworkResponse(200,
                    """{"object":"card","id":"$id","image_uris":{"normal":"$origin/image"}}""".toByteArray(), "application/json")
                else ArtworkResponse(200, png, "image/png")
            }, ArtworkUrlPolicy(origin), "$origin/cards/", { clock })
        val reference = ArtworkReference(id, 0, "v1")
        assertNull(repository.load(reference)); assertNull(repository.load(reference)); assertEquals(1, calls)
        repository.connectivityRestored()
        status = 200
        assertNotNull(repository.load(reference)); assertEquals(3, calls)
        for (failure in listOf(404, 429, 503)) {
            val custom = reference.copy(printing = null, customUrl = "$origin/$failure")
            status = failure
            assertNull(repository.load(custom)); val before = calls
            assertNull(repository.load(custom)); assertEquals(before, calls)
            repository.connectivityRestored(); status = 200
            assertNotNull(repository.load(custom)); assertEquals(before + 1, calls)
        }
        status = 503
        val before = calls
        repository.connectivityRestored()
        val custom = reference.copy(printing = null, customUrl = "$origin/503")
        assertNotNull(repository.load(custom)); assertEquals(before, calls, "reconnect retains good bytes")
    }

    @Test fun partialRasterFilesAndNetworkExceptionsRetryUntilTheyRecover() {
        var clock = 0L
        var calls = 0
        val origin = "http://127.0.0.1:12345"
        val repository = HttpArtworkRepository(ArtworkDiskCache(temporary.resolve("partial").toFile(), now = { clock }),
            ArtworkTransport { _, _, _, _ ->
                when (++calls) {
                    1 -> throw java.net.SocketTimeoutException()
                    2 -> ArtworkResponse(200, byteArrayOf(137.toByte(), 80), "image/png")
                    else -> ArtworkResponse(200, byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), "image/png")
                }
            }, ArtworkUrlPolicy(origin), "$origin/cards/", { clock })
        val reference = ArtworkReference(null, 0, "v1", customUrl = "$origin/image")
        assertNull(repository.load(reference)); clock += 1_000
        assertNull(repository.load(reference)); clock += 1_999
        assertNull(repository.load(reference)); assertEquals(2, calls)
        clock++
        assertNotNull(repository.load(reference)); assertEquals(3, calls)
    }
}
