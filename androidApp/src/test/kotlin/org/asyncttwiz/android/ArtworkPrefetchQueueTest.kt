package org.asyncttwiz.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.asyncttwiz.storage.ArtworkReference
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class ArtworkPrefetchQueueTest {
    private fun references(name: String, count: Int) = (1..count).map {
        ArtworkReference(UUID.nameUUIDFromBytes("$name-$it".toByteArray()).toString(), 0, name, "art_crop")
    }

    private suspend fun settle(condition: () -> Boolean) {
        repeat(10_000) { if (condition()) return; yield() }
        fail("Prefetch did not settle")
    }

    @Test fun boundedNewestFirstProgressThenIdle() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<ArtworkReference>()
        var running = 0; var peak = 0
        val queue = ArtworkPrefetchQueue(this, parallelism = 2) { reference ->
            started += reference; running++; peak = maxOf(peak, running)
            gate.await(); running--
            reference.version != "deck" || reference != started.first()
        }
        val deck = references("deck", 4)
        val game = references("game", 3)
        queue.submit("deck", deck + deck.first())
        assertEquals(ArtworkPrefetchProgress(0, 4), queue.progress.value)
        settle { started.size == 2 }
        queue.submit("game", game)
        assertEquals(ArtworkPrefetchProgress(0, 7), queue.progress.value)
        gate.complete(Unit)
        settle { queue.progress.value == null }
        assertEquals(2, peak)
        assertEquals(deck.take(2) + game + deck.drop(2), started, "the newest batch overtakes queued work")
        assertEquals(7, started.distinct().size, "a failed attempt is not retried by the prefetch itself")
    }

    @Test fun cancelledAndReplacedBatchesDropQueuedWork() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<ArtworkReference>()
        val queue = ArtworkPrefetchQueue(this, parallelism = 1) { started += it; gate.await(); true }
        val first = references("game", 5)
        queue.submit("game:1", first)
        settle { started.size == 1 }
        queue.cancel("game:1")
        assertNull(queue.progress.value)
        val replacement = references("deck", 2)
        queue.submit("deck:1", references("old", 3))
        queue.submit("deck:1", replacement)
        assertEquals(ArtworkPrefetchProgress(0, 2), queue.progress.value)
        gate.complete(Unit)
        settle { queue.progress.value == null }
        assertEquals(listOf(first.first()) + replacement, started)
    }

    // LOB-099
    @Test fun inspectorPlaceholderText() {
        assertEquals("Downloading artwork…", inspectorArtworkStatus(false, null))
        assertEquals("Downloading artwork 13 of 80", inspectorArtworkStatus(false, ArtworkPrefetchProgress(12, 80)))
        assertEquals("Downloading artwork 80 of 80", inspectorArtworkStatus(false, ArtworkPrefetchProgress(80, 80)))
        assertEquals("Artwork unavailable", inspectorArtworkStatus(true, ArtworkPrefetchProgress(12, 80)))
    }
}
