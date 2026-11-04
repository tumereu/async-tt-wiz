package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.HistoryIcon
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.TableChange
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * I-D56 step D on two of the app's engine games and the simulated server, both players online: the attack staging and
 * the block staging are shared table changes (I-D54 §8, CMB-028, CMB-034): the other player's table shows them as they
 * are made, the declaration ends them in its own event; the strip's texts and icons and the change flashes come from the
 * engine's events (HIS-031, HIS-061, HIS-070).
 */
class EngineSharedStagingTest {
    private val f = EngineGameFixture(creatures = true)
    private val sim = EngineAppSim(f)
    private val alice = sim.load(f.a)
    private val bob = sim.load(f.b)
    private val names = mapOf(f.a to "Alice", f.b to "Bob")
    /** Every transition each table showed (its board made after every step). */
    private val shown = mutableMapOf<String, MutableList<EngineGame.Transition>>()

    private fun settle() = repeat(40) {
        for (p in listOf(f.a, f.b)) {
            val game = sim.game(p)
            sim.deliver(p)
            game.playToStop()
            game.tick(true, sim.now)
            (game.device.status as? GameDevice.Status.Stopped)?.let { game.act(it.assumed) }
            sim.flush(p)
            game.play()
            game.transition?.let { t -> shown.getOrPut(p) { mutableListOf() }.let { if (it.lastOrNull()?.serial != t.serial) it += t } }
        }
        sim.now += 500
    }

    private fun cards(s: GameState, player: PlayerId, zone: List<ObjectId>, name: String) =
        zone.filter { s.obj(it)?.definition?.faces?.first()?.name == name && s.obj(it)?.owner == player }

    /** The player's main-phase plan: a Mountain, then an Ember Cub, then on. */
    private fun mainPhase(game: EngineGame) {
        val s = game.device.model.engine
        val me = game.device.me
        if ((s.turn.landsPlayed[me] ?: 0) == 0) cards(s, me, s.player(me).hand, "Mountain").firstOrNull()?.let {
            assertIs<GameDevice.Acted.Sent>(game.send(listOf(Input.PlayLand(me, it)))); return
        }
        val untapped = cards(s, me, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false }
        val cub = cards(s, me, s.player(me).hand, "Ember Cub").firstOrNull()
        if (untapped && cub != null) { game.send(cast(s, me, Input.Cast(me, cub))); return }
        game.pass()
    }

    /** A cast with its payment, as the cast panel sends it (Autotap: the engine's suggestion). */
    private fun cast(s: GameState, me: PlayerId, input: Input): List<Input> {
        val copy = s.deepCopy()
        val inputs = mutableListOf(input)
        var next = input
        while (true) {
            Engine.perform(copy, next).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != me) return inputs
            next = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
    }

    private fun creatures(s: GameState, player: PlayerId, ready: Boolean): List<ObjectId> {
        val view = Engine.view(s)
        return cards(s, player, s.battlefield, "Ember Cub").filter { !ready || (s.obj(it)?.tapped == false && view[it]?.summoningSick == false) }
    }

