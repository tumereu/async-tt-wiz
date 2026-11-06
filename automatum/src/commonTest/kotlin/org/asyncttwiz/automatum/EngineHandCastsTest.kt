package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * I-D50 on a game's own engine state: a raised hand card's cost chips ([HandCasts]): the ways to cast it (the regular
 * cast first) and its extras, each with its mana and whether it is paid now, and the sources Autotap would tap.
 */
class EngineHandCastsTest {
    private val table = EngineTable()
    private val alice get() = table.alice

    private fun options(card: String, way: Int = 0, times: List<Int> = emptyList()): HandCastOptions? {
        val state = table.state()
        val plays = HandPlays.of(state, alice, null).of(card)
        return HandCasts.options(table.source(state), table.definitions, alice, card, plays, way, times,
            CastFlow.Seeds(table::seed))
    }

    // ZON-080, ZON-082: the regular cast with its mana, chosen; the kicker an extra, unaffordable with two Mountains ({4}{R}); the
    // sources Autotap would tap follow the chosen way
    @Test fun regularCastAndAnUnaffordableKicker() {
        val mountains = table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val chips = assertNotNull(options(surge))
        assertEquals(listOf("Cast"), chips.ways.map { it.name })
        assertEquals("{R}", chips.chosen.cost)
        assertTrue(chips.chosen.affordable)
        val kicker = chips.extras.single()
        assertEquals("Kicker", kicker.name)
        assertEquals("{4}", kicker.cost)
        assertFalse(kicker.affordable)
        assertEquals(1, chips.sources.size)
        assertTrue(chips.sources.single() in mountains)
    }

    // ZON-082: with the kicker chosen the sources follow the extras (five Mountains for {4}{R}); the way keeps its own cost
    @Test fun sourcesFollowTheChosenExtras() {
        val mountains = table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val kicked = assertNotNull(options(surge, times = listOf(1)))
        assertEquals(listOf(1), kicked.times)
        assertEquals(mountains.toSet(), kicked.sources)
        assertEquals("{R}", kicked.chosen.cost)
    }

