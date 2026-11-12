@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.asyncttwiz.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

// The browser's card pictures (docs/specs/web-client.md §1 item 4): Scryfall's card data names the image, what came is kept
// for good (LOB-097), failures wait before the next try. Over a fake fetch and an in-memory Cache API.
class BrowserArtworkTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1, 2, 3)
    private val card = """{"object":"card","id":"$id","layout":"normal","image_uris":{"normal":"https://cards.example/n.jpg","large":"https://cards.example/l.jpg"}}"""

    private class Transport(val answers: MutableMap<String, ArrayDeque<ArtworkResponse>>) : AsyncArtworkTransport {
        val requests = mutableListOf<String>()
        override suspend fun get(url: String, maximum: Long): ArtworkResponse {
            requests += url
            return answers[url]?.removeFirstOrNull() ?: ArtworkResponse(404)
        }
    }

    private fun answers(vararg pairs: Pair<String, ArtworkResponse>) =
        pairs.groupBy({ it.first }, { it.second }).mapValues { ArrayDeque(it.value) }.toMutableMap()

    private fun metadata() = ArtworkResponse(200, card.encodeToByteArray(), "application/json")
    private fun image() = ArtworkResponse(200, jpeg, "image/jpeg")
    private val api = "https://api.scryfall.com/cards/"

    @Test
    fun aPictureIsLookedUpDownloadedAndKeptForGood() = runTest {
        val transport = Transport(answers("$api$id" to metadata(), "https://cards.example/n.jpg" to image()))
        val store = MemoryArtworkStore()
        val repository = BrowserArtworkRepository(store, transport, now = { currentTime })
        val reference = ArtworkReference(id, 0)
        assertContentEquals(jpeg, repository.load(reference))
        assertEquals(listOf("$api$id", "https://cards.example/n.jpg"), transport.requests)
        // A new page (a new repository over the same kept pictures) asks nothing again
        val again = BrowserArtworkRepository(store, transport, now = { currentTime })
        assertContentEquals(jpeg, again.load(reference))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun aMissingImageFallsBackToTheNextSize() = runTest {
        val transport = Transport(answers("$api$id" to metadata(), "https://cards.example/l.jpg" to image()))
        val repository = BrowserArtworkRepository(MemoryArtworkStore(), transport, now = { currentTime })
        assertContentEquals(jpeg, repository.load(ArtworkReference(id, 0)))
        assertEquals(listOf("$api$id", "https://cards.example/n.jpg", "https://cards.example/l.jpg"), transport.requests)
    }

    @Test
    fun aFailureWaitsBeforeItIsTriedAgain() = runTest {
        val transport = Transport(answers("$api$id" to ArtworkResponse(503), "$api$id" to metadata(),
            "https://cards.example/n.jpg" to image()))
        val repository = BrowserArtworkRepository(MemoryArtworkStore(), transport, now = { currentTime })
        val reference = ArtworkReference(id, 0)
        assertNull(repository.load(reference))
        assertNull(repository.load(reference))
        assertEquals(1, transport.requests.size)
        advanceTimeBy(BrowserArtworkRepository.backoff(1) + 1)
        assertContentEquals(jpeg, repository.load(reference))
    }

    @Test
    fun connectivityComingBackEndsTheWait() = runTest {
        val transport = Transport(answers("$api$id" to ArtworkResponse(503), "$api$id" to metadata(),
            "https://cards.example/n.jpg" to image()))
        val repository = BrowserArtworkRepository(MemoryArtworkStore(), transport, now = { currentTime })
        val reference = ArtworkReference(id, 0)
        var result: String? = null
        val attempts = mutableListOf<Boolean>()
        val job = launch { result = repository.loadDecoded(reference, { attempts += it }) { bytes -> bytes.size.toString() } }
        runCurrent()
        assertEquals(listOf(false), attempts)
        repository.connectivityRestored()
        runCurrent()
        job.join()
        assertEquals(jpeg.size.toString(), result)
        assertEquals(listOf(false, true), attempts)
    }

    @Test
    fun bytesThatDontDecodeAreDroppedAndTriedLater() = runTest {
        val transport = Transport(answers("$api$id" to metadata(), "https://cards.example/n.jpg" to image(),
            "https://cards.example/n.jpg" to image()))
        val store = MemoryArtworkStore()
        val repository = BrowserArtworkRepository(store, transport, now = { currentTime })
        val reference = ArtworkReference(id, 0)
        var decodes = 0
        val decoded = repository.loadDecoded(reference) { bytes -> if (++decodes == 1) null else bytes }
        assertContentEquals(jpeg, decoded)
        assertEquals(2, decodes)
        assertEquals(2, transport.requests.count { it.endsWith("n.jpg") })
    }

    @Test
    fun onlyPicturesAreKeptAndALookupIsMadeOnce() = runTest {
        val transport = Transport(answers("$api$id" to metadata(),
            "https://cards.example/n.jpg" to ArtworkResponse(200, "<html>".encodeToByteArray(), "image/jpeg")))
        val repository = BrowserArtworkRepository(MemoryArtworkStore(), transport, now = { currentTime })
        val loads = List(3) { async { repository.load(ArtworkReference(id, 0)) } }.awaitAll()
        assertTrue(loads.all { it == null })
        assertEquals(1, transport.requests.count { it.startsWith(api) })
    }

    @Test
    fun onlyHttpsAddressesAreFetched() = runTest {
        val transport = Transport(answers())
        val repository = BrowserArtworkRepository(MemoryArtworkStore(), transport, now = { currentTime })
        assertNull(repository.load(ArtworkReference(null, 0, customUrl = "http://cards.example/x.jpg")))
        assertNull(repository.load(ArtworkReference("not-an-id", 0)))
        assertTrue(transport.requests.isEmpty())
    }
}