    /** Plays turns (each player: a Mountain, a Cub, passes, no attacks or blocks) until [done] holds where Alice declares
     * attackers. */
    private fun until(done: (GameState) -> Boolean) {
        alice.keep(emptyList()); bob.keep(emptyList())
        repeat(400) {
            settle()
            for (game in listOf(alice, bob)) {
                if (game.device.status != GameDevice.Status.YourMove) continue
                val s = game.device.model.engine
                val me = game.device.me
                val options = Engine.options(s, me)
                when {
                    options.any { it is Input.DeclareAttackers } -> if (me == PlayerId(f.a) && done(s)) return
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

    // CMB-028, CMB-034 (I-D54 §8): the staging is shared as the declaring player makes it and ends with the declaration
    @Test fun attackAndBlockStagingAreShared() {
        val me = PlayerId(f.a)
        val other = PlayerId(f.b)
        until { s -> creatures(s, me, ready = true).isNotEmpty() && creatures(s, other, ready = false).any { s.obj(it)?.tapped == false } }
        val s = alice.device.model.engine
        val attacker = creatures(s, me, ready = true).first()
        // Alice stages her Cub: one table-only event Bob's table shows as a lane
        val attacks = listOf(Attack(attacker, TargetRef.Player(other)))
        assertIs<GameDevice.Acted.Sent>(alice.table(listOf(TableChange.AttackDraft(null, attacks))))
        settle()
        assertEquals(attacks, bob.device.model.table.attackDraft)
        val lane = assertNotNull(bob.play().board.combat).pairs.single()
        assertEquals((attacker as ObjectId.Card).card.value, lane.attackers.single().sourceId)
        assertEquals(HistoryIcon.GPS_FIXED, EngineEventText.icon(bob.device.session.events.last { it.table.isNotEmpty() && it.engine == null }))
        assertEquals("Alice staged 1 attacker", bob.timeline.events(bob, names).last { it.icon == HistoryIcon.GPS_FIXED }.description)
        // The declaration ends the staging in its own event
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(Input.DeclareAttackers(me, attacks))))
        val declared = alice.device.session.pending.first()
        assertTrue(declared.table.any { it is TableChange.AttackDraft && it.after == null })
        assertNull(alice.device.model.table.attackDraft)
        settle()
        // Bob arranges a block: Alice's table shows it while he decides
        val bs = bob.device.model.engine
        assertTrue(Engine.options(bs, other).any { it is Input.DeclareBlockers }, "Bob declares blockers")
        val blocker = creatures(bs, other, ready = false).first { bs.obj(it)?.tapped == false }
        val blocks = listOf(Block(blocker, attacker))
        assertIs<GameDevice.Acted.Sent>(bob.table(listOf(TableChange.BlockDraft(null, blocks))))
        settle()
        assertEquals(blocks, alice.device.model.table.blockDraft)
        val pair = assertNotNull(alice.play().board.combat).pairs.single()
        assertEquals((blocker as ObjectId.Card).card.value, pair.blockers.single().sourceId)
        assertIs<GameDevice.Acted.Sent>(bob.send(listOf(Input.DeclareBlockers(other, blocks))))
        assertNull(bob.device.model.table.blockDraft)
        settle()
        assertEquals("Bob blocked with 1", alice.timeline.events(alice, names).last { it.icon == HistoryIcon.SWORDS }.description)
        assertTrue(sim.leaks.isEmpty())
    }

    // HIS-031, HIS-030: a change shown on the table flashes from the engine's own changes; a load is a jump
    @Test fun lifeLostFlashesAndTheLoadIsAJump() {
        alice.play()
        assertTrue(alice.transition!!.jump)
        val me = PlayerId(f.a)
        until { s -> creatures(s, me, ready = true).isNotEmpty() }
        val attacker = creatures(alice.device.model.engine, me, ready = true).first()
        bob.play()
        val before = bob.transition!!.serial
        shown.clear()
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(Input.DeclareAttackers(me, listOf(Attack(attacker, TargetRef.Player(PlayerId(f.b))))))))
        // Bob doesn't block: the Cub's 2 damage shows as a life change on his table
        repeat(30) {
            settle()
            val bs = bob.device.model.engine
            if (bob.device.status == GameDevice.Status.YourMove && Engine.options(bs, PlayerId(f.b)).any { it is Input.DeclareBlockers })
                bob.send(listOf(Input.DeclareBlockers(PlayerId(f.b), emptyList())))
            else if (bob.device.status == GameDevice.Status.YourMove && bs.priority.holder == PlayerId(f.b)) bob.pass()
            else if (alice.device.status == GameDevice.Status.YourMove && alice.device.model.engine.priority.holder == me &&
                alice.device.model.engine.turn.step in setOf(TurnStep.CombatDamage, TurnStep.DeclareBlockers, TurnStep.EndOfCombat)) alice.pass()
            val flashed = shown[f.b].orEmpty().firstOrNull { t -> t.serial > before && t.changes.any { it.key == TableChangeKey(f.b, TableChangeKind.LIFE) } }
            if (flashed != null) {
                assertEquals(listOf(java.math.BigInteger.valueOf(-2)), flashed.changes.first { it.key.kind == TableChangeKind.LIFE }.delta)
                assertTrue(!flashed.jump)
                return
            }
        }
        fail("Bob's life never flashed: ${bob.device.model.engine.player(PlayerId(f.b)).life}")
    }
}
