package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Custom booster: Booster's pack passing with the creator's passes ("Pick n, Burn k") in place of keep and burn. */
class CustomBoosterEngineTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 7).toByte() })
    private fun settings(passes: List<DraftPass>, packs: Int = 1, cards: Int = 8, open: Int = 1) =
        DraftSettings.customBooster().copy(packsPerPlayer = packs, cardsPerPack = cards, packsOpenAtOnce = open,
            colorBalance = false, passes = passes)
    private fun start(settings: DraftSettings, players: Int = 2) = DraftEngine.start(List(players) { "p$it" },
        List(players * settings.packsPerPlayer * settings.cardsPerPack) { DraftCubeEntry("card-$it", 1) }, settings, seed)

    /** The head pack's first cards as keeps and the next ones as burns, as the pack's pass asks (none near the end). */
    private fun choice(state: DraftState, player: String): DraftPick {
        val id = state.queues.getValue(player).first()
        val cards = state.packs.first { it.id == id }.cards
        val taken = state.log.count { !it.automatic && it.packId == id }
        val keep = state.settings.keepAt(taken)
        val burns = if (state.settings.keepsOnly(cards.size, keep.toLong(), taken)) emptyList()
            else cards.drop(keep).take(state.settings.burnAt(taken))
        return DraftPick(player, id, cards.take(keep), burns)
    }

    private fun pick(state: DraftState, player: String) = assertNotNull(DraftEngine.pick(state, choice(state, player)).state)
    private fun ready(state: DraftState) = state.seating.first { state.queues.getValue(it).isNotEmpty() }

    // DRF-262: the mode's defaults and the passes' limits
    @Test fun defaultsAndLimits() {
        val base = DraftSettings.defaults(DraftMode.CUSTOM_BOOSTER)
        assertEquals(List(7) { DraftPass(1, 1) }, base.passes)
        assertEquals(3, base.packsPerPlayer); assertEquals(15, base.cardsPerPack); assertEquals(1, base.packsOpenAtOnce)
        assertTrue(base.validate(2).isEmpty())
        assertTrue(base.copy(passes = List(30) { DraftPass(1) }).validate(8).isEmpty())
        assertTrue(base.copy(passes = listOf(DraftPass(30, 0), DraftPass(1, 29))).validate(2).isEmpty())
        for (passes in listOf(emptyList(), List(31) { DraftPass(1) }, listOf(DraftPass(0, 1)), listOf(DraftPass(1, -1)),
            listOf(DraftPass(20, 11)))) assertContains(base.copy(passes = passes).validate(2), DraftSettingsIssue.PASSES)
        // Booster's keep, burn and discard play no part in a Custom booster
        assertTrue(base.copy(keep = 0, burn = -1, discardRemainingAt = 99).validate(2).isEmpty())
        assertTrue(DraftMode.CUSTOM_BOOSTER.packPassing)
        assertEquals(2..8, DraftMode.CUSTOM_BOOSTER.players)
    }

    // DRF-260, DRF-261: each pass picks and burns as its row says; when the passes run out, the rest is thrown away
    @Test fun passesRunInOrderAndTheirEndDiscardsTheRest() {
        var state = start(settings(listOf(DraftPass(2, 1), DraftPass(1, 1), DraftPass(1, 0))))
        val first = state.queues.getValue("p0").first()
        assertEquals(8, state.packs.first { it.id == first }.cards.size)
        // A pass asking other counts is refused
        val wrong = choice(state, "p0").let { it.copy(keep = it.keep.take(1)) }
        assertEquals(DraftPickRejection.WRONG_COUNTS, DraftEngine.pick(state, wrong).reason)
        val noBurn = choice(state, "p0").copy(burn = emptyList())
        assertEquals(DraftPickRejection.WRONG_COUNTS, DraftEngine.pick(state, noBurn).reason)
        state = pick(state, "p0") // pass 1: pick 2, burn 1 → 5 left, to p1
        assertEquals(5, state.packs.first { it.id == first }.cards.size)
        assertEquals(2, DraftEngine.privateView(state, "p1").queuedPacks.size + 1)
        val p1Head = state.queues.getValue("p1").first()
        assertEquals(p1Head, DraftEngine.privateView(state, "p1").head?.id)
        // p1 has their own fresh pack first (fresher packs first, DRF-049), on its first pass
        assertEquals(1, DraftEngine.privateView(state, "p1").head?.pick)
        state = pick(state, "p1") // p1's own pack, pass 1
        assertEquals(first, state.queues.getValue("p1").first())
        assertEquals(2, DraftEngine.privateView(state, "p1").head?.pick)
        val second = choice(state, "p1")
        assertEquals(1, second.keep.size); assertEquals(1, second.burn.size)
        state = pick(state, "p1") // pass 2 of the first pack: pick 1, burn 1 → 3 left, back to p0
        assertEquals(3, state.packs.first { it.id == first }.cards.size)
        while (state.queues.getValue("p0").first() != first) state = pick(state, "p0")
        val last = choice(state, "p0")
        assertEquals(1, last.keep.size); assertTrue(last.burn.isEmpty())
        // DRF-261 (owner 2026-11-22): on the last pass only the pick is chosen; a partial burn is refused
        val partial = last.copy(burn = state.packs.first { it.id == first }.cards.filter { it !in last.keep }.take(1))
        assertEquals(DraftPickRejection.WRONG_COUNTS, DraftEngine.pick(state, partial).reason)
        state = pick(state, "p0") // pass 3, the last: pick 1 → the other 2 burn
        val entry = state.log.last { it.packId == first }
        assertEquals(2, entry.burned.size); assertTrue(entry.discarded.isEmpty())
        assertTrue(state.packs.first { it.id == first }.cards.isEmpty())
        assertEquals(listOf(listOf(2, 1, 0), listOf(1, 1, 0), listOf(1, 2, 0)),
            state.log.filter { it.packId == first }.map { listOf(it.kept.size, it.burned.size, it.discarded.size) })
    }

    // DRF-261 (owner 2026-11-22): a last pass burns every card not picked whatever the pack's size, when the pick names them
    // all too; a bot's pick ([DraftBotSeats]) on it names no burns and the rest burn the same
    @Test fun theLastPassBurnsEveryCardNotPicked() {
        var state = start(settings(listOf(DraftPass(1, 0)), cards = 9))
        val own = state.queues.getValue("p0").first()
        val cards = state.packs.first { it.id == own }.cards
        assertTrue(state.settings.keepsOnly(cards.size, 1, 0))
        state = assertNotNull(DraftEngine.pick(state, DraftPick("p0", own, cards.take(1), cards.drop(1))).state)
        assertEquals(listOf(1, 8, 0), state.log.last { it.packId == own }.let { listOf(it.kept.size, it.burned.size, it.discarded.size) })
        val theirs = state.queues.getValue("p1").first()
        val move = assertNotNull(DraftBotSeats.booster(state, "p1") { DraftBotCard(it, null) })
        assertTrue(move.move.burn.isEmpty())
        state = assertNotNull(DraftEngine.pick(state, move.move).state)
        assertEquals(listOf(1, 8, 0), state.log.last { it.packId == theirs }.let { listOf(it.kept.size, it.burned.size, it.discarded.size) })
        assertTrue(state.finished)
    }

    // DRF-261: a pack that runs out before its passes is done; one too small for its pass's picks is given automatically
    @Test fun packRunningOutFirstEndsItAndSmallPacksAreGivenAutomatically() {
        var state = start(settings(listOf(DraftPass(1, 2), DraftPass(2, 0), DraftPass(1, 0), DraftPass(1, 0)), cards = 5))
        val first = state.queues.getValue("p0").first()
        state = pick(state, "p0") // pick 1, burn 2 → 2 left: the next pass picks 2, so p1 gets them automatically
        val award = state.log.last { it.packId == first }
        assertTrue(award.automatic); assertEquals("p1", award.player); assertEquals(2, award.kept.size)
        assertTrue(state.packs.first { it.id == first }.cards.isEmpty())
        // p1's own pack, then the near-end rule (DRF-045): with keep + burn or fewer left only the keeps are chosen
        state = start(settings(listOf(DraftPass(1, 3), DraftPass(1, 1)), cards = 6))
        val own = state.queues.getValue("p0").first()
        state = pick(state, "p0") // 2 left for p1's pass 2 (pick 1, burn 1): keeps only, the other card burns
        while (state.queues.getValue("p1").first() != own) state = pick(state, "p1")
        val view = DraftEngine.privateView(state, "p1").head!!
        assertEquals(2, view.cards.size)
        val keepOnly = DraftPick("p1", own, view.cards.take(1).map { it.id })
        state = assertNotNull(DraftEngine.pick(state, keepOnly).state)
        assertEquals(listOf(1, 1, 0), state.log.last { it.packId == own }.let { listOf(it.kept.size, it.burned.size, it.discarded.size) })
    }

    // DRF-260: every card is accounted for over whole drafts of 2–8 players, several open packs, and Librarians' extra keeps
    @Test fun wholeDraftsConserveCardsAndPredictPicks() {
        val scheduler = RandomV1.Words(seed)
        for (players in 2..8) for (open in listOf(1, 2)) {
            val passes = listOf(DraftPass(1 + players % 2, players % 3), DraftPass(1, 1), DraftPass(2, 0), DraftPass(1, 2), DraftPass(1, 0))
            val settings = settings(passes, packs = 2, cards = 11, open = open)
            var state = start(settings, players)
            val dealt = state.packs.flatMap { it.cards }.toSet()
            var steps = 0
            while (!state.finished) {
                assertTrue(steps++ < 1000)
                val ready = state.seating.filter { state.queues.getValue(it).isNotEmpty() }
                val actor = ready[RandomV1.sample(ready.size, scheduler::next)]
                val before = DraftEngine.consecutivePicks(state, actor)
                state = pick(state, actor)
                // Pick counts never depend on which cards are taken: one pick is exactly one less (DRF-074)
                assertEquals(before - 1, DraftEngine.consecutivePicks(state, actor))
                val accounted = state.packs.flatMap { it.cards } + state.log.flatMap { it.kept + it.burned + it.discarded }
                assertEquals(dealt, accounted.toSet()); assertEquals(dealt.size, accounted.size)
                // No pack ever goes past its passes
                for (pack in state.packs) assertTrue(state.log.count { !it.automatic && it.packId == pack.id } <= passes.size)
            }
            assertEquals(state, Json.decodeFromString<DraftState>(Json.encodeToString(state)))
        }
    }

    // DRF-263, DRF-261 (owner 2026-11-22): burned cards show in the draft history like picks, marked as burned, the last pass's too
    @Test fun historyShowsEachPassBurns() {
        var state = start(settings(listOf(DraftPass(1, 2), DraftPass(1, 1), DraftPass(1, 0))))
        while (!state.finished) state = pick(state, ready(state))
        val history = DraftEngine.history(state, "p0")
        val own = history.first { it.pick == 1 }
        assertEquals(1, own.kept.size); assertEquals(2, own.burned.size); assertEquals(8, own.cards.size)
        val third = history.first { it.pick == 3 }
        assertEquals(1, third.kept.size); assertEquals(2, third.burned.size); assertEquals(3, third.cards.size)
    }
}
