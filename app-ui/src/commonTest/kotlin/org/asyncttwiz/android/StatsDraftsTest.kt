package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import org.asyncttwiz.protocol.*

/** Stats' Drafts tab (owner, 2026-11-26): the kept drafts listed and opened. */
class StatsDraftsTest {
    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private val carol = AccountIdentity("carol", "Carol")
    private val set = PodSource(kind = PodSourceKind.SET, setCode = "TST", setName = "Test Set", boosterKind = SetBoosterKind.RECIPE_BASED,
        packSets = listOf("TST"), botRatings = true)
    private fun draft(id: String, vararg players: AccountIdentity, settings: DraftSettings = DraftSettings(), source: PodSource = set) =
        DraftRecordSummary(id, "pod-$id", "Pod $id", source, settings, "2026-11-26T10:00:00Z", players.toList())

    // LOB-280: with an opponent chosen only the drafts they were in, in their order; with All every one
    @Test fun anOpponentChipKeepsTheDraftsTheyWereIn() {
        val drafts = listOf(draft("1", alice, bob), draft("2", alice, PodBots.identity(1)), draft("3", carol, alice, bob))
        assertEquals(listOf("1", "2", "3"), statsDrafts(drafts, null).map { it.id })
        assertEquals(listOf("1", "3"), statsDrafts(drafts, "bob").map { it.id })
        assertEquals(listOf("3"), statsDrafts(drafts, "carol").map { it.id })
        assertEquals(emptyList(), statsDrafts(drafts, "dave"))
    }

    // LOB-280: a row's second line names the set, then the date (the set's code without a name)
    @Test fun aRowNamesItsSetAndDate() {
        assertEquals("Test Set · ${draftDate(draft("1", alice))}", statsDraftLine(draft("1", alice)))
        assertEquals("TST", statsDraftLine(draft("1", alice).copy(finishedAt = "not a date", source = set.copy(setName = null))))
    }

    // LOB-281, DRF-212: a kept draft's history has its pod's cards per row range: 15 for a set with a booster recipe, else the
    // pack size (Arena: the offer's)
    @Test fun aKeptDraftsHistoryTakesItsPodsPackSize() {
        assertEquals(15, podHistoryPackSize(DraftSettings(cardsPerPack = 5), set))
        assertEquals(5, podHistoryPackSize(DraftSettings(cardsPerPack = 5), set.copy(boosterKind = SetBoosterKind.APPROXIMATE)))
        assertEquals(DraftSettings.arena().cardsPerPack, podHistoryPackSize(DraftSettings.arena(), set))
    }
}
