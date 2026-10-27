package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodPickMarksTest {
    private val pack = DraftPrivatePack("pack", 1, (1..8).map { DraftCopy("$it", "definition") })
    private val settings = DraftSettings(keep = 2, burn = 2)
    private val rules = PodPickRules(pack, settings)
    private val empty = PodPickMarks().forPack(pack)

    @Test fun sameDirectionUnmarksAndOppositeDirectionSwitches() {
        val keep = empty.toggle("1", PodPickMark.KEEP, rules)
        assertEquals(listOf("1"), keep.keeps)
        assertEquals(empty, keep.toggle("1", PodPickMark.KEEP, rules))
        val burn = keep.toggle("1", PodPickMark.BURN, rules)
        assertTrue(burn.keeps.isEmpty())
        assertEquals(listOf("1"), burn.burns)
        assertEquals(empty, burn.toggle("1", PodPickMark.BURN, rules))
        assertEquals(keep, burn.toggle("1", PodPickMark.KEEP, rules))
        assertEquals(empty, burn.unmark("1"))
    }

    @Test fun overflowDropsEarliestOfOnlyThatKind() {
        var marks = empty.toggle("8", PodPickMark.BURN, rules)
        for (id in listOf("3", "1", "2")) marks = marks.toggle(id, PodPickMark.KEEP, rules)
        assertEquals(listOf("1", "2"), marks.keeps)
        assertEquals(listOf("8"), marks.burns)
        for (id in listOf("6", "4")) marks = marks.toggle(id, PodPickMark.BURN, rules)
        assertEquals(listOf("6", "4"), marks.burns)
        assertEquals(listOf("1", "2"), marks.keeps)
    }

    @Test fun remarkingAppendsToEndAndSwitchingDropsEarliestAtDestination() {
        val marks = empty.toggle("1", PodPickMark.KEEP, rules).toggle("2", PodPickMark.KEEP, rules)
            .toggle("1", PodPickMark.KEEP, rules).toggle("1", PodPickMark.KEEP, rules)
            .toggle("3", PodPickMark.BURN, rules).toggle("3", PodPickMark.KEEP, rules)
        assertEquals(listOf("1", "3"), marks.keeps)
        assertTrue(marks.burns.isEmpty())
    }

    @Test fun boosterIgnoresLeftSwipeEvenOnAKeptCard() {
        val booster = PodPickRules(pack, DraftSettings())
        val keep = empty.toggle("1", PodPickMark.KEEP, booster)
        assertEquals(keep, keep.toggle("1", PodPickMark.BURN, booster))
        assertEquals(empty, empty.toggle("1", PodPickMark.BURN, booster))
    }

    @Test fun ordinaryPickRequiresExactCountsAndNotBusy() {
        var marks = empty
        assertFalse(rules.canConfirm(marks, false))
        for (id in listOf("1", "2")) marks = marks.toggle(id, PodPickMark.KEEP, rules)
        assertFalse(rules.canConfirm(marks, false))
        for (id in listOf("3", "4")) marks = marks.toggle(id, PodPickMark.BURN, rules)
        assertTrue(rules.canConfirm(marks, false))
        assertFalse(rules.canConfirm(marks, true))
        assertFalse(rules.canConfirm(marks.unmark("1"), false))
        assertFalse(rules.canConfirm(marks.unmark("4"), false))
    }

    @Test fun nearEndAndExactBoundaryOnlyRequireKeepsAndExplicitlyBurnRest() {
        for (size in listOf(3, 4)) {
            val end = pack.copy(cards = pack.cards.take(size))
            val endRules = PodPickRules(end, settings)
            assertTrue(endRules.automaticBurns)
            assertEquals(0, endRules.manualBurns)
            assertEquals(emptyList(), endRules.burnIds(empty))
            assertEquals(empty, empty.toggle("3", PodPickMark.BURN, endRules))
            val partial = empty.toggle("2", PodPickMark.KEEP, endRules)
            assertEquals(emptyList(), endRules.burnIds(partial))
            val marks = partial.toggle("1", PodPickMark.KEEP, endRules)
            assertTrue(endRules.canConfirm(marks, false))
            assertEquals(end.cards.drop(2).map { it.id }, endRules.burnIds(marks))
            assertFalse(endRules.canConfirm(marks.unmark("1"), false))
            assertEquals(emptyList(), endRules.burnIds(marks.unmark("1")))
            assertEquals(end.cards.filterNot { it.id in listOf("1", "3") }.map { it.id },
                endRules.burnIds(marks.toggle("3", PodPickMark.KEEP, endRules)))
        }
        assertFalse(PodPickRules(pack.copy(cards = pack.cards.take(5)), settings).automaticBurns)
    }

    @Test fun fewerCardsThanKeepAndEmptyPackAreHandled() {
        val endRules = PodPickRules(pack.copy(cards = pack.cards.take(1)), settings)
        assertEquals(1, endRules.requiredKeeps)
        assertEquals(0, endRules.requiredBurns)
        val marks = empty.toggle("1", PodPickMark.KEEP, endRules)
        assertTrue(endRules.canConfirm(marks, false))
        assertEquals(emptyList(), endRules.burnIds(marks))
        assertFalse(PodPickRules(pack.copy(cards = emptyList()), settings).canConfirm(empty, false))
    }

    @Test fun packIdChangeResetsButSamePackDataRefreshDoesNot() {
        val marks = empty.toggle("1", PodPickMark.KEEP, rules).toggle("2", PodPickMark.BURN, rules)
        assertEquals(marks, marks.forPack(pack.copy(number = 2)))
        assertEquals(PodPickMarks("next"), marks.forPack(pack.copy(id = "next")))
        assertEquals(PodPickMarks(), marks.forPack(null))
    }

    @Test fun invalidCopiesDuplicatesAndOverlappingMarksCannotConfirm() {
        assertEquals(empty, empty.toggle("unknown", PodPickMark.KEEP, rules))
        assertFalse(rules.canConfirm(empty.copy(keeps = listOf("1", "1"), burns = listOf("3", "4")), false))
        assertFalse(rules.canConfirm(empty.copy(keeps = listOf("1", "2"), burns = listOf("2", "3")), false))
        assertFalse(rules.canConfirm(empty.copy(keeps = listOf("1", "2"), burns = listOf("3", "unknown")), false))
    }

    @Test fun librarianSlotsIncreaseOverflowLimitAndRefundKeepsLatestMarks() {
        val available = listOf("l1", "l2", "l3")
        var marks = empty.spendLibrarian(available, pack, settings).spendLibrarian(available, pack, settings)
        val enlarged = PodPickRules(pack, settings, marks.librarians.size)
        assertEquals(4, enlarged.requiredKeeps)
        for (id in listOf("1", "2", "3", "4", "5")) marks = marks.toggle(id, PodPickMark.KEEP, enlarged)
        assertEquals(listOf("2", "3", "4", "5"), marks.keeps)
        assertEquals(listOf("l1", "l2"), marks.librarians)
        marks = marks.refundLibrarian("l1", pack, settings)
        assertEquals(listOf("3", "4", "5"), marks.keeps)
        assertEquals(listOf("l2"), marks.librarians)
        assertEquals(listOf("l2", "l1"), marks.spendLibrarian(available, pack, settings).librarians)
        assertTrue(marks.forPack(pack.copy(id = "next")).librarians.isEmpty())
    }

    @Test fun librarianKeepsShrinkBurnsFirstAndCannotExceedPackOrOwnership() {
        val small = pack.copy(cards = pack.cards.take(4))
        val s = DraftSettings(keep = 1, burn = 2)
        var marks = empty.spendLibrarian(listOf("l1", "l2", "l3", "l4"), small, s)
        assertEquals(2, PodPickRules(small, s, 1).requiredBurns)
        marks = marks.spendLibrarian(listOf("l1", "l2", "l3", "l4"), small, s)
        val rules = PodPickRules(small, s, 2)
        assertEquals(1, rules.requiredBurns)
        for (id in listOf("1", "2", "3")) marks = marks.toggle(id, PodPickMark.KEEP, rules)
        assertEquals(listOf("4"), rules.burnIds(marks))
        assertTrue(rules.canConfirm(marks, false))
        assertEquals(marks, marks.spendLibrarian(listOf("l1", "l2"), small, s))
        marks = marks.spendLibrarian(listOf("l1", "l2", "l3", "l4"), small, s)
        assertEquals(0, PodPickRules(small, s, 3).requiredBurns)
        assertEquals(marks, marks.spendLibrarian(listOf("l1", "l2", "l3", "l4"), small, s))
    }
}
