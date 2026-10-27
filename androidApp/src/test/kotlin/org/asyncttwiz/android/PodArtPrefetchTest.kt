package org.asyncttwiz.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class PodArtPrefetchTest {
    private fun pack(id: String, count: Int) = DraftPrivatePack(id, 1, (1..count).map { DraftCopy("$id-$it", "$id-definition-$it") })
    private val shown = pack("shown", 15)
    private val next = pack("next", 14)
    private val third = pack("third", 13)
    private val definitions = listOf(shown, next, third).flatMap { it.cards }.associate {
        it.definition to CardDefinition(it.definition, listOf(DefinitionFace("face", it.definition,
            scryfallId = UUID.nameUUIDFromBytes(it.definition.toByteArray()).toString())))
    }

    private suspend fun settle(condition: () -> Boolean) {
        repeat(1_000) { if (condition()) return; yield() }
        fail("Prefetch did not settle")
    }

    @Test fun everyCardOfTheShownAndNextPackIsRequestedAtTileSizeWithBoundedParallelism() = runBlocking {
        val references = podPrefetchArtwork(listOf(shown, next), definitions)
        assertEquals(29, references.size)
        assertTrue(references.all { it.size == "normal" && it.printing != null })
        assertEquals((shown.cards + next.cards).map { podTileArtwork(it.definition, definitions[it.definition]) }, references)
        val gate = CompletableDeferred<Unit>()
        val requested = mutableListOf<ArtworkReference>()
        var running = 0
        var peak = 0
        val prefetcher = ArtworkPrefetcher(this, parallelism = 4) { reference ->
            requested += reference
            running++; peak = maxOf(peak, running)
            gate.await()
            running--
        }
        prefetcher.update(references)
        settle { requested.size == 4 }
        repeat(10) { yield() }
        assertEquals(4, requested.size)
        gate.complete(Unit)
        settle { requested.size == references.size && running == 0 }
        assertEquals(references, requested)
        assertEquals(4, peak)
        // Already requested art is not requested again when the same packs are shown again.
        prefetcher.update(references)
        repeat(10) { yield() }
        assertEquals(references.size, requested.size)
    }

    @Test fun advancingKeepsTheNextPackRunningAndCancelsOnlyArtNoLongerShown() = runBlocking {
        val started = mutableListOf<ArtworkReference>()
        val cancelled = mutableListOf<ArtworkReference>()
        val prefetcher = ArtworkPrefetcher(this, parallelism = 100) { reference ->
            started += reference
            try { awaitCancellation() } catch (error: CancellationException) { cancelled += reference; throw error }
        }
        val first = podPrefetchArtwork(listOf(shown, next), definitions)
        prefetcher.update(first)
        settle { started.size == first.size }
        val second = podPrefetchArtwork(listOf(next, third), definitions)
        prefetcher.update(second)
        settle { started.size == first.size + third.cards.size && cancelled.size == shown.cards.size }
        assertEquals(podPrefetchArtwork(listOf(shown), definitions).toSet(), cancelled.toSet())
        assertEquals(second.toSet(), (started - cancelled.toSet()).toSet())
        prefetcher.cancel()
        settle { cancelled.size == started.size }
    }

    @Test fun cardsWithoutRetainedPrintingsAreSkipped() {
        val bare = pack("bare", 3)
        val missing = mapOf(bare.cards[0].definition to CardDefinition("No printing", listOf(DefinitionFace("face", "No printing"))))
        assertTrue(podPrefetchArtwork(listOf(bare), missing).isEmpty())
    }
}
