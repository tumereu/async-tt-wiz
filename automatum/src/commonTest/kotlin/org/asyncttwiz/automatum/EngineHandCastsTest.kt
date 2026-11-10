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

    /** "Creature spells you cast with mana value [least] or greater cost {2} less to cast", as an invented card named [name]. */
    private fun drover(name: String, least: Int): String =
        drover(name, arcanum.api.ObjectFilter.ManaValue(arcanum.api.Comparison.AtLeast, arcanum.api.Value.Fixed(least)))

    /** "Creature spells you cast cost {2} less to cast" for spells matching [filter], as an invented card named [name]. */
    private fun drover(name: String, filter: arcanum.api.ObjectFilter): String {
        val cards = arcanum.engine.cards.Cards
        val face = cards.definition(cards.invent("$name Base", "{0}", "Enchantment", "Creature spells you cast cost {2} less to cast.")).faces.single()
        val static = face.abilities.single() as arcanum.api.Ability.Static
        val m = static.mechanics.single() as arcanum.api.Mechanic.CostChange
        return cards.define(name, arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(face.copy(name = name,
            abilities = listOf(static.copy(mechanics = listOf(m.copy(what = arcanum.api.ObjectFilter.And(listOf(m.what, filter))))))))))
    }

    private fun giantFlow(): CastFlow {
        val giant = arcanum.engine.cards.Cards.invent("Hand X Giant", "{X}{4}", "Creature — Giant", "", "4/4")
        val (card) = table.put(alice, Zone.Hand, giant)
        val state = table.state()
        return CastFlow.start(table.source(state), alice, HandPlays.of(state, alice, null).of(card).first(), CastFlow.Seeds(table::seed))
    }

    // STK-052, STK-023, CR 601.2f (castability review): two reducers start to apply at mana value 6: with three Forests X = 0
    // ({4}) and X = 1 ({5}) can't be paid, X = 2 and 3 can; the values the stepper offers that can't be paid are known, so Cast
    // greys on them; X = 4 can't be paid even at the most both reductions take, which ends the look exactly: 3 is the most
    @Test fun gapsInWhatAnXCanBePaidAreKnownAndTheMostStaysExact() {
        val reducer = drover("Hand Drover", 6)
        table.put(alice, Zone.Battlefield, reducer, reducer, "Forest", "Forest", "Forest")
        val flow = giantFlow()
        assertIs<Decision.Announce>(flow.pending)
        val shown = assertNotNull(HandCasts.announceable(flow, cap = 30))
        assertEquals(3, shown.most)
        assertEquals(setOf(0, 1), shown.unpayable)
    }

    // STK-052 (castability review): twelve reducers for kicked spells, which never apply to the unkicked Giant, could take
    // {24} as far as anything proves: with three Forests twenty values in a row can't be paid without proof that larger ones
    // can't: that bound is no "no", so the stepper stays open up to the top, the values tried greying Cast
    @Test fun aRunOfUnpayableValuesWithoutProofLeavesTheStepperOpen() {
        val reducer = drover("Hand Kicked Drover", arcanum.api.ObjectFilter.WasPaid(arcanum.api.Keyword.Kicker.Kind, 1))
        table.put(alice, Zone.Battlefield, *Array(12) { reducer }, "Forest", "Forest", "Forest")
        val flow = giantFlow()
        assertIs<Decision.Announce>(flow.pending)
        val shown = assertNotNull(HandCasts.announceable(flow, cap = 30))
        assertEquals(30, shown.most, "the look ran out of tries before the top")
        assertEquals((0..19).toSet(), shown.unpayable)
    }

    // STK-024: five any-colour sources tapped by hand for five colours: past the first combinations of colours the engine's search decides
    @Test fun aSetOfSourcesPaysPastTheFirstCombinations() {
        val sources = (1..5).map { arcanum.engine.state.ObjectId.Card(arcanum.engine.state.CardId("alice.$it"), 0) }
        val options = sources.flatMap { s -> arcanum.api.Color.all.map { c -> arcanum.engine.decision.ManaUse.Ability(s, arcanum.engine.view.AbilityOrigin.Printed(0, 0), c) } }
        val d = Decision.ManaPayment(arcanum.engine.decision.DecisionId("pay"), arcanum.engine.state.PlayerId("alice"), sources.first(), arcanum.api.ManaCost.parse("{W}{U}{B}{R}{G}")!!, options, emptyList())
        val pays = { uses: List<arcanum.engine.decision.ManaUse> -> uses.size == 5 && uses.map { (it as arcanum.engine.decision.ManaUse.Ability).color }.toSet().size == 5 }
        val answer = sources.zip(arcanum.api.Color.all).map { (s, c) -> arcanum.engine.decision.ManaUse.Ability(s, arcanum.engine.view.AbilityOrigin.Printed(0, 0), c) }
        var current: List<arcanum.engine.decision.ManaUse> = emptyList()
        ManaPicks.groups(d).forEach { group -> current = ManaPicks.toggle(d, current, group, pays) { pinned, _ -> if (pinned.size == 5) answer else emptyList() } }
        assertTrue(pays(current), "the five can pay it: $current")
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

    private fun chips(card: String): HandCastChips? {
        val state = table.state()
        return HandCasts.chips(table.source(state), table.definitions, alice, card, HandPlays.of(state, alice, null).of(card),
            CastFlow.Seeds(table::seed))
    }

    // ZON-080 (owner 2026-11-07): a card with one complete way and nothing to ask has one chip and no choice (its raised
    // card's tap casts it), with the Mountain Autotap would tap
    @Test fun oneWayIsOneChipAndNoChoice() {
        val pup = arcanum.engine.cards.Cards.invent("Ember Pup", "{R}", "Creature — Elemental Dog", "", "2/1")
        val mountains = table.put(alice, Zone.Battlefield, "Mountain")
        val (card) = table.put(alice, Zone.Hand, pup)
        val chips = assertNotNull(chips(card))
        assertEquals(listOf("Cast" to "{R}"), chips.chips.map { it.name to it.cost })
        assertFalse(chips.choice)
        assertFalse(chips.more)
        assertTrue(chips.chips.single().affordable)
        assertEquals(mountains.toSet(), chips.chips.single().sources)
        assertEquals(mountains.toSet(), chips.sources)
    }

    // ZON-079 (owner 2026-11-07): a greyed way beside the regular cast is no choice (the raised card's tap casts it); a card
    // whose only payable way isn't the regular cast (cycling) has one (its panel opens)
    @Test fun aGreyedWayIsNoChoice() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain")
        val (firebird) = table.put(alice, Zone.Hand, "Inspector's Firebird")
        val bestow = assertNotNull(chips(firebird))
        assertEquals(listOf(true, false), bestow.chips.map { it.affordable })
        assertFalse(bestow.choice)
        val musing = arcanum.engine.cards.Cards.invent("Ember Musing", "{3}{R}", "Sorcery", "Draw two cards.\nCycling {1}")
        val (card) = table.put(alice, Zone.Hand, musing)
        val cycling = assertNotNull(chips(card))
        assertEquals(listOf(false, true), cycling.chips.map { it.affordable })
        assertTrue(cycling.choice)
    }

    // ZON-080 (owner 2026-11-07): a kicker spell has two complete ways, "Cast" and "Cast with kicker" with its whole cost;
    // with two Mountains the kicked one is greyed; both ask for a target
    @Test fun kickerMakesTwoChips() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val chips = assertNotNull(chips(surge))
        assertEquals(listOf("Cast" to "{R}", "Cast with kicker" to "{4}{R}"), chips.chips.map { it.name to it.cost })
        assertEquals(listOf(listOf(0), listOf(1)), chips.chips.map { it.times })
        assertEquals(listOf(true, false), chips.chips.map { it.affordable })
        assertTrue(chips.chips.all { it.asks }, "a target to choose")
        assertTrue(chips.choice)
    }

    // ZON-080 (owner 2026-11-07): kicker × offspring gives four complete ways, extras off first, each with its total cost
    @Test fun kickerTimesOffspringMakesFourChips() {
        val brood = arcanum.engine.cards.Cards.invent("Ember Brood", "{1}{R}", "Creature — Elemental",
            "Kicker {1} (You may pay an additional {1} as you cast this spell.)\nOffspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)", "2/2")
        val mountains = table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (card) = table.put(alice, Zone.Hand, brood)
        val chips = assertNotNull(chips(card))
        assertEquals(listOf("Cast" to "{1}{R}", "Cast with kicker" to "{2}{R}", "Cast with offspring" to "{3}{R}",
            "Cast with kicker and offspring" to "{4}{R}"), chips.chips.map { it.name to it.cost })
        assertTrue(chips.chips.all { it.affordable && !it.asks })
        assertEquals(mountains.toSet(), chips.chips.last().sources, "five Mountains for {4}{R}")
        assertEquals(2, chips.chips.first().sources.size)
    }

    // ZON-080 (owner 2026-11-07): more than four complete ways (two ways × kicker × offspring) make one "Cast…" chip with
    // the cheapest cost the player can pay
    @Test fun moreThanFourWaysMakeOneCastChip() {
        val raider = arcanum.engine.cards.Cards.invent("Ember Broodraider", "{2}{R}", "Creature — Elemental",
            "Kicker {1}\nOffspring {2}\nDash {R}", "2/2")
        table.put(alice, Zone.Battlefield, "Mountain")
        val (card) = table.put(alice, Zone.Hand, raider)
        val chips = assertNotNull(chips(card))
        assertEquals(8, chips.count)
        assertTrue(chips.more)
        assertTrue(chips.choice)
        assertTrue(chips.chips.isEmpty())
        assertEquals("{R}", chips.cheapest, "Dash, the only way one Mountain pays")
        assertTrue(chips.affordable)
    }

    // ZON-080 (owner 2026-11-07): an Adventure is a complete way of its own; an alternative cost (evoke) too
    @Test fun adventureAndEvokeAreChips() {
        table.put(alice, Zone.Battlefield, "Island", "Island", "Island")
        table.put(table.bob, Zone.Battlefield, "Elvish Seer")
        val (pilferer) = table.put(alice, Zone.Hand, "Brash Pilferer")
        val adventure = assertNotNull(chips(pilferer))
        assertEquals(listOf("Cast" to "{1}{U}{U}", "Petty Pinch" to "{1}{U}"), adventure.chips.map { it.name to it.cost })
        assertEquals(listOf(false, true), adventure.chips.map { it.asks }, "the Adventure targets")
        val (vivacity) = table.put(alice, Zone.Hand, "Vivacity")
        assertEquals(listOf("Cast", "Evoke"), assertNotNull(chips(vivacity)).chips.map { it.name })
    }

    // STK-024 (owner 2026-11-07): a cast whose target is still open is taken on to its payment, which the panel shows
    @Test fun aheadReachesThePayment() {
        table.put(alice, Zone.Battlefield, "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val state = table.state()
        val flow = CastFlow.start(table.source(state), alice, HandPlays.of(state, alice, null).of(surge).first(), CastFlow.Seeds(table::seed),
            extras = listOf(0))
        assertIs<Decision.Targets>(flow.pending)
        val ahead = assertNotNull(HandCasts.ahead(flow))
        val payment = assertIs<Decision.ManaPayment>(ahead.pending)
        assertEquals("{R}", payment.cost.toString())
        assertTrue(ahead.pays(payment.suggestion))
    }

    // ZON-080 (owner 2026-11-07): the cast panel changes the way and extras, keeping the answers that still fit
    @Test fun withChoiceKeepsTheTarget() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        val (surge) = table.put(alice, Zone.Hand, "Flash Surge")
        val state = table.state()
        val begin = HandPlays.of(state, alice, null).of(surge).first()
        val flow = CastFlow.start(table.source(state), alice, begin, CastFlow.Seeds(table::seed), extras = listOf(0))
        val targeted = flow.answer(arcanum.engine.decision.Answer.Targets(listOf((flow.pending as Decision.Targets).options.first())))
        assertEquals("{R}", (targeted.pending as Decision.ManaPayment).cost.toString())
        val kicked = targeted.withChoice(begin, listOf(1))
        assertEquals(1, kicked.answers.size, "the target kept")
        assertEquals("{4}{R}", (kicked.pending as Decision.ManaPayment).cost.toString())
    }
}
