package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner problem report JVPJ8Y (beta16): the attacker's phone assumed the defender's pass after a double block, and the
 * combat damage step that followed opened the attacker's own division of their creature's damage among the two blockers
 * (CR 510.1c). It is order-free (enough for lethal damage to each, STK-033), so the phone answered it with the engine's
 * suggestion in the same event, as it folds every engine-automatic answer; the server took that answer for the author's
 * own real input in an event that began with an assumed one (I-D56 §13) and refused the event ("refusedByEngine"), so
 * every press of "Assume no response" was taken back.
 */
class AssumedDivisionTest {
    private val hauler = arcanum.engine.cards.Cards.invent("Zinc Hauler", "{0}", "Artifact Creature — Construct", "", "4/3")
    private val pilot = arcanum.engine.cards.Cards.invent("Rattled Pilot", "{0}", "Creature — Zombie Pilot", "", "2/2")

    private fun hand(model: GameModel, p: PlayerId, name: String): ObjectId =
        model.engine.player(p).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    private fun GameModel.named(p: PlayerId, zone: List<ObjectId>, name: String) =
        zone.filter { engine.obj(it)?.controller == p || engine.obj(it)?.owner == p }.filter { engine.obj(it)?.definition?.faces?.first()?.name == name }

    // JVPJ8Y, STK-033, CR 510.1c: Alice casts a Zinc Hauler (4/3) in her turn 1, Bob two Rattled Pilots (2/2) in his turn 2;
    // in her turn 3 she attacks with the Hauler and Bob blocks with both. Both pass by hand (Control, Assume Never). Alice
    // passes after the blocks and presses Assume for Bob's pass: her 4 damage divides 2 and 2 by itself, both Pilots die,
    // the Hauler (2 + 2 damage, toughness 3) dies too, and the server takes the event as her phone made it
    @Test
    fun anAssumedPassBeforeAnOrderFreeDivisionOfYourOwnIsTaken() {
        val sim = DeviceSim(EngineGames(3, List(40) { hauler } to List(40) { pilot }))
        val alice = sim.alice; val bob = sim.bob
        sim.start(alice, mode = ResponseMode.FullControl, alwaysAssume = false)
        for (p in listOf(alice, bob)) sim.device(p).settings(PlaySettings(mode = ResponseMode.FullControl).assuming(AssumeResolves.Never))
        sim.settle()
        for (p in listOf(alice, bob)) { assertIs<GameDevice.Acted.Sent>(sim.device(p).act(sim.keep(p))); sim.settle() }
        fun s() = sim.server.state().engine
        var guard = 0
        // Each player's move by hand until Alice's attack is declared and the blockers are awaited
        while (guard++ < 300) {
            val state = s()
            if (state.turn.number == 3 && state.turn.step == TurnStep.DeclareBlockers && state.priority.holder == null) break
            val acting = listOf(alice, bob).first { p -> sim.device(p).status.let { it == GameDevice.Status.YourMove || it is GameDevice.Status.Stopped } }
            val d = sim.device(acting)
            val ds = d.model.engine
            val wanted = if (acting == alice) 1 else 2
            val input = when {
                ds.priority.holder == acting && ds.turn.active == acting && ds.stack.isEmpty() && ds.turn.step == TurnStep.PrecombatMain &&
                    ds.turn.number <= 2 && ds.battlefield.count { ds.obj(it)?.controller == acting } < wanted ->
                    Input.Cast(acting, hand(d.model, acting, if (acting == alice) hauler else pilot))
                ds.priority.holder == acting -> Input.PassPriority(acting)
                ds.turn.number == 3 && acting == alice -> Input.DeclareAttackers(alice, listOf(arcanum.engine.input.Attack(
                    d.model.named(alice, ds.battlefield, hauler).single(), arcanum.engine.state.TargetRef.Player(bob))))
                ds.turn.step == TurnStep.DeclareBlockers -> Input.DeclareBlockers(acting, emptyList())
                else -> Input.DeclareAttackers(acting, emptyList())
            }
            assertIs<GameDevice.Acted.Sent>(d.act(input), "$acting: $input")
            sim.settle()
        }
        assertEquals(1, s().combat?.attackers?.size, "Alice attacks")
        // Bob blocks the Hauler with both Pilots (his own input)
        val bobs = sim.device(bob)
        val attacker = bobs.model.engine.combat!!.attackers.single().id
        val blockers = bobs.model.named(bob, bobs.model.engine.battlefield, pilot)
        assertEquals(2, blockers.size)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.DeclareBlockers(bob, blockers.map { Block(it, attacker) })))
        sim.settle()
        val alices = sim.device(alice)
        assertEquals(TurnStep.DeclareBlockers, alices.model.engine.turn.step)
        assertEquals(alice, alices.model.engine.priority.holder, "Alice's priority after the blocks")
        // Bob is away from here on: Alice passes, and her phone waits for his pass with Assume (his Control stops there)
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.PassPriority(alice)))
        sim.settle(only = listOf(alice))
        assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(alices.status).offer, "waiting for Bob with Assume")
        assertIs<GameDevice.Acted.Sent>(alices.assume())
        sim.settle(only = listOf(alice))

        val assumed = sim.server.line().last { it.assumed?.assumedBy == alice && it.assumed?.input == Input.PassPriority(bob) }
        val division = assumed.engine!!.steps.mapNotNull { it.input as? Input.Answer }.single { it.answer is Answer.Division }
        assertEquals(listOf(2, 2), (division.answer as Answer.Division).shares.map { it.amount }, "lethal damage to each Pilot")
        assertEquals(null, assumed.engine!!.steps.single { it.input == division }.assumedBy, "her own automatic answer, not assumed")
        val server = sim.server.state()
        assertEquals(2, server.named(bob, server.engine.player(bob).graveyard, pilot).size, "both Pilots died: ${server.engine.turn}")
        assertEquals(1, server.named(alice, server.engine.player(alice).graveyard, hauler).size, "the Hauler died")
        assertTrue(server.engine.objects.values.none { it.zone == Zone.Battlefield && it.definition?.faces?.first()?.name == pilot })
        assertEquals(sim.server.head, alices.session.tip, "nothing of hers refused or taken back")
        assertTrue(alices.session.pending.isEmpty())
        assertTrue(sim.leaks.isEmpty(), "${sim.leaks}")
        assertEquals(emptyList(), sim.equivalence())
    }

    // JVPJ8Y (I-D56 §13): the gate's own rule, on the same board: the author's automatic answer folds into their assumed
    // event, any other answer of theirs to that decision doesn't (it is a real input: its own event)
    @Test
    fun onlyTheAutomaticDivisionFoldsIntoAnAssumedEvent() {
        val sim = DeviceSim(EngineGames(3, List(40) { hauler } to List(40) { pilot }))
        val alice = sim.alice; val bob = sim.bob
        sim.start(alice, mode = ResponseMode.FullControl, alwaysAssume = false)
        val server = sim.server
        server.keepAndAssumeKeep(alice)
        fun s() = server.state().engine
        // Alice's Hauler, Bob's two Pilots, Alice's attack and Bob's double block, all made on the server directly
        var guard = 0
        while (guard++ < 300) {
            val st = s()
            if (st.turn.number == 3 && st.turn.step == TurnStep.DeclareBlockers && st.priority.holder == alice && st.combat?.blockers?.isNotEmpty() == true) break
            val holder = st.priority.holder
            val active = st.turn.active
            val wanted = if (active == alice) 1 else 2
            val result = when {
                holder == active && st.stack.isEmpty() && st.turn.step == TurnStep.PrecombatMain && st.turn.number <= 2 &&
                    st.battlefield.count { st.obj(it)?.controller == active } < wanted ->
                    server.act(active, Input.Cast(active, hand(server.state(), active, if (active == alice) hauler else pilot)))
                holder != null -> server.act(holder, Input.PassPriority(holder))
                st.turn.step == TurnStep.DeclareAttackers -> server.act(active, Input.DeclareAttackers(active,
                    if (st.turn.number == 3) listOf(arcanum.engine.input.Attack(server.state().named(alice, st.battlefield, hauler).single(),
                        arcanum.engine.state.TargetRef.Player(bob))) else emptyList()))
                else -> server.act(bob, Input.DeclareBlockers(bob, if (st.turn.number == 3) server.state().named(bob, st.battlefield, pilot)
                    .map { Block(it, st.combat!!.attackers.single().id) } else emptyList()))
            }
            result.getOrThrow()
        }
        server.act(alice, Input.PassPriority(alice)).getOrThrow()
        server.seen(alice, server.head)
        // Bob's pass assumed by Alice, then the division she has to make
        val model = server.state()
        val builder = EventBuilder(model, "probe", server.head, alice.value)
        builder.perform(Input.PassPriority(bob), assumedBy = alice)
        builder.answerRandoms()
        val divide = assertIs<arcanum.engine.decision.Decision.Divide>(model.engine.pending?.decision, "her division is open")
        assertEquals(alice, divide.player)
        val automatic = WhoActs.automaticAnswer(model, divide)!!
        val lopsided = Input.Answer(divide.id, Answer.Division(listOf(
            divide.suggested[0].copy(amount = 3), divide.suggested[1].copy(amount = 1))))
        builder.abandon()
        fun submission(id: String, answer: Input.Answer) = Submission(id, server.head,
            listOf(Act.Perform(Input.PassPriority(bob), assumed = true), Act.Perform(answer)))
        val refused = kotlin.test.assertFailsWith<Refusal> { server.submit(submission("lopsided", lopsided), alice).getOrThrow() }
        assertTrue("never share an event" in refused.reason, refused.reason)
        assertTrue(server.submit(submission("automatic", automatic), alice).isSuccess, "the automatic division folds in")
    }
}
