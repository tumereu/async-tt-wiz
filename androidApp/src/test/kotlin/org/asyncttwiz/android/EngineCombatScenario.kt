package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import kotlin.test.fail

/**
 * Alice and Bob playing the creatures fixture through their app engine games on the simulated server ([EngineAppSim]):
 * each turn a Mountain and an Ember Cub, passing otherwise, never attacking or blocking, until Alice declares attackers
 * with [until]'s condition met. [online] says who is online (the green dot each sees of the other).
 */
internal class EngineCombatScenario(val f: EngineGameFixture = EngineGameFixture(creatures = true)) {
    val sim = EngineAppSim(f)
    val alice = sim.load(f.a)
    val bob = sim.load(f.b)
    val me = PlayerId(f.a)
    val other = PlayerId(f.b)
    val online = mutableMapOf(f.a to true, f.b to true)
    /** Whether [settle] answers a player's decision stop with the assumed input (their device left alone otherwise). */
    val confirmStops = mutableMapOf(f.a to true, f.b to true)
    /** Every transition each table showed (its board made after every step). */
    val shown = mutableMapOf<String, MutableList<EngineGame.Transition>>()

    fun settle() = repeat(40) {
        for (p in listOf(f.a, f.b)) {
            if (online[p] == false) continue
            val game = sim.game(p)
            sim.deliver(p)
            game.playToStop()
            game.tick(sim.now)
            if (confirmStops[p] != false) (game.device.status as? GameDevice.Status.Stopped)?.let { game.act(it.assumed) }
            sim.flush(p)
            game.play()
            game.transition?.let { t -> shown.getOrPut(p) { mutableListOf() }.let { if (it.lastOrNull()?.serial != t.serial) it += t } }
        }
        sim.now += 500
    }

    fun cards(s: GameState, player: PlayerId, zone: List<ObjectId>, name: String) =
        zone.filter { s.obj(it)?.definition?.faces?.first()?.name == name && s.obj(it)?.owner == player }

    /** [player]'s Cubs on the battlefield ([ready]: untapped and able to attack). */
    fun creatures(s: GameState, player: PlayerId, ready: Boolean): List<ObjectId> {
        val view = Engine.view(s)
        return cards(s, player, s.battlefield, "Ember Cub").filter { !ready || (s.obj(it)?.tapped == false && view[it]?.summoningSick == false) }
    }

    /** The player's main-phase plan: a Mountain, then an Ember Cub, then on. */
    private fun mainPhase(game: EngineGame) {
        val s = game.device.model.engine
        val me = game.device.me
        if ((s.turn.landsPlayed[me] ?: 0) == 0) cards(s, me, s.player(me).hand, "Mountain").firstOrNull()?.let {
            game.send(listOf(Input.PlayLand(me, it))); return
        }
        val untapped = cards(s, me, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false }
        val cub = cards(s, me, s.player(me).hand, "Ember Cub").firstOrNull()
        if (untapped && cub != null) { game.send(cast(s, me, Input.Cast(me, cub))); return }
        game.pass()
    }

    /** A cast with its payment, as the cast panel sends it (Autotap: the engine's suggestion); its targets the first the
     * engine lists, or [target] when given. */
    fun cast(s: GameState, me: PlayerId, input: Input, target: arcanum.engine.state.TargetRef? = null): List<Input> {
        val copy = s.deepCopy()
        val inputs = mutableListOf(input)
        var next = input
        while (true) {
            Engine.perform(copy, next).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != me) return inputs
            next = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else if (d is Decision.Targets && target != null) Input.Answer(d.id, Answer.Targets(listOf(target)))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
    }

    /** Plays turns until [done] holds where Alice declares attackers (her move, the step arrow's declaration open). */
    fun until(done: (GameState) -> Boolean) {
        alice.keep(); bob.keep()
        repeat(400) {
            settle()
            for (game in listOf(alice, bob)) {
                // HIS-095, HIS-096: a stop at the other side's assumption about them (live too) is answered like their move
                if (game.device.status != GameDevice.Status.YourMove && game.device.status !is GameDevice.Status.Stopped) continue
                val s = game.device.model.engine
                val me = game.device.me
                val options = Engine.options(s, me)
                when {
                    options.any { it is Input.DeclareAttackers } -> if (me == this.me && done(s)) return
                        else game.send(listOf(Input.DeclareAttackers(me, emptyList())))
                    options.any { it is Input.DeclareBlockers } -> game.send(listOf(Input.DeclareBlockers(me, emptyList())))
                    s.turn.active == me && s.stack.isEmpty() && s.turn.step in setOf(TurnStep.PrecombatMain, TurnStep.PostcombatMain) &&
                        s.priority.holder == me -> mainPhase(game)
                    s.priority.holder == me -> game.pass()
                    else -> {}
                }
            }
        }
        fail("never reached: ${alice.device.model.engine.turn}")
    }

    /** Alice has a Cub ready to attack and Bob an untapped Cub to block with. */
    fun bothHaveCubs(s: GameState) = creatures(s, me, ready = true).isNotEmpty() && creatures(s, other, ready = false).any { s.obj(it)?.tapped == false }
}
