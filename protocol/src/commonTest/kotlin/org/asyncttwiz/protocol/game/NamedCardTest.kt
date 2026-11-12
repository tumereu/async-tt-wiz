package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.Inspect
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CR 707.12 (MH2: "Create a copy of the card with the chosen name"): the engine asks the host for the named card
 * ([Decision.NamedCard]); the player's device answers it from its card records ([GameDevice.namedCards]) with their own
 * action, so the copy is made and may be cast. Before, nobody answered it and the game waited.
 */
class NamedCardTest {
    private val study = Cards.invent("Ember Study", "{0}", "Sorcery", "Draw a card.")
    private val copier = Cards.invent("Ember Copier", "{0}", "Artifact",
        "{T}: Choose a card name that hasn't been chosen from among Ember Study, Forest, Island, Mountain, Plains, and Swamp. " +
            "Create a copy of the card with the chosen name. You may cast the copy.")

    /** Bob's deck of Copiers, Alice's of Forests; Bob starts and keeps, Alice keeps and plays along live; Bob's phone names
     * cards from [records]. */
    private fun sim(records: NamedCards): DeviceSim {
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to List(40) { copier }))
        sim.start(sim.bob)
        sim.keepWhileAway(sim.bob)
        sim.device(sim.alice).play()
        sim.settle()
        sim.device(sim.bob).namedCards = records
        return sim
    }

    private fun DeviceSim.copierOf(): ObjectId = device(bob).model.engine.let { s ->
        s.battlefield.first { s.obj(it)?.definition?.faces?.first()?.name == copier }
    }

    /** Bob makes [input], answering his own decisions: [name] for the name, yes to casting the copy, else the first listed
     * answer; the named card is his phone's to answer. */
    private fun DeviceSim.bobDoes(input: Input, name: String = study) {
        val acted = device(bob).act(input) { m ->
            val d = m.engine.pending?.decision
            when {
                d == null || d is Decision.Random || d is Decision.NamedCard || d.player != bob -> null
                d is Decision.ChooseOption -> Input.Answer(d.id, Answer.Option(name))
                d is Decision.May -> Input.Answer(d.id, Answer.YesNo(true))
                else -> Engine.options(m.engine, bob, EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            }
        }
        assertIs<GameDevice.Acted.Sent>(acted)
        settle()
    }

    /** Bob activates his Copier and answers what its resolution asks of him ([bobDoes]) until nothing of his is open. */
    private fun DeviceSim.bobCopies() {
        bobDoes(Input.Activate(bob, copierOf(), device(bob).model.engine.let { s ->
            Engine.options(s, bob, EnginePreferences.of(s)).filterIsInstance<Input.Activate>().first { it.source == copierOf() }.ability
        }))
        repeat(5) {
            val d = device(bob).model.engine.pending?.decision ?: return
            if (d.player != bob || d is Decision.NamedCard) return
            bobDoes(when (d) {
                is Decision.ChooseOption -> Input.Answer(d.id, Answer.Option(study))
                is Decision.May -> Input.Answer(d.id, Answer.YesNo(true))
                else -> device(bob).model.engine.let { s -> Engine.options(s, bob, EnginePreferences.of(s)).filterIsInstance<Input.Answer>().first() }
            })
        }
    }

    private fun DeviceSim.copierInHand(): ObjectId = device(bob).model.engine.let { s ->
        s.player(bob).hand.first { s.obj(it)?.definition?.faces?.first()?.name == copier }
    }

    // CR 707.12: Bob's Copier names Ember Study; his phone gives the engine the Study from his card records, the copy is made
    // (a copy of a card, not a card) and cast, and resolves: he draws a card; both phones see the same game
    @Test
    fun theNamedCardIsAnsweredFromTheCardRecords() {
        val asked = mutableListOf<String>()
        val sim = sim { name -> asked += name; Cards.definition(name) }
        sim.bobDoes(Input.Cast(sim.bob, sim.copierInHand()))
        val hand = sim.device(sim.bob).model.engine.player(sim.bob).hand.size
        sim.bobCopies()
        assertEquals(listOf(study), asked, "the engine asked for the named card once")
        val s = sim.device(sim.bob).model.engine
        assertNull(s.pending, "nothing waits: ${s.pending?.decision}")
        val copy = Inspect.events(s).mapNotNull { (it.change as? Change.ZoneChange) }.flatMap { it.moves }
            .firstOrNull { it.after.definition.faces.first().name == study && ObjectStatus.CopyOfCard in it.after.statuses }
        assertTrue(copy != null, "the copy of the Study was cast")
        assertEquals(hand + 1, s.player(sim.bob).hand.size, "the copy resolved: a card drawn")
        assertEquals(s, sim.device(sim.alice).model.engine, "Alice's phone has the same game")
    }

    // Without card records (none installed), the decision waits for them; nothing else answers it
    @Test
    fun withoutCardRecordsTheNamedCardWaits() {
        val sim = sim(NamedCards.NONE)
        sim.bobDoes(Input.Cast(sim.bob, sim.copierInHand()))
        sim.bobCopies()
        val d = sim.device(sim.bob).model.engine.pending?.decision
        assertIs<Decision.NamedCard>(d)
        assertEquals(study, d.name)
        assertTrue(sim.device(sim.bob).recentRecord(20).any { "named card $study: none in the card records" in it })
    }
}
