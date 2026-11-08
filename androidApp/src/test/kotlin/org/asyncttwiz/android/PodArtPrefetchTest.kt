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

    // DRF-070
    @Test fun everyCardOfTheShownAndNextPackIsRequestedAtTileSizeWithBoundedParallelism() = runBlocking {
        val references = podPrefetchArtwork(listOf(shown, next), definitions)
        assertEquals(29, references.size)
        assertTrue(references.all { it.size == "normal" && it.printing != null })
        assertEquals((shown.cards + next.cards).map { podTileArtwork(definitions[it.definition]) }, references)
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

    // DRF-070
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

    private fun copy(id: String) = DraftCopy(id, "$id-definition")
    private fun printed(vararg ids: String) = ids.associate {
        "$it-definition" to CardDefinition("Card $it", listOf(DefinitionFace("face", "Card $it", typeLine = "Artifact",
            scryfallId = UUID.nameUUIDFromBytes(it.toByteArray()).toString())))
    }

    // DRF-070: the whole Rotisserie pool, taken cards and cards outside a search included, in board order.
    @Test fun rotisserieHandsOverTheWholePoolInBoardOrder() {
        val board = listOf(LimitedSlot(copy("b")), LimitedSlot(copy("a"), pickedBy = "bob"), LimitedSlot(copy("c")))
        val view = LimitedView(DraftMode.ROTISSERIE, "ann", listOf("ann", "bob"), emptyList(), false, board = board)
        val definitions = printed("a", "b", "c")
        val cards = podLimitedPrefetchCards(view, definitions)
        assertEquals(podRotisserieOrder(board, definitions).map { it.copy }, cards)
        assertEquals(3, podCardPrefetchArtwork(cards, definitions).size)
    }

    // DRF-070: every open Rochester pack, your packs first as the screen shows them.
    @Test fun rochesterHandsOverEveryOpenPackInScreenOrder() {
        fun pack(number: Int, current: String?, vararg ids: String) =
            LimitedPackView("p$number", number, "ann", ids.map { LimitedSlot(copy(it), pickedBy = "bob".takeIf { _ -> it == "x" }) }, current, 0)
        val view = LimitedView(DraftMode.ROCHESTER, "ann", listOf("ann", "bob"), emptyList(), false,
            packs = listOf(pack(1, "bob", "a", "x"), pack(2, "ann", "b", "c"), pack(3, null, "d")))
        assertEquals(listOf("b", "c", "a", "x", "d"), podLimitedPrefetchCards(view, emptyMap()).map { it.id })
    }

    // DRF-070: Winston hands over only the pile cards you have seen; face-down cards never reach the prefetcher.
    @Test fun winstonHandsOverSeenPileCardsButNeverFaceDownOnes() {
        val view = LimitedView(DraftMode.WINSTON, "ann", listOf("ann", "bob"), emptyList(), false, currentPlayer = "ann",
            stack = 20, piles = listOf(listOf(copy("a"), null), listOf(null), listOf(copy("b"), copy("c"), null)))
        val cards = podLimitedPrefetchCards(view, emptyMap())
        assertEquals(listOf("a", "b", "c"), cards.map { it.id })
        assertEquals(3, podCardPrefetchArtwork(cards, printed("a", "b", "c")).size)
    }

    // DRF-070: every Winchester pile, bottom pile first.
    @Test fun winchesterHandsOverEveryPile() {
        val view = LimitedView(DraftMode.WINCHESTER, "ann", listOf("ann", "bob"), emptyList(), false,
            piles = listOf(listOf(copy("a")), emptyList(), listOf(copy("b"), copy("c"))))
        assertEquals(listOf("a", "b", "c"), podLimitedPrefetchCards(view, emptyMap()).map { it.id })
        // DRF-158: with several tables, every table's piles in table order.
        val tables = view.copy(tables = listOf(LimitedTableView("ann", 0, 3, listOf(listOf(copy("a")))),
            LimitedTableView("bob", 0, 3, listOf(listOf(copy("d")), listOf(copy("e"))))))
        assertEquals(listOf("a", "d", "e"), podLimitedPrefetchCards(tables, emptyMap()).map { it.id })
    }

    // DRF-070: the Picks view hands over each opponent's seen picks in chip order, never a card back.
    @Test fun picksViewHandsOverSeenPicksOnly() {
        val picks = listOf(PodOpponentPicks(AccountIdentity("bob", "Bob"), listOf(copy("a"), null, copy("b"))),
            PodOpponentPicks(AccountIdentity("cid", "Cid"), listOf(null, copy("c"))))
        assertEquals(listOf("a", "b", "c"), podPicksPrefetchCards(picks).map { it.id })
    }

    @Test fun cardsWithoutRetainedPrintingsAreSkipped() {
        val bare = pack("bare", 3)
        val missing = mapOf(bare.cards[0].definition to CardDefinition("No printing", listOf(DefinitionFace("face", "No printing"))))
        assertTrue(podPrefetchArtwork(listOf(bare), missing).isEmpty())
    }
}
