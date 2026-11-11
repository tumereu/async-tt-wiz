package org.asyncttwiz.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * What a large cube's first screen asks of the network (LOB-097): a real repository, disk cache, download gate and the
 * provider's 100 ms API spacing, with a fake network behind them. It prints how long the tiles take; only the
 * number of requests is checked, never the time.
 */
class ArtworkFirstVisitTest {
    @TempDir lateinit var temporary: Path
    private val origin = "http://127.0.0.1:12345"
    private val tiles = TILE_COLUMNS * TILE_ROWS

    private inner class FakeNetwork {
        val metadata = AtomicInteger()
        val images = AtomicInteger()
        private val throttle = ArtworkThrottle()
        private val picture = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) + ByteArray(PICTURE_BYTES)
        val transport = ArtworkTransport { url, _ ->
            if (url.contains("/cards/")) {
                throttle.acquire()
                metadata.incrementAndGet()
                Thread.sleep(LOOKUP_MILLIS)
                val id = url.substringAfterLast('/')
                ArtworkResponse(200, """{"object":"card","id":"$id","image_uris":{"normal":"$origin/image/$id"}}""".toByteArray(), "application/json")
            } else {
                images.incrementAndGet()
                Thread.sleep(PICTURE_MILLIS)
                ArtworkResponse(200, picture, "image/png")
            }
        }
    }

    private fun repository(network: FakeNetwork) = HttpArtworkRepository(ArtworkDiskCache(temporary.resolve("art").toFile()),
        network.transport, ArtworkUrlPolicy(origin), "$origin/cards/")

    /** Loads every tile at once, as the screen's tiles do, and gives the time to the first and last picture. */
    private fun visit(art: HttpArtworkRepository, cards: List<ArtworkReference>): Pair<Long, Long> {
        val pool = Executors.newFixedThreadPool(cards.size)
        try {
            val start = System.nanoTime()
            val done = pool.invokeAll(cards.map { card -> Callable {
                assertNotNull(art.load(card))
                (System.nanoTime() - start) / 1_000_000
            } }).map { it.get() }
            return done.min() to done.max()
        } finally { pool.shutdownNow() }
    }

    @Test fun aLargeCubesFirstScreenAsksOneLookupAndOnePicturePerTileAndAReturnVisitAsksNothing() {
        val cube = List(CUBE_CARDS) { ArtworkReference(UUID.nameUUIDFromBytes("card-$it".toByteArray()).toString(), 0) }
        val screen = cube.take(tiles)
        val network = FakeNetwork()
        val (firstPicture, lastPicture) = visit(repository(network), screen)
        println("ART first visit, $CUBE_CARDS card cube, first screen of $tiles tiles " +
            "($LOOKUP_MILLIS ms a lookup, $PICTURE_MILLIS ms a picture): ${network.metadata.get()} lookups + " +
            "${network.images.get()} pictures = ${network.metadata.get() + network.images.get()} requests; " +
            "first picture after $firstPicture ms, last after $lastPicture ms")
        assertEquals(tiles, network.metadata.get())
        assertEquals(tiles, network.images.get())

        val back = FakeNetwork()
        val (_, kept) = visit(repository(back), screen)
        println("ART return visit, a new app run: ${back.metadata.get() + back.images.get()} requests; last picture after $kept ms")
        assertEquals(0, back.metadata.get() + back.images.get())

        // The rest of the cube as it would come if a player scrolled through it all, spaced by the provider's API limit.
        val rest = FakeNetwork()
        val whole = cube.drop(tiles).take(WHOLE_SAMPLE)
        val (_, sample) = visit(repository(rest), whole)
        println("ART scrolling on: ${rest.metadata.get() + rest.images.get()} requests for ${whole.size} more tiles in $sample ms " +
            "(${sample / whole.size} ms a tile; the $CUBE_CARDS card cube at that rate takes ${sample * CUBE_CARDS / whole.size / 1000} s)")
    }

    private companion object {
        const val CUBE_CARDS = 540
        // The saved cube view starts at 5 columns in portrait; a tile is about 100 dp tall on a 800 dp screen.
        const val TILE_COLUMNS = 5
        const val TILE_ROWS = 8
        const val WHOLE_SAMPLE = 60
        const val LOOKUP_MILLIS = 150L
        const val PICTURE_MILLIS = 250L
        const val PICTURE_BYTES = 120 * 1024
    }
}
