package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.math.max
import kotlin.math.min
import kotlin.test.*

/** Card ratings in the draft history dialogs (owner, 2026-11-25). */
class PodHistoryRatingsTest {
    private val account = AccountIdentity("alice", "Alice")
    private val rated = PodSource(kind = PodSourceKind.SET, setCode = "TST", packSets = listOf("TST"), botRatings = true)
    private fun detail(source: PodSource, phase: PodPhase, wait: PodDraftWait? = null, mode: DraftMode = DraftMode.BOOSTER) =
        PodDetail("pod", "Pod", phase, account, DraftSettings.defaults(mode), source, DraftSupply(0, Long.MAX_VALUE),
            listOf(PodMember(account)), emptyList(), updatedAt = "", draftWait = wait)
    private fun card(name: String) = CardDefinition(name, listOf(DefinitionFace("face", name)))

    /** Twenty cards rated 40.1% … 42.0%: "Card 20" the best. */
    private val ratings = PodHistoryRatings(DraftBotRatings("TST", (1..20).associate { "Card $it" to 400 + it }))

    // DRF-315 (owner, 2026-11-25): ratings show once your own draft is done (DRF-119), in a pod of one set with ratings (the
    // bots' rule, DRF-312), in every draft mode's history dialog; before that and elsewhere none
    @Test fun ratingsShowOnceYourOwnDraftIsDoneInAPodOfOneRatedSet() {
        for (mode in listOf(DraftMode.BOOSTER, DraftMode.GLIMPSE, DraftMode.CUSTOM_BOOSTER, DraftMode.ARENA, DraftMode.WINSTON,
            DraftMode.WINCHESTER, DraftMode.GRID, DraftMode.ROCHESTER, DraftMode.ROTISSERIE)) {
            assertEquals("TST", podHistoryRatingsSet(detail(rated, PodPhase.DRAFTED, mode = mode)), "$mode after the draft")
            assertEquals("TST", podHistoryRatingsSet(detail(rated, PodPhase.DRAFTING, mode = mode)), "$mode: own draft done, others drafting")
            assertNull(podHistoryRatingsSet(detail(rated, PodPhase.DRAFTING, PodDraftWait.Picks, mode)), "$mode while drafting")
        }
        assertEquals("your own draft isn't done yet", podHistoryNoRatingsReason(detail(rated, PodPhase.DRAFTING, PodDraftWait.Picks)))
        assertNull(podHistoryNoRatingsReason(detail(rated, PodPhase.DRAFTED)))
        // A cube, mixed sets and a set without ratings have none
        val none = listOf(PodSource("Cube", 540), rated.copy(packSets = listOf("TST", "TS2")), rated.copy(botRatings = false))
        for (source in none) {
            assertNull(podHistoryRatingsSet(detail(source, PodPhase.DRAFTED)), "$source")
            assertEquals("the pod isn't of one set with card ratings", podHistoryNoRatingsReason(detail(source, PodPhase.DRAFTED)))
        }
    }

    // DRF-315: the chip reads the card's GIH WR with one decimal; a card without a rating has none
    @Test fun theChipReadsTheWinRate() {
        assertEquals("58.4%", podRatingText(584))
        assertEquals("60.0%", podRatingText(600))
        assertEquals(420, ratings.rating(card("Card 20")))
        assertNull(ratings.rating(card("Unrated")))
        assertNull(ratings.rating(null))
    }

    // DRF-317 (owner, 2026-11-25): the citation 17Lands asks for, in sight wherever its numbers show
    @Test fun theCitationNames17Lands() = assertEquals("Ratings: 17Lands", POD_RATINGS_CITATION)

    // DRF-315: five steps by rank in the set: the top 10 %, the next 15 %, the middle, the next 15 % and the bottom 10 %
    @Test fun theStepIsTheRankInTheSet() {
        val steps = (20 downTo 1).map { ratings.step(400 + it) }
        assertEquals(List(2) { PodRatingStep.Top } + List(3) { PodRatingStep.High } + List(10) { PodRatingStep.Middle } +
            List(3) { PodRatingStep.Low } + List(2) { PodRatingStep.Bottom }, steps)
        // Equal ratings share a step: by how many rate higher
        val ties = PodHistoryRatings(DraftBotRatings("TST", (1..10).associate { "Card $it" to if (it <= 5) 600 else 500 }))
        assertEquals(PodRatingStep.Top, ties.step(600))
        assertEquals(PodRatingStep.Middle, ties.step(500))
    }

    // DRF-316: By rating sorts best first; unrated cards and card backs follow in their own order
    @Test fun byRatingSortsBestFirst() {
        val definitions = mapOf("d5" to card("Card 5"), "d19" to card("Card 19"), "dx" to card("Unrated"), "d2" to card("Card 2"),
            "dy" to card("Also unrated"))
        val copies = listOf("d5", "dx", "d19", "dy", "d2").map { DraftCopy("c-$it", it) }
        assertEquals(listOf("c-d19", "c-d5", "c-d2", "c-dx", "c-dy"), podHistoryRatingOrder(copies, definitions, ratings).map { it.id })
        assertEquals(copies, podHistoryRatingOrder(copies, definitions, null))
        val picks = listOf(null, copies[0], copies[1], null, copies[2])
        assertEquals(listOf("c-d19", "c-d5", null, "c-dx", null), podPicksRatingOrder(picks, definitions, ratings).map { it?.id })
        assertEquals(picks, podPicksRatingOrder(picks, definitions, null))
    }

    // DRF-315 (owner, 2026-11-25; colours owner 2026-11-26): the chips' five colours from the top down are gold, violet, bronze,
    // grey and dark grey, never green or red (the keeps' and burns' colours)
    @Test fun theChipColoursGoFromGoldToDarkGreyNeverGreenOrRed() {
        val steps = listOf(PodRatingStep.Top, PodRatingStep.High, PodRatingStep.Middle, PodRatingStep.Low, PodRatingStep.Bottom)
        assertEquals(listOf(0xFFE2B53E, 0xFFB79AE0, 0xFFC4996B, 0xFF8E9297, 0xFF55595E).map { androidx.compose.ui.graphics.Color(it) },
            steps.map(PodRatingColours::fill), "gold, violet, bronze, grey, dark grey")
        for (step in steps) {
            val color = PodRatingColours.fill(step)
            val r = color.red; val g = color.green; val b = color.blue
            val high = max(r, max(g, b)); val low = min(r, min(g, b))
            val saturation = if (high == 0f) 0f else (high - low) / high
            if (saturation < .25f) continue // a grey
            val hue = when (high) {
                r -> 60 * (((g - b) / (high - low)) % 6)
                g -> 60 * ((b - r) / (high - low) + 2)
                else -> 60 * ((r - g) / (high - low) + 4)
            }.let { if (it < 0) it + 360 else it }
            assertFalse(hue < 20 || hue > 340, "$step is red: hue $hue")
            assertFalse(hue in 75f..165f, "$step is green: hue $hue")
        }
        // Dark text on the three light steps, white on the greys
        assertEquals(listOf(false, false, false, true, true), steps.map { PodRatingColours.text(it) == androidx.compose.ui.graphics.Color.White })
    }
}
