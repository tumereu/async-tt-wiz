package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.AssumeReveals
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * LOB-135 on Alice's phone: the game's assume reveal settings reach her engine game from the game's summary
 * ([EngineGame.assumeReveals]) and her device's information rule follows them at once.
 */
class EngineAssumeRevealsTest {
    /** Alice's game after her keep, with Bob away; she casts [spell] at him. */
    private fun casts(spell: String, reveals: AssumeReveals): EngineGame {
        val f = EngineGameFixture(aliceCards = listOf("Mountain", spell))
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true))
        game.assumeReveals(reveals)
        game.apply { tick(1_000); keep(emptyList()); tick(2_000) }
        val me = PlayerId(f.a)
        val card = game.device.model.engine.let { s -> s.player(me).hand.first { s.obj(it)?.definition?.faces?.first()?.name == spell } }
        val copy = game.device.model.engine.deepCopy()
        val inputs = mutableListOf<Input>(Input.Cast(me, card))
        var next: Input = inputs.first()
        while (true) {
            Engine.perform(copy, next).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Engine.decider(copy, d.player) != me) break
            next = if (d is Decision.Targets) Input.Answer(d.id, Answer.Targets(listOf(arcanum.engine.state.TargetRef.Player(PlayerId(f.b)))))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
        assertIs<GameDevice.Acted.Sent>(game.send(inputs))
        game.tick(4_000)
        return game
    }

    private fun EngineGame.waitsForHiddenCards() = (device.status as? GameDevice.Status.Waiting)?.reason == GameDevice.Wait.HiddenCards

    // LOB-135, TRN-071: with the game's defaults a reveal of Bob's library top is assumed through, a reveal of his hand waits;
    // the host turning "Allow assumes that reveal hands" on lets that wait go on at once (Bob's pass assumed, the spell
    // resolved)
    @Test fun theGamesSettingsDecideTheWaitAndAChangeCountsAtOnce() {
        val augury = casts(EngineGameFixture.Choices.AUGURY, AssumeReveals())
        assertTrue(augury.device.model.engine.stack.isEmpty(), "the Augury resolved on Bob's assumed pass: ${augury.device.status}")
        val exposure = casts(EngineGameFixture.Choices.EXPOSURE, AssumeReveals())
        assertTrue(exposure.waitsForHiddenCards(), "${exposure.device.status}")
        assertEquals(1, exposure.device.model.engine.stack.size)
        exposure.assumeReveals(AssumeReveals(library = true, hands = true))
        assertTrue(exposure.device.model.engine.stack.isEmpty(), "the Exposure resolved on Bob's assumed pass: ${exposure.device.status}")
        assertTrue(exposure.device.session.pending.any { it.assumed != null })
    }

    // LOB-135: with the library setting off, the Augury's reveal waits as well
    @Test fun withTheLibrarySettingOffALibraryRevealWaits() {
        assertTrue(casts(EngineGameFixture.Choices.AUGURY, AssumeReveals.NONE).waitsForHiddenCards())
    }
}
