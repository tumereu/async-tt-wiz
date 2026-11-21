package org.asyncttwiz.protocol.game

import arcanum.engine.Host
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * STK-224, STK-225, HIS-155, HIS-110, HIS-233 (owner 2026-11-21): a choice from among cards just revealed, looked at or exiled face up
 * is its chooser's one panel for them: the cards it can't choose show with it ([Decision.ChooseObjects.others]), so no reveal panel of
 * the chooser's comes before it. The other player's table is unchanged: a reveal or a face-up exile still opens their panel with all
 * its cards, and of a private look they see nothing but the card the chooser reveals afterwards.
 */
class ChoiceAmongRevealTest {
    private val unveiling = Cards.invent("Ember Unveiling", "{0}", "Sorcery",
        "Reveal the top four cards of your library. You may put a creature card from among them into your hand. Put the rest on the bottom of your library in a random order.")
    private val glimpse = Cards.invent("Ember Glimpse", "{0}", "Sorcery",
        "Look at the top four cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.")
    private val excavation = Cards.invent("Ember Excavation", "{0}", "Sorcery",
        "Exile the top three cards of your library. Choose one of them. You may play that card this turn.")
    private val wolf = Cards.invent("Gray Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")

    /** Bob's deck of [spell]s, Gray Wolves and Islands, Alice's of Forests; both playing live, Bob in his main phase. */
    private fun live(spell: String): DeviceSim {
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(14) { spell } + List(13) { wolf } + List(13) { "Island" })))
        sim.start(sim.bob)
        sim.keepWhileAway(sim.bob)
        sim.device(sim.alice).play()
        sim.settle()
        return sim
    }

    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Bob casts [spell] and it resolves up to his choice among the cards, which stays open on his phone. */
    private fun DeviceSim.bobCastsUpToHisChoice(spell: String, others: Boolean = true): Decision.ChooseObjects {
        val bobs = device(bob)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, spell))))
        settle()
        val d = assertIs<Decision.ChooseObjects>(bobs.model.engine.pending?.decision, "Bob's choice among the cards")
        assertEquals(bob, d.player)
        assertTrue(d.options.isNotEmpty() && d.others.isNotEmpty() == others, "cards to choose, other cards beside them ($others): $d")
        return d
    }

    private fun cards(ids: Collection<ObjectId>) = ids.mapNotNull { (it as? ObjectId.Card)?.card }.toSet()

    // STK-224, STK-225, HIS-155, HIS-110: "Reveal the top four … You may put a creature card from among them into your hand": Bob's
    // phone opens no reveal panel, only the choice, which holds every revealed card (the Wolves to choose, the rest beside them);
    // Alice's phone opens the reveal panel with all four cards
    @Test
    fun aRevealThenChooseIsTheChoosersOnePanelAndStillARevealForTheOther() {
        val sim = live(unveiling)
        val d = sim.bobCastsUpToHisChoice(unveiling)
        assertNull(sim.device(sim.bob).reveal, "no reveal panel of Bob's: the choice shows the cards")
        val hers = assertNotNull(sim.device(sim.alice).reveal, "Alice's reveal panel")
        assertEquals(sim.bob, hers.by)
        assertEquals(4, hers.cards.size)
        assertEquals(cards(d.options + d.others), hers.cards.toSet(), "every revealed card in her panel")
    }

    // STK-224, STK-225: "Look at the top four … you may reveal a creature card from among them": Bob's choice holds the four cards and
    // no reveal panel opens on either phone for the look; Alice's phone never sees the cards looked at, and opens a panel only for the
    // Wolf Bob reveals, with that card alone
    @Test
    fun aPrivateLookThenChooseShowsTheOtherPlayerNothingButTheCardRevealed() {
        val sim = live(glimpse)
        val d = sim.bobCastsUpToHisChoice(glimpse)
        val alices = sim.device(sim.alice)
        val bobs = sim.device(sim.bob)
        assertNull(bobs.reveal, "no panel of Bob's")
        assertTrue(alices.openReveals.isEmpty(), "nothing shown to Alice of the look: ${alices.openReveals}")
        val looked = cards(d.options + d.others)
        fun seenByAlice(): Set<CardId> = Host.visible(alices.model.engine, sim.alice).mapNotNull { alices.model.engine.obj(it)?.card }.toSet()
        assertTrue(looked.none { it in seenByAlice() }, "no card looked at visible to Alice")
        val wolf = d.options.first()
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Answer(d.id, Answer.Objects(listOf(wolf)))))
        sim.settle()
        val hers = assertNotNull(alices.reveal, "Alice's panel for the Wolf Bob revealed")
        assertEquals(listOf((wolf as ObjectId.Card).card), hers.cards, "the revealed Wolf alone")
        assertNull(bobs.reveal, "the Wolf he chose isn't news to Bob")
        assertTrue(alices.dismissReveal())
        sim.settle()
        assertTrue((looked - wolf.card).none { it in seenByAlice() }, "the cards he didn't reveal stay hidden from Alice")
    }

    // STK-225, HIS-233: "Exile the top three cards of your library. Choose one of them.": the exile opens no panel of Bob's, his choice
    // holds the three cards; Alice's phone opens the library exile's panel with all three
    @Test
    fun anExileThenChooseIsTheChoosersOnePanelAndStillShownToTheOther() {
        val sim = live(excavation)
        val d = sim.bobCastsUpToHisChoice(excavation, others = false)
        assertNull(sim.device(sim.bob).reveal, "no exile panel of Bob's: the choice shows the cards")
        val hers = assertNotNull(sim.device(sim.alice).reveal, "Alice's panel of the exile")
        assertEquals(sim.bob, assertNotNull(hers.exiled).owner)
        assertEquals(cards(d.options + d.others), hers.cards.toSet(), "every exiled card in her panel")
    }
}
