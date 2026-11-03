package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Adversarial review of I-D54 + I-D56 steps A and B (2026-11-04, `~/projects/atw-cache/ab-review-2026-11-04.md`):
 * each finding's reproduction, ignored with its number until fixed. Expected behaviour is the spec's (or the plainest
 * reading where the spec is silent); the report says why.
 */
class AbReviewTest {
    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Bob's deck of [land]s and [spell]s, Alice's of Forests (she can cast nothing). */
    private fun sim(spell: String, land: String = "Island") =
        DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { land } + List(20) { spell })))

    private fun DeviceSim.atMain(p: PlayerId) = device(p).model.engine.let {
        it.turn.active == p && it.turn.step == TurnStep.PrecombatMain && it.stack.isEmpty() && it.priority.holder == p
    }

    /** Bob starts and keeps; Alice is away, so his device assumes her keep and her passes up to his main phase. */
    private fun DeviceSim.bobToMain() {
        start(bob)
        assertIs<GameDevice.Acted.Sent>(device(bob).act(keep(bob)))
        settle()
        assertTrue(atMain(bob), "Bob is at his main phase")
    }

    /** [p] comes back and plays back to the head, confirming each assumption their playback stops at. */
    private fun DeviceSim.catchUp(p: PlayerId): GameDevice {
        val d = load(p)
        d.play()
        while (d.session.behind) {
            d.playToStop()
            (d.status as? GameDevice.Status.Stopped)?.let { d.act(it.assumed) }
        }
        settle()
        return d
    }

    /** The flow answers for [p]: targets at [target] when asked, else the first answer listed. */
    private fun answers(p: PlayerId, target: PlayerId): (GameModel) -> Input? = { m ->
        val d = m.engine.pending?.decision
        if (d == null || d is Decision.Random || d.player != p) null
        else Engine.options(m.engine, p).filterIsInstance<Input.Answer>().filter { it.decision == d.id }.let { listed ->
            if (d is Decision.Targets) listed.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(target)) } else listed.first()
        }
    }

    // abreview #1 (I-D54 §3, I-D56 §6): a table-only event of the other player (placing their cards, an early answer)
    // decides nothing and never confirms or branches; an engine input made on the event before it must not be refused
    // as stale because of it, or every card the other player drags cancels what you were doing in live play
    @Ignore("abreview #1: an engine input made on the event before the other player's table-only event is refused as stale")
    @Test
    fun theOtherPlayersTableOnlyEventDoesNotMakeAnInputStale() {
        val server = AsyncServer(EngineGames(1))
        server.start()
        val alice = server.alice; val bob = server.bob
        val head = server.head
        // Bob declares his keep early (simultaneous mulligans) on the start event...
        val (bobs, _) = server.make(server.state(), head, bob, Input.SetupAnswer(bob, Answer.Option("Keep")))
        // ...while Alice's early answer (a table-only event of hers) reaches the server first
        val early = EventBuilder(server.state(), "early", head, alice.value).also {
            it.table(TableChange.EarlyAnswered("k", null, EarlyAnswer(alice, "k", Answer.YesNo(true))))
        }.build()
        server.submit(Submission.of(early), alice).getOrThrow()
        val result = server.submit(bobs, bob)
        assertTrue(result.isSuccess, "refused: ${result.exceptionOrNull()?.message}")
    }

    // abreview #2 (I-D56 §6, §19.3): Bob is casting (a draft open) when Alice's table-only event arrives; the session
    // drops the draft silently while GameDevice keeps it as open, so the flow goes on outside any draft and send() fails
    @Ignore("abreview #2: GameSession drops an open draft on another event at the head; GameDevice's draft is left dangling")
    @Test
    fun aDraftSurvivesTheOtherPlayersTableOnlyEvent() {
        val sim = sim("Thought Siphon")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.catchUp(alice)
        val bobs = sim.device(bob)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island"))))
        sim.settle()
        assertEquals(sim.server.head, alices.session.localHead)
        // Bob starts casting Thought Siphon at Alice (a target to choose)
        val draft = assertNotNull(bobs.begin())
        draft.perform(Input.Cast(bob, hand(bobs.model, bob, "Thought Siphon")))
        // Meanwhile Alice gives an early answer (table-only, at the head), which reaches Bob mid-flow
        assertIs<GameDevice.Acted.Sent>(alices.earlyAnswer("k", EarlyAnswer(alice, "k", Answer.YesNo(true))))
        sim.flush(alice)
        sim.deliver(bob)
        // Bob finishes his flow and sends it
        val flow = answers(bob, alice)
        while (true) draft.perform(flow(bobs.model) ?: break)
        val sent = bobs.send()
        sim.settle()
        assertIs<GameDevice.Acted.Sent>(sent)
        assertTrue(bobs.session.pending.isEmpty() && bobs.model.sameAs(sim.server.stateAt(bobs.session.localHead)),
            "Bob's device shows the server's state")
        assertTrue(sim.server.line().any { e -> e.engine?.steps.orEmpty().any { it.input is Input.Cast } }, "the cast was taken")
    }

    // abreview #3 (I-D56 §8, §19.2): at a decision stop Alice answers with a draft whose first input is the assumed
    // pass and that goes on with her next decision (the discard the resolution asks); the server confirms (it compares
    // only the first input and drops the rest) while her device made a branch: her device waits forever for its branch
    // and ignores the line the server goes on with
    @Ignore("abreview #3: device branches where the server confirms (same first input, more inputs): the device is stuck")
    @Test
    fun aDraftStartingWithTheAssumedInputAgreesWithTheServer() {
        val sim = sim("Crow's Larceny", land = "Swamp")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val bobs = sim.device(bob)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Swamp"))))
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, "Crow's Larceny")), follow = answers(bob, alice)))
        sim.settle()
        assertEquals(alice, sim.server.state().engine.pending?.decision?.player, "Alice's pass was assumed: her discard waits")
        // Alice comes back with Normal: playback stops at the assumed keep, then at the assumed pass (her pause)
        val alices = sim.load(alice)
        alices.settings(PlaySettings(mode = ResponseMode.Normal))
        alices.play()
        var stop: GameDevice.Status.Stopped
        while (true) {
            alices.playToStop()
            stop = assertIs(alices.status)
            if (stop.assumed is Input.PassPriority) break
            alices.act(stop.assumed)
        }
        // Her answer as one flow: the same pass, then the discard it leads to
        val draft = assertNotNull(alices.begin())
        draft.perform(Input.PassPriority(alice))
        val discard = assertNotNull(answers(alice, bob)(alices.model))
        draft.perform(discard)
        alices.send()
        sim.settle()
        assertTrue(alices.session.pending.isEmpty(), "her device agrees with the server about what her answer did")
        assertEquals(sim.server.head, alices.session.head)
        assertTrue(alices.model.sameAs(sim.server.stateAt(alices.session.localHead)))
    }

    // abreview #4 (I-D56 §11, CR 104.3a): conceding is taken at any time; a concession made at the device's head after
    // the server's head moved on (an event still on the wire) is refused as "newLine" and lost
    @Ignore("abreview #4: a concession crossing another event on the wire is refused (newLine) and dropped")
    @Test
    fun aConcessionCrossingAnEventIsStillTaken() {
        val sim = sim("Thought Siphon")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.catchUp(alice)
        val bobs = sim.device(bob)
        // Bob plays an Island; it reaches the server but not yet Alice's device
        bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island")))
        sim.flush(bob)
        assertIs<GameDevice.Acted.Sent>(alices.concede())
        sim.flush(alice)
        sim.settle()
        assertEquals(GameResult.Won(bob), Engine.result(sim.server.state().engine), "Alice's concession was taken; her notes: ${alices.notes()}")
    }

    // abreview #5 (I-D56 §9, §10, §18.6): a random result seen makes undo a request (no re-roll); Rewind misses one in
    // an event that starts a line (a branch at a decision stop whose outcome flips a coin with forced passes folded:
    // both players with empty hands), so undoing it is free and the new line flips again
    @Ignore("abreview #5: Rewind.drewRandom ignores random answers in an event that starts a line: a free re-roll")
    @Test
    fun aCoinFlipInALineStartingEventMakesUndoARequest() {
        val toss = Cards.invent("Review Toss", "{U}", "Instant", "Flip a coin. If you win the flip, you gain 6 life.")
        val server = AsyncServer(EngineGames(4, List(40) { "Forest" } to (List(20) { "Island" } + List(20) { toss })))
        val alice = server.alice; val bob = server.bob
        server.start(bob)
        server.keepAndAssumeKeep(bob)
        repeat(40) {
            val s = server.state().engine
            if (s.turn.active == bob && s.turn.step == TurnStep.PrecombatMain && s.priority.holder == bob && s.stack.isEmpty()) return@repeat
            val holder = s.priority.holder!!
            server.act(bob, Input.PassPriority(holder), assumed = holder != bob).getOrThrow()
        }
        server.act(bob, Input.PlayLand(bob, hand(server.state(), bob, "Island"))).getOrThrow()
        server.act(bob, Input.Cast(bob, hand(server.state(), bob, toss)), follow = server.flow(bob)).getOrThrow()
        val target = server.head
        // The event's shape: a new line's reshuffle, Bob's pass, the other player's forced pass folded, the flip
        fun flipEvent(newLine: Boolean): GameEvent {
            val model = server.stateAt(target)
            val builder = EventBuilder(model, "flip", target, bob.value)
            if (newLine) builder.startLine(server.lines + 1)
            builder.perform(Input.PassPriority(bob))
            builder.perform(Input.PassPriority(alice))
            builder.answerRandoms()
            return builder.build()
        }
        val plain = flipEvent(newLine = false)
        assertEquals("random", Rewind.requestNeeded(server.stateAt(target), target, listOf(plain), bob, listOf(target)),
            "the same event without a line start is a request")
        val branching = flipEvent(newLine = true)
        assertTrue(branching.startsLine != null)
        assertEquals("random", Rewind.requestNeeded(server.stateAt(target), target, listOf(branching), bob, listOf(target)))
    }

    // Coverage (review scope: results rarely reached in the simulations): a lethal attack on a tentative line, and the
    // defender concedes while behind (I-D56 §11): the line beyond his local head is discarded, the result is his
    // concession, final at once, and the attacker's "win" on the discarded line is taken back
    @Test
    fun aConcessionWhileBehindDiscardsATentativeWin() {
        val sim = DeviceSim(EngineGames(5, List(40) { if (it % 2 == 0) "Forest" else "Elvish Seer" }.let { it to it }, life = 1))
        val alice = sim.alice; val bob = sim.bob
        sim.start(alice, ResponseMode.Normal)
        for (p in listOf(alice, bob)) { sim.device(p).act(sim.keep(p)); sim.settle() }
        for (p in listOf(alice, bob)) {
            sim.catchUp(p)
            val d = sim.device(p)
            d.act(Input.PlayLand(p, hand(d.model, p, "Forest")))
            d.act(Input.Cast(p, hand(d.model, p, "Elvish Seer")), follow = answers(p, sim.other(p)))
            sim.settle()
            d.endTurn(sim.now)
            sim.settle()
        }
        sim.catchUp(alice)
        val alices = sim.device(alice)
        val m = alices.model.engine
        val seer = m.battlefield.first { m.obj(it)?.controller == alice && m.obj(it)?.definition?.faces?.first()?.name == "Elvish Seer" }
        alices.act(Input.PassPriority(alice))
        sim.settle(only = listOf(alice))
        alices.act(Input.DeclareAttackers(alice, listOf(arcanum.engine.input.Attack(seer, TargetRef.Player(bob)))))
        sim.settle(only = listOf(alice))
        assertIs<GameDevice.Acted.Sent>(alices.assume())
        sim.settle(only = listOf(alice))
        while (alices.status == GameDevice.Status.YourMove && alices.model.engine.priority.holder == alice) {
            alices.act(Input.PassPriority(alice)); sim.settle(only = listOf(alice))
        }
        assertIs<GameDevice.Status.Over>(alices.status)
        // Bob loads the game behind the attack and concedes at once
        val bobs = sim.load(bob)
        assertTrue(bobs.session.behind)
        assertIs<GameDevice.Acted.Sent>(bobs.concede())
        sim.settle()
        assertEquals(GameResult.Won(alice), Engine.result(sim.server.state().engine))
        assertTrue(sim.server.line().none { it.assumed?.input is Input.DeclareBlockers }, "the assumed blocks are off the main line")
        val over = assertIs<GameDevice.Status.Over>(alices.status)
        assertTrue(over.final)
        assertTrue(sim.leaks.isEmpty())
    }
}
