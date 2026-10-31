package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodPickMarksTest {
    private val pack = DraftPrivatePack("pack", 1, (1..8).map { DraftCopy("$it", "definition") })
    private val settings = DraftSettings(keep = 2, burn = 2)
    private val rules = PodPickRules(pack, settings)
    private val empty = PodPickMarks().forPack(pack)

    // DRF-060
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

    // DRF-060
    @Test fun overflowDropsEarliestOfOnlyThatKind() {
        var marks = empty.toggle("8", PodPickMark.BURN, rules)
        for (id in listOf("3", "1", "2")) marks = marks.toggle(id, PodPickMark.KEEP, rules)
        assertEquals(listOf("1", "2"), marks.keeps)
        assertEquals(listOf("8"), marks.burns)
        for (id in listOf("6", "4")) marks = marks.toggle(id, PodPickMark.BURN, rules)
        assertEquals(listOf("6", "4"), marks.burns)
        assertEquals(listOf("1", "2"), marks.keeps)
    }

    // DRF-060
    @Test fun remarkingAppendsToEndAndSwitchingDropsEarliestAtDestination() {
        val marks = empty.toggle("1", PodPickMark.KEEP, rules).toggle("2", PodPickMark.KEEP, rules)
            .toggle("1", PodPickMark.KEEP, rules).toggle("1", PodPickMark.KEEP, rules)
            .toggle("3", PodPickMark.BURN, rules).toggle("3", PodPickMark.KEEP, rules)
        assertEquals(listOf("1", "3"), marks.keeps)
        assertTrue(marks.burns.isEmpty())
    }

    // DRF-060
    @Test fun boosterIgnoresLeftSwipeEvenOnAKeptCard() {
        val booster = PodPickRules(pack, DraftSettings())
        val keep = empty.toggle("1", PodPickMark.KEEP, booster)
        assertEquals(keep, keep.toggle("1", PodPickMark.BURN, booster))
        assertEquals(empty, empty.toggle("1", PodPickMark.BURN, booster))
    }

    // DRF-067
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

    // DRF-045
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

    // DRF-052
    @Test fun librarianSlotsIncreaseOverflowLimitAndRefundKeepsLatestMarks() {
        val available = listOf("l1", "l2", "l3")
        var marks = empty.spendLibrarian(available, pack, settings).spendLibrarian(available, pack, settings)
        val enlarged = PodPickRules(pack, settings, marks.librarians)
        assertEquals(4, enlarged.requiredKeeps)
        assertEquals(pack.cards.map { it.id } + listOf("l1", "l2"), enlarged.ids)
        for (id in listOf("1", "2", "3", "4", "5")) marks = marks.toggle(id, PodPickMark.KEEP, enlarged)
        assertEquals(listOf("2", "3", "4", "5"), marks.keeps)
        assertEquals(listOf("l1", "l2"), marks.librarians)
        marks = marks.refundLibrarian("l1", pack, settings)
        assertEquals(listOf("3", "4", "5"), marks.keeps)
        assertEquals(listOf("l2"), marks.librarians)
        assertEquals(listOf("l2", "l1"), marks.spendLibrarian(available, pack, settings).librarians)
        assertTrue(marks.forPack(pack.copy(id = "next")).librarians.isEmpty())
    }

    // DRF-053
    @Test fun aSpentLibrarianIsAPackCardThatCanBeKeptBurnedOrLeftAndRefundRemovesItsMark() {
        val s = DraftSettings(keep = 1, burn = 2)
        var marks = empty.spendLibrarian(listOf("l1"), pack, s)
        val rules = PodPickRules(pack, s, marks.librarians)
        assertEquals(2, rules.requiredKeeps)
        assertEquals(2, rules.manualBurns)
        // Burn it with one card and keep two cards.
        marks = marks.toggle("l1", PodPickMark.BURN, rules).toggle("1", PodPickMark.BURN, rules)
            .toggle("2", PodPickMark.KEEP, rules).toggle("3", PodPickMark.KEEP, rules)
        assertTrue(rules.canConfirm(marks, false))
        assertEquals(listOf("l1", "1"), rules.burnIds(marks))
        // Keep it back instead: still a valid pick.
        val back = marks.toggle("l1", PodPickMark.KEEP, rules)
        assertEquals(listOf("3", "l1"), back.keeps)
        assertEquals(listOf("1"), back.burns)
        // Refunding removes it and its mark; the pick then needs one keep fewer.
        val refunded = back.refundLibrarian("l1", pack, s)
        assertEquals(listOf("3"), refunded.keeps)
        assertTrue(PodPickRules(pack, s).canConfirm(refunded.toggle("4", PodPickMark.BURN, PodPickRules(pack, s)), false))
        // Marks for another spend set do not confirm: the rules follow exactly the spent Librarians.
        assertFalse(PodPickRules(pack, s, listOf("l2")).canConfirm(marks.copy(librarians = listOf("l2")), false))
        assertFalse(rules.canConfirm(marks.copy(librarians = emptyList()), false))
    }

    // DRF-053
    @Test fun nearTheEndASpentLibrarianNotKeptBurnsWithTheRest() {
        val small = pack.copy(cards = pack.cards.take(3))
        val s = DraftSettings(keep = 1, burn = 2)
        var marks = empty.spendLibrarian(listOf("l1", "l2"), small, s).spendLibrarian(listOf("l1", "l2"), small, s)
        val rules = PodPickRules(small, s, marks.librarians)
        assertEquals(3, rules.requiredKeeps)
        // A spend never changes whether burns are automatic: 3 cards <= keep 1 + burn 2.
        assertTrue(rules.automaticBurns)
        assertEquals(PodPickRules(small, s).automaticBurns, rules.automaticBurns)
        // Keep two cards and l2 back: the third card and l1 burn automatically.
        marks = marks.toggle("1", PodPickMark.KEEP, rules).toggle("2", PodPickMark.KEEP, rules).toggle("l2", PodPickMark.KEEP, rules)
        assertEquals(listOf("3", "l1"), rules.burnIds(marks))
        assertTrue(rules.canConfirm(marks, false))
        // Spending is no longer limited by the pack size; only by the Librarians owned.
        marks = marks.spendLibrarian(listOf("l1", "l2", "l3"), small, s)
        assertEquals(listOf("l1", "l2", "l3"), marks.librarians)
        assertEquals(marks, marks.spendLibrarian(listOf("l1", "l2", "l3"), small, s))
    }

    // DRF-052
    @Test fun shownPackAppendsSpentLibrariansInSpendOrder() {
        val owned = listOf(DraftCopy("l1", "librarian", librarian = true), DraftCopy("l2", "librarian", librarian = true))
        assertEquals(pack, pack.withSpentLibrarians(emptyList(), owned))
        assertEquals(pack.cards + owned.reversed(), pack.withSpentLibrarians(listOf("l2", "l1"), owned).cards)
    }
}
