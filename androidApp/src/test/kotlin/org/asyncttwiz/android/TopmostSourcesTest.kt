package org.asyncttwiz.android

import arcanum.api.Color
import arcanum.engine.decision.ManaUse
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.view.AbilityOrigin
import org.junit.Test
import kotlin.test.assertEquals

/** Autotap's sources in a stack of identical permanents: the topmost members (TBL-130, STK-024, ZON-079). */
class TopmostSourcesTest {
    private fun obj(id: String) = ObjectId.Card(CardId(id), 0)
    private val tap = AbilityOrigin.Printed(0, 0)
    private fun red(id: String) = ManaUse.Ability(obj(id), tap, Color.Red)
    private val mountains = BattlefieldGroup("stack:m1", "a", BattlefieldRow.Lands, listOf("m1", "m2", "m3", "m4"), BattlefieldGroup.Kind.Stack)
    private val card: (ObjectId) -> String? = { (it as? ObjectId.Card)?.card?.value }

    // TBL-130, STK-024 (owner playtest 2026-11-07): the engine's suggestion takes the first Mountains; the table makes it
    // the topmost ones able to make the same mana, a source not offered (tapped) passed over; other uses stay as they are
    @Test fun autotapTakesTheTopmostMembersOfAStack() {
        val options = listOf(red("m1"), red("m2"), red("m3"), red("m4"), red("lone"), ManaUse.Pool(0))
        assertEquals(listOf(red("m4"), red("m3"), red("lone"), ManaUse.Pool(0)),
            TopmostSources.of(listOf(red("m1"), red("m2"), red("lone"), ManaUse.Pool(0)), options, listOf(mountains), card))
        val untappedOnly = listOf(red("m1"), red("m2"), red("m3"))
        assertEquals(listOf(red("m3")), TopmostSources.of(listOf(red("m1")), untappedOnly, listOf(mountains), card),
            "the top one isn't offered (tapped): the next one down")
        assertEquals(listOf(red("m2")), TopmostSources.of(listOf(red("m2")), options, emptyList(), card), "no stacks: unchanged")
    }

    // ZON-079, TBL-130: the outlined sources of a stack are its topmost untapped members, as many as Autotap takes
    @Test fun theOutlinedSourcesAreTheTopmostUntapped() {
        assertEquals(setOf("m3", "m2", "lone"),
            TopmostSources.cards(setOf("m1", "m2", "lone"), listOf(mountains)) { it == "m4" })
        assertEquals(setOf("m4"), TopmostSources.cards(setOf("m1"), listOf(mountains)) { false })
    }
}
