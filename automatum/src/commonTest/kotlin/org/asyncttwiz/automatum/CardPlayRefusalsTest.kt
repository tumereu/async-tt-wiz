package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.state.ControlSince
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TBL-123, LOB-090: a greyed menu row says the engine's own reason, never the catch-all, for what the prod faults of
 * 2026-11-22–26 showed it for (2VLVQG, NNJWE3, S2AJML: a creature's {T} ability; 8M35M6, WCT9GA, HMZSXN: every mana source
 * tapped; T3CN7C: a face-up permanent's disguise; TP6NRM: a flashback card that can't be cast now had no row at all).
 */
class CardPlayRefusalsTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private val druid = Cards.invent("Plays Druid", "{1}{G}", "Creature — Elf Druid", "{T}: Add {G}.", "2/2")
    private val spinner = Cards.invent("Plays Spinner", "{3}{G}", "Creature — Spider",
        "{2}{G}: Put a +1/+1 counter on target creature you control. Activate only as a sorcery.", "2/4")
    private val snare = Cards.invent("Plays Snare", "{2}", "Artifact", "{T}: Tap target creature an opponent controls.")
    private val blade = Cards.invent("Plays Blade", "{1}{R}", "Artifact — Equipment",
        "Equipped creature gets +3/+0.\nDisguise {2}{R}\nEquip {1}{R}")
    private val days = Cards.invent("Plays Days", "{2}{B}{B}", "Sorcery",
        "Create two tapped 2/2 black Horror creature tokens.\nFlashback {4}{B}{B}")

    // TBL-123 (CR 107.5): a tapped creature's {T}: Add {G} says it is tapped
    @Test fun aTappedPermanentIsTapped() {
        val (card) = table.put(alice, Zone.Battlefield, druid, tapped = true)
        assertEquals(CardPlay.Refusal.Tapped, table.plays().of(card).single().refusal)
    }

    // TBL-123 (CR 302.6): a creature cast this turn says summoning sickness
    @Test fun aCreatureCastThisTurnIsSummoningSick() {
        val (card) = table.put(alice, Zone.Battlefield, druid)
        table.update(card) { it.copy(controlSince = ControlSince(PlayerId(alice), table.turn)) }
        assertEquals(CardPlay.Refusal.SummoningSick, table.plays().of(card).single().refusal)
    }

    // TBL-123 (CR 601.2g, 601.2h): with every land tapped, "Not enough mana"; with the mana, the row is usable
    @Test fun withoutTheManaItIsNotEnoughMana() {
        val (card) = table.put(alice, Zone.Battlefield, spinner)
        val (a, b, c) = table.put(alice, Zone.Battlefield, "Forest", "Forest", "Forest", tapped = true)
        assertEquals(CardPlay.Refusal.Mana, table.plays().of(card).single().refusal)
        listOf(a, b, c).forEach { id -> table.update(id) { it.copy(tapped = false) } }
        val usable = table.plays().of(card).single()
        assertNull(usable.refusal)
        assertEquals(true, usable.legal)
    }

    // TBL-123 (CR 601.2c): without a creature of the other player's to tap, "Nothing it can target now"
    @Test fun withoutATargetThereIsNothingToTarget() {
        val (card) = table.put(alice, Zone.Battlefield, snare)
        assertEquals(CardPlay.Refusal.NoTarget, table.plays().of(card).single().refusal)
    }

    // TBL-123 (CR 702.168d): a face-up Equipment's "Turn face up" says it is face up already
    @Test fun aFaceUpPermanentIsFaceUpAlready() {
        val (card) = table.put(alice, Zone.Battlefield, blade)
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain")
        val turn = table.plays().of(card).single { it.kind == CardPlay.Kind.Special }
        assertEquals(CardPlay.Refusal.FaceUp, turn.refusal)
    }

    // ZON-107, TBL-118: a flashback card in the graveyard that can't be cast now has its greyed Cast, with the engine's
    // reason, as a hand card has; with the mana its flashback is offered instead
    @Test fun aFlashbackCardThatCantBeCastNowIsGreyed() {
        val (card) = table.put(alice, Zone.Graveyard, days)
        val greyed = table.plays().of(card).single()
        assertEquals(CardPlay.Kind.Cast, greyed.kind)
        assertNull(greyed.input)
        assertEquals(CardPlay.Refusal.Other(), greyed.refusal)
        table.put(alice, Zone.Battlefield, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        val offered = table.plays().of(card)
        assertEquals(true, offered.isNotEmpty() && offered.all { it.legal && it.refusal == null }, "$offered")
    }
}
