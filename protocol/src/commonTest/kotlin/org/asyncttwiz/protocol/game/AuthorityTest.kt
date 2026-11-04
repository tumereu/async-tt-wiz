package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * I-D54 phases 3–4 and I-D56 step A through two devices and the server's rules (protocol level): devices make their
 * events on the states they show and the server makes the same events (the engine is deterministic and random answers
 * come from the line's stream); a player acts only for themselves, assumes only the other player's assumable inputs,
 * and the other player's real answer confirms the assumption or branches the game; the information rule, local heads,
 * next stops, undo and rewinds.
 */
class AuthorityTest {
    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    private fun keep(server: AsyncServer) = Input.Answer(server.pending()!!.id, Answer.Option("Keep"))

    /** [me] plays on to the point [done] says, passing their own priority and assuming the other player's passes. */
    private fun AsyncServer.playOn(me: PlayerId, done: (GameModel) -> Boolean) {
        repeat(40) {
            val s = state()
            if (done(s)) return
            check(s.engine.pending == null) { "a decision is open: ${s.engine.pending}" }
            val holder = s.engine.priority.holder!!
            act(me, Input.PassPriority(holder), assumed = holder != me).getOrThrow()
        }
        error("not reached")
    }

    private fun AsyncServer.atOwnMain(me: PlayerId) = playOn(me) {
        it.engine.turn.active == me && it.engine.turn.step == TurnStep.PrecombatMain && it.engine.priority.holder == me && it.engine.stack.isEmpty()
    }

    /** Bob's deck of Islands and [spell], Alice's of Forests: Alice has nothing she could cast. */
    private fun spells(spell: String) = AsyncServer(EngineGames(3, List(40) { "Forest" } to (List(20) { "Island" } + List(20) { spell })))

    /** Bob starts, keeps, assumes Alice's keep, plays an Island and casts [spell] (targeting Alice when it targets). */
    private fun AsyncServer.bobCasts(spell: String) {
        start(bob)
        keepAndAssumeKeep(bob)
        atOwnMain(bob)
        act(bob, Input.PlayLand(bob, hand(state(), bob, "Island"))).getOrThrow()
        act(bob, Input.Cast(bob, hand(state(), bob, spell)), follow = flow(bob) { d, answers ->
            if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
        }).getOrThrow()
        if (state().engine.priority.holder == bob) act(bob, Input.PassPriority(bob)).getOrThrow()
        check(state().engine.priority.holder == alice && state().engine.stack.isNotEmpty())
    }

    @Test
    fun aDevicesEventIsTheServersEvent() {
        val server = AsyncServer(EngineGames(1))
        server.start()
        // Bob declares first (simultaneous mulligans): his own, then Alice's own; each event is the device's
        server.act(server.bob, Input.SetupAnswer(server.bob, Answer.Option("Keep"))).getOrThrow()
        server.act(server.alice, keep(server)).getOrThrow()
        assertEquals(1, server.state().engine.turn.number)
        // The random answers were the line's: the game's start used line 0's stream, nothing else
        assertEquals(0, server.state().table.line.number)
        assertTrue(server.state().table.line.drawn > 0)
    }

    @Test
    fun aStaleSubmissionIsRefused() {
        val server = AsyncServer(EngineGames(2))
        server.start()
        val head = server.head
        val (alices, _) = server.make(server.state(), head, server.alice, keep(server))
        val (bobs, _) = server.make(server.state(), head, server.bob, Input.SetupAnswer(server.bob, Answer.Option("Keep")))
        server.submit(alices, server.alice).getOrThrow()
        assertEquals("stale", server.submit(bobs, server.bob).exceptionOrNull()?.message)
    }

    // I-D56 §6: nobody decides for the other player; random answers are the line's (§9)
    @Test
    fun onlyTheDeciderMakesTheirInputs() {
        val server = AsyncServer(EngineGames(3))
        server.start()
        val alice = server.alice; val bob = server.bob
        // Alice's declaration is open: Bob may not answer it, nor concede or suggest for her
        assertEquals("Only alice decides this (I-D56 §6)", server.act(bob, keep(server)).exceptionOrNull()?.message)
        assertTrue(server.act(alice, Input.Concede(bob)).isFailure)
        assertTrue(server.act(alice, Input.AgreedResult(GameResult.Draw)).isFailure)
        assertTrue(server.submit(Submission("x", server.head, table = listOf(TableChange.LineMoved(Line(), Line(1)))), alice).isFailure)
        assertTrue(Authority.mayMake(server.state().engine, keep(server), alice))
        assertTrue(!Authority.mayMake(server.state().engine, keep(server), bob))
        // A mulligan shuffles (once both declared): a shuffle that isn't the line's next random answer is refused, the
        // line's is taken
        server.act(bob, Input.SetupAnswer(bob, Answer.Option("Keep"))).getOrThrow()
        val mulligan = keep(server).copy(answer = Answer.Option("Mulligan"))
        val model = server.state()
        val builder = EventBuilder(model, "probe", server.head, alice.value)
        builder.perform(mulligan)
        val d = model.engine.pending!!.decision as Decision.Random
        val right = builder.randomAnswer(d) as Answer.RandomObjects
        builder.abandon()
        val wrong = right.copy(chosen = right.chosen.reversed())
        val shuffled = { answer: Answer, id: String -> Submission(id, server.head, listOf(Act.Perform(mulligan), Act.Perform(Input.Answer(d.id, answer)))) }
        assertEquals("A random answer that isn't the line's (I-D56 §9)", server.submit(shuffled(wrong, "w"), alice).exceptionOrNull()?.message)
        assertTrue(server.submit(shuffled(right, "r"), alice).isSuccess)
    }

    // I-D56 §3, §8: the opening keep assumed by the starting player; the same real answer confirms it
    @Test
    fun anAssumedKeepIsConfirmedByTheSameAnswer() {
        val server = AsyncServer(EngineGames(4))
        server.start()
        val alice = server.alice; val bob = server.bob
        server.act(alice, keep(server)).getOrThrow()
        val aliceKeep = server.head
        // Only the starting player assumes, only after their own keep: Bob's assumed keep of Alice was refused before
        server.act(alice, keep(server), assumed = true).getOrThrow()
        val assumed = server.head
        assertEquals(alice, server.line().last().assumed?.assumedBy)
        // Bob's playback stops at the assumed keep, whatever his mode (assumed keeps always stop, §7)
        for (mode in listOf(ResponseMode.AutoSkip, ResponseMode.Normal, ResponseMode.FullControl))
            assertEquals(NextStop.Stop(aliceKeep, assumed), server.nextStop(bob, mode))
        // He answers at his local head (the start: his submission moves it to Alice's keep first)
        val open = server.stateAt(aliceKeep).engine.pending!!.decision
        val (same, _) = server.make(server.stateAt(aliceKeep), aliceKeep, bob, Input.Answer(open.id, Answer.Option("Keep")))
        val confirmed = server.submit(same, bob).getOrThrow()
        assertIs<Acceptance.Confirmed>(confirmed)
        assertEquals(assumed, confirmed.event)
        assertEquals(assumed, server.heads[bob])
        assertEquals(assumed, server.head)
    }

    // I-D56 §8, §9: another answer is a branch: a new line from the counter, reshuffled; the old line is discarded
    @Test
    fun anotherAnswerBranchesAndTheDiscardedLineIsShownToNobody() {
        val server = AsyncServer(EngineGames(5))
        server.start()
        val alice = server.alice; val bob = server.bob
        server.act(alice, keep(server)).getOrThrow()
        val aliceKeep = server.head
        server.act(alice, keep(server), assumed = true).getOrThrow()
        val assumed = server.head
        // Alice plays on in her turn, assuming Bob's passes (not at his stops)
        server.playOn(alice) { it.engine.turn.step == TurnStep.PrecombatMain }
        val oldLine = server.line().map { it.id }
        assertEquals(server.head, server.heads[alice])
        // Bob mulligans instead: without a new line it's refused; with the next line it branches at his local head
        val at = server.stateAt(aliceKeep)
        val open = at.engine.pending!!.decision
        val (plain, _) = server.make(server.stateAt(aliceKeep), aliceKeep, bob, Input.Answer(open.id, Answer.Option("Mulligan")))
        assertEquals("newLine", server.submit(plain, bob).exceptionOrNull()?.message)
        val (branch, made) = server.make(server.stateAt(aliceKeep), aliceKeep, bob, Input.Answer(open.id, Answer.Option("Mulligan")), newLine = 1)
        val accepted = assertIs<Acceptance.Accepted>(server.submit(branch, bob).getOrThrow())
        assertEquals(made, accepted.event)
        assertEquals(assumed, accepted.discarded)
        assertEquals(1, accepted.event.startsLine)
        // The new line's first step reshuffled the libraries' unknown parts; nothing known moved (all unknown here)
        val reseed = accepted.event.engine!!.steps.first()
        assertNull(reseed.input)
        assertTrue(reseed.changes.all { it is arcanum.engine.change.Change.Reorder })
        assertNotEquals(at.engine.player(alice).library, server.state().engine.player(alice).library)
        // The discarded line: kept in storage, not on the main line; Alice's local head fell back to the branch point
        assertEquals(listOf("start", aliceKeep, accepted.event.id), server.line().map { it.id })
        assertTrue(oldLine.drop(2).all { server.store.node(it) != null && it !in server.line().map { e -> e.id } })
        assertEquals(aliceKeep, server.heads[alice])
        // Her inputs on the old line are stale now
        val (late, _) = server.make(server.stateAt(oldLine.last()), oldLine.last(), alice, Input.PassPriority(alice))
        assertEquals("stale", server.submit(late, alice).exceptionOrNull()?.message)
        // The next new line takes the counter's next number, never the parent's again (§9)
        assertEquals(1, server.lines)
        assertEquals("stale", runCatching {
            Authority.make(server.state(), Submission("n", server.head, listOf(Act.NewLine(1))), bob, Authority.Rules(nextLine = 2))
        }.exceptionOrNull()?.message)
    }

    // I-D56 §9: the k-th random answer of line n is H(secret, n, k): the same on a line, new on another
    @Test
    fun randomAnswersAreDeterministicPerLine() {
        val secret = LineRandom.secret(ByteArray(32) { it.toByte() })
        val kind = arcanum.engine.decision.RandomKind.Objects((1..30).map { ObjectId.Card(arcanum.engine.state.CardId("c$it"), 0) }, 30)
        assertEquals(LineRandom.answer(secret, 2, 5, kind), LineRandom.answer(secret, 2, 5, kind))
        assertNotEquals(LineRandom.answer(secret, 2, 5, kind), LineRandom.answer(secret, 3, 5, kind))
        assertNotEquals(LineRandom.answer(secret, 2, 5, kind), LineRandom.answer(secret, 2, 6, kind))
        assertNotEquals(LineRandom.answer(secret, 2, 5, kind), LineRandom.answer(LineRandom.secret(ByteArray(32)), 2, 5, kind))
    }

    // I-D56 §3: only the assumable kinds, only where the other player decides, never at their stop
    @Test
    fun onlyTheAssumableKindsAreAssumed() {
        val server = AsyncServer(EngineGames(6))
        server.start()
        val alice = server.alice; val bob = server.bob
        // Bob isn't the starting player: he can't assume Alice's keep; Alice can't assume before her own keep
        assertTrue(server.act(bob, keep(server), assumed = true).isFailure)
        server.act(bob, Input.SetupAnswer(bob, Answer.Option("Keep"))).getOrThrow()
        assertTrue(server.act(alice, keep(server), assumed = true).isFailure, "her own input isn't assumed")
        server.act(alice, keep(server)).getOrThrow()
        server.playOn(alice) { it.engine.turn.step == TurnStep.PrecombatMain }
        // In Alice's main phase she can't pass for Bob, nor assume anything of his but a pass at his priority
        assertTrue(server.act(alice, Input.PassPriority(bob)).isFailure)
        assertTrue(server.act(alice, Input.PassPriority(bob), assumed = true).isFailure)
        // Into Bob's turn: Alice may assume his passes in his upkeep and draw step, never at his main phase (his stop)
        server.playOn(alice) { it.engine.turn.active == bob && it.engine.turn.step == TurnStep.PrecombatMain }
        assertEquals(bob, server.state().engine.priority.holder)
        val refused = server.act(alice, Input.PassPriority(bob), assumed = true).exceptionOrNull()?.message
        assertEquals("Passing at their own main phase is skipping their turn, not assumed (I-D56 §3)", refused)
    }

    // I-D56 §5: while the line is tentative, an outcome that would show a hidden card is refused; the real pass isn't
    @Test
    fun theInformationRuleStopsALookAtTheOtherPlayersHand() {
        val server = spells("Cerebral Scan")
        val alice = server.alice; val bob = server.bob
        server.bobCasts("Cerebral Scan")
        // Assuming Alice's pass would resolve "look at target player's hand": refused
        val stop = server.act(bob, Input.PassPriority(alice), assumed = true)
        assertEquals(Authority.REVEALS_HIDDEN, stop.exceptionOrNull()?.message)
        val waiting = server.head
        // Alice plays back: her playback stops at the assumed keep (always), confirms it by keeping
        val keepAt = server.nextStop(alice)!!
        assertNotNull(keepAt.event)
        val open = server.stateAt(keepAt.at).engine.pending!!.decision
        val (keeps, _) = server.make(server.stateAt(keepAt.at), keepAt.at, alice, Input.Answer(open.id, Answer.Option("Keep")))
        assertIs<Acceptance.Confirmed>(server.submit(keeps, alice).getOrThrow())
        // Bob's assumed upkeep passes aren't her pauses: Auto-skip plays on to the real wait at the head
        assertEquals(NextStop.Stop(waiting, null), server.nextStop(alice))
        server.seen(alice, waiting)
        // Her own pass lets the spell resolve: Bob looks at her hand as the rules allow
        val accepted = assertIs<Acceptance.Accepted>(server.act(alice, Input.PassPriority(alice)).getOrThrow())
        assertTrue(Engine.revealed(server.stateAt(waiting).engine, accepted.event.engine!!.changes, bob).isNotEmpty())
    }

    // I-D56 §7: playback stops by the player's private response mode
    @Test
    fun nextStopsFollowTheResponseMode() {
        val server = spells("Glance Ahead")
        val alice = server.alice; val bob = server.bob
        server.bobCasts("Glance Ahead")
        // Bob's own library: the scry in passing doesn't stop him
        server.act(bob, Input.PassPriority(alice), assumed = true).getOrThrow()
        val events = server.line()
        val pauseAt = events.last().parentId
        server.seen(alice, events.first { it.assumed?.input is Input.Answer }.id)
        // Full control stops at Bob's first assumed upkeep pass; Normal only at the pause (Bob's spell on top);
        // Auto-skip not even there: Alice holds only Forests, so she couldn't respond (judged with her own cards)
        val first = events.first { it.assumed?.input is Input.PassPriority }
        assertEquals(first.id, server.nextStop(alice, ResponseMode.FullControl)?.event)
        assertEquals(NextStop.Stop(pauseAt, events.last().id), server.nextStop(alice, ResponseMode.Normal))
        val auto = server.nextStop(alice, ResponseMode.AutoSkip)
        assertTrue(auto == null || auto.event == null, "no stop at an assumed pass she couldn't respond to: $auto")
    }

    // I-D56 §10: undo is free while everything after the target is your own and showed you nothing new
    @Test
    fun undoIsFreeUntilSomethingNewWasShown() {
        val server = spells("Glance Ahead")
        val alice = server.alice; val bob = server.bob
        server.start(bob)
        server.keepAndAssumeKeep(bob)
        server.atOwnMain(bob)
        val beforeLand = server.head
        server.act(bob, Input.PlayLand(bob, hand(server.state(), bob, "Island"))).getOrThrow()
        assertNull(server.rewindNeeds(bob, beforeLand))
        val library = server.state().engine.player(bob).library
        // The rewind only pops the land's event (owner's rule 2026-11-04): it adds none of its own; a new line is due
        assertEquals(emptyList(), server.rewind(bob, beforeLand))
        assertEquals(beforeLand, server.head)
        assertEquals(beforeLand, server.heads[bob])
        assertEquals(library, server.state().engine.player(bob).library)
        assertEquals(1, server.lines)
        // The next event there starts it: its first step reshuffled Bob's library's unknown part
        server.act(bob, Input.PlayLand(bob, hand(server.state(), bob, "Island"))).getOrThrow()
        assertEquals(1, server.history.store.node(server.head)!!.event.startsLine)
        assertNotEquals(library, server.state().engine.player(bob).library)
        assertEquals(library.toSet(), server.state().engine.player(bob).library.toSet())
        // Cast Glance Ahead, assume Alice's pass, scry the top card to the bottom, draw
        val beforeCast = server.head
        server.act(bob, Input.Cast(bob, hand(server.state(), bob, "Glance Ahead")), follow = server.flow(bob)).getOrThrow()
        if (server.state().engine.priority.holder == bob) server.act(bob, Input.PassPriority(bob)).getOrThrow()
        server.act(bob, Input.PassPriority(alice), assumed = true).getOrThrow()
        val scry = server.pending() as Decision.ChooseObjects
        server.act(bob, Input.Answer(scry.id, Answer.Objects(scry.options))).getOrThrow()
        val bottom = server.state().engine.player(bob).library.last()
        assertEquals(scry.options.single().let { (it as ObjectId.Card).card }, (bottom as ObjectId.Card).card)
        // Undoing past the look and the draw needs Alice's approval (your own library counts here)
        assertEquals("shown", server.rewindNeeds(bob, beforeCast))
        // After the draw, Bob passes (nothing new shown): free; the rewind's reshuffle keeps the scried card at the bottom
        val afterDraw = server.head
        server.act(bob, Input.PassPriority(bob)).getOrThrow()
        assertNull(server.rewindNeeds(bob, afterDraw))
        server.rewind(bob, afterDraw)
        assertEquals(2, server.lines)
        server.act(bob, Input.PassPriority(bob)).getOrThrow()
        assertEquals(2, server.history.store.node(server.head)!!.event.startsLine)
        assertEquals(bottom, server.state().engine.player(bob).library.last())
        val places = server.state().engine.player(bob).let { p -> p.libraryPlaces.filterKeys { it in p.library } }
        assertEquals(mapOf<ObjectId, Int>(bottom to 0), places)
        // Once Alice confirmed Bob's assumption, undoing past it is a request too
        server.seen(alice, server.head)
        assertEquals("confirmed", server.rewindNeeds(bob, beforeCast))
    }

    // I-D56 §11: conceding while behind is the conceder's real input at their local head: a branch
    @Test
    fun aConcessionWhileBehindBranches() {
        val server = AsyncServer(EngineGames(7))
        server.start()
        val alice = server.alice; val bob = server.bob
        server.act(alice, keep(server)).getOrThrow()
        server.act(alice, keep(server), assumed = true).getOrThrow()
        server.playOn(alice) { it.engine.turn.step == TurnStep.PrecombatMain }
        val (concede, _) = server.make(server.stateAt("start"), "start", bob, Input.Concede(bob), newLine = server.lines + 1)
        val accepted = assertIs<Acceptance.Accepted>(server.submit(concede, bob).getOrThrow())
        assertEquals("start", accepted.event.parentId)
        assertEquals(GameResult.Won(alice), Engine.result(server.state().engine))
    }

    // I-D56 §11: the result is confirmed only once both players' local heads reached it
    @Test
    fun aResultIsConfirmedOnceBothHaveSeenIt() {
        val server = AsyncServer(EngineGames(8))
        server.start()
        val alice = server.alice; val bob = server.bob
        server.act(alice, Input.Concede(alice)).getOrThrow()
        val confirm = Submission("c1", server.head, table = listOf(TableChange.ResultConfirmed(null, alice)))
        // Bob hasn't seen the concession yet
        assertTrue(server.submit(confirm, alice).isFailure)
        server.seen(bob, server.head)
        assertTrue(server.submit(confirm.copy(id = "c2"), alice).isSuccess)
    }

    @Test
    fun aSuggestedResultIsAgreedByTheOtherPlayer() {
        val server = AsyncServer(EngineGames(9))
        server.start()
        val alice = server.alice; val bob = server.bob
        val suggestion = ResultSuggestion(alice, GameResult.Draw)
        server.submit(Submission("r1", server.head, table = listOf(TableChange.Suggestion(null, suggestion))), alice).getOrThrow()
        val agree = listOf(Act.Perform(Input.AgreedResult(GameResult.Draw)))
        val clear = listOf(TableChange.Suggestion(suggestion, null))
        assertTrue(server.submit(Submission("r2", server.head, agree, clear), alice).isFailure)
        server.seen(alice, server.head)
        server.submit(Submission("r3", server.head, agree, clear + TableChange.ResultConfirmed(null, bob)), bob).getOrThrow()
        assertEquals(GameResult.Draw, Engine.result(server.state().engine))
    }

    // Seeded games where each device makes only its own inputs (and the engine-automatic passes any device may make):
    // every accepted event is the one the device made, and the line stays one (equivalence with synchronous play)
    @Test
    fun devicesAndTheServerStayInStep() {
        for (seed in 1L..3L) {
            val server = AsyncServer(EngineGames(seed))
            server.start()
            val rnd = Random(seed)
            var events = 0
            while (events < 120 && Engine.result(server.state().engine) == null) {
                val state = server.state().engine
                val me = Attention.of(state) ?: break
                val listed = Engine.options(state, me).filter { it !is Input.Concede && Authority.mayMake(state, it, me) }
                val input = listed.randomOrNull(rnd) ?: break
                server.act(me, input).getOrThrow()
                server.heads.keys.toList().forEach { server.seen(it, server.head) }
                events++
            }
            assertTrue(events > 50, "seed $seed: $events events")
            assertEquals(0, server.lines)
        }
    }
}