    // ZON-082, STK-051: a cast begun with the chosen kicker never asks it: the cast asks only for its target, then pays
    @Test fun aCastWithTheChosenKickerAsksOnlyTheTarget() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val state = table.state()
        val begin = HandPlays.of(state, alice, null).of(surge).first()
        val flow = CastFlow.start(table.source(state), alice, begin, CastFlow.Seeds(table::seed), extras = listOf(1))
        assertIs<Decision.Targets>(flow.pending)
        assertTrue(flow.steps.isEmpty())
    }

    // ZON-080: an alternative cost is a way of its own (Evoke) with its own mana; the regular cast is greyed when unpaid
    @Test fun evokeIsAWay() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (vivacity) = table.put(alice, Zone.Hand, "Vivacity")
        val chips = assertNotNull(options(vivacity))
        assertEquals(listOf("Cast", "Evoke"), chips.ways.map { it.name })
        assertFalse(chips.ways[0].affordable)
        assertTrue(chips.ways[1].affordable)
        assertEquals("{R/G}{R/G}", chips.ways[1].cost)
        assertIs<Input.Cast>(assertNotNull(options(vivacity, way = 1)).chosen.input)
    }

    // ZON-080: a land's only way is Play, without a cost; a card the player can't pay for now keeps its chips, greyed
    @Test fun aLandPlaysAndAnUnpaidCastIsGreyed() {
        val (mountain) = table.put(alice, Zone.Hand, "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        assertEquals(listOf("Play"), assertNotNull(options(mountain)).ways.map { it.name })
        val chips = assertNotNull(options(surge))
        assertEquals(listOf("Cast"), chips.ways.map { it.name })
        assertFalse(chips.affordable)
        assertTrue(chips.sources.isEmpty())
    }

    // STK-023 (owner playtest 2026-11-05 evening): X can't be set past what the player can pay: with four Mountains
    // Doomfire ({X}{R}) goes up to X = 3; with none even X = 0 can't be paid (one less than its least)
    @Test fun theMostXIsWhatTheManaPays() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain", "Mountain")
        val (doomfire) = table.put(alice, Zone.Hand, "Doomfire")
        val state = table.state()
        val flow = CastFlow.start(table.source(state), alice, HandPlays.of(state, alice, null).of(doomfire).first(), CastFlow.Seeds(table::seed))
        val open = assertIs<Decision.Announce>(flow.pending)
        assertEquals(0, open.min)
        assertEquals(3, HandCasts.mostAnnounced(flow))
    }

    // STK-023: X counted twice ({X}{X}{G}): five Mountains and a Forest pay up to X = 2
    @Test fun aDoubleXCountsTwice() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Forest")
        val (outbreak) = table.put(alice, Zone.Hand, "Vermin Outbreak")
        val state = table.state()
        val flow = CastFlow.start(table.source(state), alice, HandPlays.of(state, alice, null).of(outbreak).first(), CastFlow.Seeds(table::seed))
        assertIs<Decision.Announce>(flow.pending)
        assertEquals(2, HandCasts.mostAnnounced(flow))
    }

    // ZON-080 (owner playtest 2026-11-05 evening, fix): a bestow creature whose bestow needs collect evidence it can't
    // collect showed no Bestow chip at all: every way the card has is a chip, the engine only greys it
    @Test fun aBestowNeedingEvidenceStillShowsGreyed() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain")
        val (firebird) = table.put(alice, Zone.Hand, "Inspector's Firebird")
        val chips = assertNotNull(options(firebird))
        assertEquals(listOf("Cast", "Bestow"), chips.ways.map { it.name })
        assertTrue(chips.ways[0].affordable)
        assertFalse(chips.ways[1].affordable)
        assertEquals("{R}", chips.ways[1].cost)
    }

    // ZON-080 (owner playtest 2026-11-05 evening): the card's special actions from the hand are ways of their own, after its
    // casts: Plot with its cost; with two Mountains the plain cast ({3}{R}) is greyed and Plot ({1}{R}) can be paid
    @Test fun plotIsAWayFromTheHand() {
        val schemer = arcanum.engine.cards.Cards.invent("Ember Schemer", "{3}{R}", "Sorcery", "Ember Schemer deals 2 damage to any target.\nPlot {1}{R}")
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (card) = table.put(alice, Zone.Hand, schemer)
        val chips = assertNotNull(options(card))
        assertEquals(listOf("Cast", "Plot"), chips.ways.map { it.name })
        assertFalse(chips.ways[0].affordable)
        assertTrue(chips.ways[1].affordable)
        assertEquals("{1}{R}", chips.ways[1].cost)
        assertIs<Input.Activate>(chips.ways[1].input)
    }

    // ZON-080: foretell (CR 702.143b) is a way from the hand during its owner's turn ({2}), greyed in the other player's
    @Test fun foretellIsAWayInYourTurn() {
        val omen = arcanum.engine.cards.Cards.invent("Ember Omen", "{3}{R}", "Instant", "Ember Omen deals 3 damage to any target.\nForetell {1}{R}")
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (card) = table.put(alice, Zone.Hand, omen)
        val chips = assertNotNull(options(card))
        assertEquals(listOf("Cast", "Foretell"), chips.ways.map { it.name })
        assertTrue(chips.ways[1].affordable)
        assertEquals("{2}", chips.ways[1].cost)
        table.active = table.bob; table.priority = table.bob
        val theirs = assertNotNull(options(card))
        assertEquals(listOf("Cast", "Foretell"), theirs.ways.map { it.name })
        assertFalse(theirs.ways[1].affordable)
    }

    // ZON-080: dash (an alternative cost) is a way, greyed when its mana can't be paid
    @Test fun dashShowsGreyedWhenUnpaid() {
        val raider = arcanum.engine.cards.Cards.invent("Ember Raider", "{R}", "Creature — Human Warrior", "Dash {3}{R}", "2/1")
        table.put(alice, Zone.Battlefield, "Mountain")
        val (card) = table.put(alice, Zone.Hand, raider)
        val chips = assertNotNull(options(card))
        assertEquals(listOf("Cast", "Dash"), chips.ways.map { it.name })
        assertTrue(chips.ways[0].affordable)
        assertFalse(chips.ways[1].affordable)
        assertEquals("{3}{R}", chips.ways[1].cost)
    }

    // ZON-080 (batch B 2026-11-06): an ability used from the hand is a chip after the casts: Cycling with its cost, paid
    // with one Mountain while the cast ({3}{R}) is greyed; with no Mountain it is greyed too
    @Test fun cyclingIsAChipFromTheHand() {
        val musing = arcanum.engine.cards.Cards.invent("Ember Musing", "{3}{R}", "Sorcery", "Draw two cards.\nCycling {1}")
        val (card) = table.put(alice, Zone.Hand, musing)
        val none = assertNotNull(options(card))
        assertEquals(listOf("Cast", "Cycling"), none.ways.map { it.name })
        assertFalse(none.ways[1].affordable)
        assertEquals("{1}", none.ways[1].cost)
        table.put(alice, Zone.Battlefield, "Mountain")
        val chips = assertNotNull(options(card))
        assertFalse(chips.ways[0].affordable)
        assertTrue(chips.ways[1].affordable)
        assertIs<Input.Activate>(chips.ways[1].input)
    }

    // ZON-080: channel (an ability word over "{1}, Discard this card: …") is a chip named Channel
    @Test fun channelIsAChipFromTheHand() {
        val sprout = arcanum.engine.cards.Cards.invent("Ember Sprout", "{2}{R}", "Creature — Elemental",
            "Channel — {1}, Discard this card: Draw a card.", "2/2")
        table.put(alice, Zone.Battlefield, "Mountain")
        val (card) = table.put(alice, Zone.Hand, sprout)
        val chips = assertNotNull(options(card))
        assertEquals(listOf("Cast", "Channel"), chips.ways.map { it.name })
        assertTrue(chips.ways[1].affordable)
    }

    // ZON-080: a land with cycling: Play, then Cycling; in the other player's turn Play is greyed and Cycling (any time)
    // can still be paid
    @Test fun aCyclingLandPlaysOrCycles() {
        val flats = arcanum.engine.cards.Cards.invent("Ash Flats", null, "Land", "{T}: Add {R}.\nCycling {2}")
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (card) = table.put(alice, Zone.Hand, flats)
        val chips = assertNotNull(options(card))
        assertEquals(listOf("Play", "Cycling"), chips.ways.map { it.name })
        assertTrue(chips.ways.all { it.affordable })
        table.active = table.bob; table.priority = table.alice
        val later = assertNotNull(options(card))
        assertEquals(listOf("Play", "Cycling"), later.ways.map { it.name })
        assertFalse(later.ways[0].affordable)
        assertTrue(later.ways[1].affordable)
    }

    // ZON-080 (batch B 2026-11-06): a split card's fused halves show as a chip even while they can't be cast (greyed)
    @Test fun fusedHalvesShowGreyed() {
        val fuse = "Fuse (You may cast one or both halves of this card from your hand.)"
        val split = arcanum.engine.cards.Cards.inventFaces("Split",
            arcanum.parser.OracleCard("Spark", "{R}", "Instant", "Spark deals 1 damage to any target.\n$fuse"),
            arcanum.parser.OracleCard("Ember", "{2}{R}", "Instant", "Ember deals 2 damage to any target.\n$fuse"))
        table.put(alice, Zone.Battlefield, "Mountain")
        val (card) = table.put(alice, Zone.Hand, split)
        val chips = assertNotNull(options(card))
        assertEquals(listOf("Cast", "Ember", "Spark // Ember (fused)"), chips.ways.map { it.name })
        assertTrue(chips.ways[0].affordable)
        assertFalse(chips.ways[1].affordable)
        assertFalse(chips.ways[2].affordable)
    }
}
