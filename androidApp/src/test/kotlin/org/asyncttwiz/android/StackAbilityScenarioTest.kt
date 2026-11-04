package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-04: a creature's enters trigger on the Stack was drawn as the creature card, so it looked as if
 * the spell were still waiting to resolve. Two app engine games on the simulated server: Alice casts an Ember Herald
 * (an enters trigger) while Bob is away and plays on; Bob's playback stops at her spell, then at her trigger. Both on
 * the Stack and in every text the trigger is the Herald's ability, never the Herald itself.
 */
class StackAbilityScenarioTest {
    private val f = EngineGameFixture(heralds = true)
    private val a = PlayerId(f.a)
    private val names = mapOf(f.a to "Alice", f.b to "Bob")
    private val heraldText = "When this creature enters, create a 1/1 red Elemental creature token."

    /** Alice's device on its own while Bob is away: what the server sent played back, her device's own steps sent. */
    private fun alone(sim: EngineAppSim, alice: EngineGame) {
        sim.deliver(f.a)
        alice.playToStop()
        (alice.device.status as? GameDevice.Status.Stopped)?.let { alice.act(it.assumed) }
        alice.tick(false, sim.now)
        sim.flush(f.a)
        sim.now += 1_000
    }

    private fun top(play: TableBoard): String? = CombatStackRules.stack(play.board).cards.firstOrNull()

    /** Plays [game] back until it stops. */
    private fun playToStop(game: EngineGame) {
        var guard = 0
        while (game.device.status is GameDevice.Status.Playing && guard++ < 200) assertNotNull(game.step())
    }

    // STK-046, HIS-091, TRN-066 (owner playtest 2026-11-04): an enters trigger on the Stack is drawn and named as its
    // source's ability, for both players; the spell itself stays a card
    @Test fun anEntersTriggerShowsAsTheSourcesAbilityOnBothDevices() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        bob.settings(PlaySettings(mode = ResponseMode.Normal))
        sim.flush(f.b)
        alice.keep(emptyList())
        sim.flush(f.a)
        sim.deliver(f.b)
        bob.playToStop()
        bob.keep(emptyList())
        sim.flush(f.b)
        repeat(5) { alone(sim, alice) }
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        val s0 = alice.device.model.engine
        val mountain = s0.player(a).hand.first { s0.obj(it)?.definition?.faces?.first()?.name == "Mountain" }
        alice.send(listOf(Input.PlayLand(a, mountain)))
        sim.flush(f.a)
        val s1 = alice.device.model.engine
        val cast = assertNotNull(Engine.options(s1, a).filterIsInstance<Input.Cast>().firstOrNull {
            s1.obj(it.card)?.definition?.faces?.first()?.name == "Ember Herald" })
        assertIs<GameDevice.Acted.Sent>(alice.send(EngineCombatScenario(f).cast(s1, a, cast)))
        sim.flush(f.a)
        alice.endTurn()
        repeat(3) { alone(sim, alice) }

        // Bob's playback stops first at the spell: it is the Herald card, no ability
        sim.deliver(f.b)
        playToStop(bob)
        val spellStop = assertIs<GameDevice.Status.Stopped>(bob.device.status)
        val spellBoard = bob.play()
        val spell = assertNotNull(top(spellBoard))
        assertEquals(MtgStackKind.Spell, spellBoard.board.stackEffects.single { it.id == spell }.item?.kind)
        assertNull(StackAbilities.of(spellBoard, spell, f.b), "a spell is drawn as its card")
        assertEquals("Ember Herald", contentsEntries(spellBoard, PileContents("stack", "Stack", listOf(spell), true), f.b).single().name)
        bob.act(spellStop.assumed)
        sim.flush(f.b)

        // Then at the trigger: the Herald's ability, drawn as an ability token with its text, for both players
        playToStop(bob)
        assertIs<GameDevice.Status.Stopped>(bob.device.status)
        val play = bob.play()
        val entry = assertNotNull(top(play))
        assertEquals(MtgStackKind.Triggered, play.board.stackEffects.single { it.id == entry }.item?.kind)
        val shown = StackAbilityShown(triggered = true, source = "Ember Herald", text = heraldText)
        assertEquals(shown, StackAbilities.of(play, entry, f.b))
        assertEquals(shown, StackAbilities.of(play, entry, f.a))
        val scene = TablePresentation.scene(f.game, play, f.b, 412f, 892f, false)
        val drawing = scene.items.single { it.id == "stackDrawing/$entry" }
        assertEquals(shown, drawing.stackAbility)
        assertTrue("Triggered ability, Ember Herald — $heraldText" in drawing.label, drawing.label)
        // The Stack list and the target picker name it as an ability
        val listed = contentsEntries(play, PileContents("stack", "Stack", listOf(entry), true), f.b).single()
        assertEquals("Ember Herald's ability", listed.name)
        assertEquals(shown, listed.ability)
        // The wait's words on Alice's side name the ability, not the card
        val item = play.engine!!.state.stack.last()
        assertIs<ObjectId.StackItem>(item)
        assertEquals("Bob may respond to Ember Herald's ability", waitingText(GameDevice.Wait.Respond(item), play, "Bob"))

        // The events where they resolved: the spell as the card, then the trigger as the Herald's ability
        val resolutions = sim.line().filter { e -> e.engine?.changes.orEmpty().any { it is Change.StackItemRemoved } }
            .map { EngineEventText.of(it, bob.shown!!.engine, f.b, names) }
        assertEquals(listOf("Ember Herald resolved", "Ember Herald's ability resolved"), resolutions.take(2), "$resolutions")
    }
}
