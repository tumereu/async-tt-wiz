package org.asyncttwiz.automatum

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import org.asyncttwiz.protocol.TypedCounter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Copies of cards (CR 707.12) through ATW: a Saga's last chapter exiles cards, copies them and casts the copies. Each copy
 * is an ATW token card in its owner's Exile showing the copied card's own card data, its ledger naming the engine's copy;
 * a cast copy stays there with its Stack entry pointing at it (STK-001), and a copy stops existing outside the Stack and
 * the Battlefield (CR 704.5e, TBL-103). Every call is checked by [RoundTrip] (rebuilt state = the engine's own, undo
 * restores the board).
 */
class CardCopiesTest {
    private val table = AtwTable("copies")
    private val alice get() = table.alice
    private val bob get() = table.bob

    private val chronicle = Cards.invent("The Chronicle of Tamiyo", "{2}{U}", "Legendary Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III — Mill two cards. If two cards that share a card type were milled this way, draw a card and repeat this process.
        IV — Exile any number of target instant, sorcery, and/or Tamiyo planeswalker cards from your graveyard. Copy them. You may cast any number of the copies.
    """)
    private val arcade = Cards.invent("Ashen Arcade", "{4}{R}{R}", "Enchantment",
        "Once each turn, you may pay {0} rather than pay the mana cost for a spell you cast from exile.")
    private val rebuke = Cards.invent("Kindled Rebuke", "{2}{R}", "Sorcery", "Kindled Rebuke deals 2 damage to each opponent.")
    private val verse = Cards.invent("Smolder Verse", "{2}{R}", "Sorcery", "Smolder Verse deals 2 damage to each opponent.")

    private fun zoneOf(rt: RoundTrip, card: PlayCard) = rt.state.board.containers.single { it.id == card.containerId }
    private fun copies(rt: RoundTrip) = rt.state.board.cards.filter { it.token }
    private fun life(rt: RoundTrip, player: String) = rt.state.board.players.single { it.accountId == player }.life

    /** Alice's draw step of turn 3: the Saga with three lore counters, [arcade] if [withArcade], three Mountains, and the
     * two sorceries in her graveyard. A pass reaches her main phase, where chapter IV triggers (CR 714.2b, 714.3b). */
    private fun chapterFour(withArcade: Boolean): RoundTrip {
        table.turn = TurnState(alice, "3", TurnPosition.DRAW.phase, TurnPosition.DRAW.step)
        table.priority = MtgPriority(alice)
        val (saga) = table.put(alice, PlayZone.BATTLEFIELD, chronicle)
        if (withArcade) table.put(alice, PlayZone.BATTLEFIELD, arcade)
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain", "Mountain")
        table.put(alice, PlayZone.GRAVEYARD, rebuke, verse)
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val state = table.state().let { s ->
            s.copy(board = s.board.copy(cards = s.board.cards.map {
                if (it.id == saga) it.copy(typedCounters = listOf(TypedCounter(EngineCodec.counterName(CounterKind.Lore), 3, 0))) else it
            }))
        }
        val rt = RoundTrip(state, table.definitions)
        rt.run(alice) { StepFlow.pass(it, alice) }
        // Chapter IV's targets: both sorceries in her graveyard
        val targets = rt.decision as Decision.Targets
        rt.perform(Input.Answer(targets.id, Answer.Targets(listOf(rebuke, verse).map { TargetRef.Object(rt.obj(it, Zone.Graveyard)) })), alice)
        // Both pass: chapter IV resolves up to its first "you may cast"
        while (rt.decision == null) rt.pass(rt.engine().priority.holder!!.value)
        return rt
    }

    private fun yes(rt: RoundTrip, yes: Boolean) = rt.answer { it == Answer.YesNo(yes) }
    private fun pay(rt: RoundTrip) {
        val d = rt.decision as Decision.ManaPayment
        rt.perform(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)), alice)
    }

    // CR 707.12, 112.2a: the copies are made in Alice's Exile, where the cards are: ATW token cards showing the copied
    // cards' own card data, named in their ledgers as the engine's copies of cards
    @Test fun copiesAreTokenCardsInExile() {
        val rt = chapterFour(withArcade = false)
        val copies = copies(rt)
        assertEquals(2, copies.size)
        assertTrue(copies.all { zoneOf(rt, it).zone == PlayZone.EXILE && zoneOf(rt, it).accountId == alice })
        val names = copies.map { c -> rt.state.definitions.single { it.digest == c.definition }.definition.name }.sorted()
        assertEquals(listOf(rebuke, verse).sorted(), names)
        // The copied card's own card data (its type line has no "Token"), marked as a token's
        val data = copies.map { c -> rt.state.definitions.single { it.digest == c.definition }.definition }
        assertTrue(data.all { it.token && it.faces.single().typeLine == "Sorcery" }, "$data")
        assertTrue(copies.all { CardLedger.of(it.ledger).copyOfCard })
        val engine = rt.engine()
        assertTrue(engine.exile.map { engine.objOrThrow(it) }.count { it.copyOfCard && !it.token } == 2)
    }

    // CR 707.12, 707.12a, 601.2a: each copy may be cast; a cast copy is a spell whose entry points at its token card,
    // which stays in Exile (STK-001); one not cast ceases to exist (CR 704.5e); a resolved copy put into the graveyard
    // ceases to exist too, and only the real cards stay in Exile
    @Test fun aCastCopyResolvesAndAnUncastOneCeasesToExist() {
        val rt = chapterFour(withArcade = false)
        yes(rt, true)
        pay(rt)
        yes(rt, false)
        val entry = rt.state.board.stackEffects.single()
        val cast = rt.state.board.cards.single { it.id == entry.sourceId }
        assertTrue(cast.token && zoneOf(rt, cast).zone == PlayZone.EXILE, "the spell's card is its copy, in Exile")
        assertEquals(1, copies(rt).size, "the copy not cast ceased to exist")
        while (rt.engine().stack.isNotEmpty()) rt.pass(rt.engine().priority.holder!!.value)
        assertEquals("18", life(rt, bob))
        assertEquals(0, copies(rt).size)
        val exile = rt.state.board.containers.single { it.zone == PlayZone.EXILE && it.accountId == alice }.cards
        assertEquals(2, exile.size, "the two exiled cards")
        assertTrue(rt.state.board.containers.single { it.zone == PlayZone.GRAVEYARD && it.accountId == alice }.cards.none { id ->
            rt.state.board.cards.single { it.id == id }.token })
    }

    // CR 608.2g, 601.2b, 118.9: the copies cast while chapter IV resolves are spells cast from exile, so the {0}
    // alternative cost another permanent gives may be announced, once each turn
    @Test fun copiesUseAGrantedAlternativeCost() {
        val rt = chapterFour(withArcade = true)
        yes(rt, true)
        rt.answer { it is Answer.Option && "{0}" in it.chosen }
        yes(rt, true)
        // The {0} was used this turn: the second copy's mana cost is paid without a choice
        pay(rt)
        assertEquals(2, rt.state.board.stackEffects.size)
        while (rt.engine().stack.isNotEmpty()) rt.pass(rt.engine().priority.holder!!.value)
        assertEquals("16", life(rt, bob))
        assertEquals(0, copies(rt).size)
    }
}
