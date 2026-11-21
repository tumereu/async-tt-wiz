package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HIS-112 (owner, 2026-11-20: a Room unlocked in prod showed nothing): the other player's special actions (CR 116.2), which
 * don't use the Stack, are shown on the player's table as their activations are, holding the view until done; never the
 * player's own (HIS-143).
 */
class SpecialActionShowTest {
    private val schemer = Cards.invent("Ember Plotter", "{3}{R}", "Sorcery", "Ember Plotter deals 2 damage to any target.\nPlot {0}")
    private val room = Cards.inventFaces("Split",
        face("Ember Hall", "{0}", "Enchantment — Room", "", null, null, null),
        face("Ember Study", "{0}", "Enchantment — Room", "When you unlock this door, you gain 2 life.", null, null, null),
    )

    private fun sim(bobs: String) = DeviceSim(EngineGames(3, List(40) { "Forest" } to List(40) { bobs }))

    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Bob starts and keeps; Alice keeps and is away (his device assumes her passes up to his main phase); then her table is
     * live at the head. With [before], what Bob does first while no play is shown yet. */
    private fun DeviceSim.toBobsMain(before: DeviceSim.() -> Unit = {}) {
        start(bob)
        keepWhileAway(bob)
        device(alice).play()
        settle()
        before()
        settle()
        assertEquals(server.head, device(alice).session.localHead, "Alice live")
        device(alice).showPlays = true
        device(bob).showPlays = true
    }

    /** Bob makes [input], answering his own questions with the first answer the engine lists. */
    private fun DeviceSim.bobActs(input: Input) {
        val bob = bob
        val acted = device(bob).act(input) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob, EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>().first()
        }
        assertIs<GameDevice.Acted.Sent>(acted)
    }

    // HIS-112, HIS-237, HIS-140, HIS-143 (CR 702.170a): Bob plots a card from his hand: Alice's table shows it, a special action of his plotted card
    // (its object in his hand, which special action it was), her view held at it; his own table shows nothing; done, hers plays on
    @Test
    fun theOtherPlayersPlotShowsOnTheTable() {
        val sim = sim(schemer)
        sim.toBobsMain()
        val bob = sim.bob
        val card = hand(sim.device(bob).model, bob, schemer)
        val plot = assertNotNull(Engine.view(sim.device(bob).model.engine)[card]?.characteristics?.abilities
            ?.firstOrNull { it.ability is arcanum.api.Ability.SpecialAction }, "Plot is a special action").origin
        sim.bobActs(Input.Activate(bob, card, plot))
        sim.settle()
        val alices = sim.device(sim.alice)
        val show = assertNotNull(alices.show, "Bob's plot shown on Alice's table")
        assertEquals(GameDevice.PlayKind.Special, show.kind)
        assertEquals(bob, show.by)
        assertEquals(card, show.entry)
        assertEquals((card as ObjectId.Card).card, show.card)
        assertEquals(plot, show.ability)
        assertEquals(show.event, alices.session.localHead, "her table at the plot")
        assertNull(alices.step(), "playback waits while it shows")
        assertNull(sim.device(bob).show, "his own special action shows nothing")
        assertTrue(alices.dismissShow(show))
        sim.settle()
        assertNull(alices.show)
        assertEquals(sim.server.head, alices.session.localHead, "on to the head once done")
    }

    // HIS-112, HIS-237, HIS-134 (CR 709.5e, 709.5g): Bob unlocks his Room's other door, whose "when you unlock this door" trigger goes on the Stack:
    // on Alice's table the unlock shows first, then the trigger, in that order; on his own table only his trigger
    @Test
    fun anUnlockShowsBeforeItsTrigger() {
        val sim = sim(room)
        sim.toBobsMain { bobActs(Input.Cast(bob, hand(device(bob).model, bob, "Ember Hall"))) }
        val bob = sim.bob
        val rooms = sim.device(bob).model.engine.battlefield.filter { sim.device(bob).model.engine.obj(it)?.owner == bob }
        val hall = rooms.single()
        val unlock = AbilityOrigin.Rule("unlock-right")
        sim.bobActs(Input.Activate(bob, hall, unlock))
        sim.settle()
        val alices = sim.device(sim.alice)
        val moments = alices.openShows
        assertEquals(listOf(GameDevice.PlayKind.Special, GameDevice.PlayKind.Triggered), moments.map { it.kind })
        assertEquals(unlock, moments.first().ability)
        assertEquals(hall, moments.first().entry)
        assertEquals(moments.first(), alices.show, "the unlock first")
        assertTrue(alices.dismissShow())
        assertEquals(GameDevice.PlayKind.Triggered, alices.show?.kind, "then its trigger")
        assertEquals(listOf(GameDevice.PlayKind.Triggered), sim.device(bob).openShows.map { it.kind }, "his own trigger only")
    }
}
