package org.asyncttwiz.server.game

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.GameResult as EngineResult
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.Act
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.GameSession
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.asyncttwiz.protocol.game.Submission
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * I-D54 phase 3 and I-D56 step A through the game service: a play started as engine state (the test seam [engineSeats],
 * or the decks parsed on the server) takes [GameAction.Submit]s in arrival order, refuses a stale
 * parent and an impossible input, stores and broadcasts each event, confirms an assumed input by the same real answer
 * and branches on another, keeps each player's local head and next stop (whose move it is, per viewer), rewinds (free,
 * or by an approved undo request), keeps play settings private, ends the play once its result is final, and serves its
 * state at the main line's head, also after a restart (cold: checkpoint plus events).
 */
class EngineGameServiceTest {
    private fun id() = UUID.randomUUID().toString()
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime); accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun command(action: GameAction): GameCommand {
            val scope = if (action is GameAction.Submit || action.isEngine) GameScope(game.id, playId = games.head(actor, game.id).summary.playId)
                else GameScope(game.id, game.preparationRound, if (action == GameAction.AcceptInvitation) game.invitationId else null)
            return GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
        }
        fun admit(c: GameCommand) = games.admit(actor, hello, connection, c)
        fun result(c: GameCommand) = games.results(actor, game.id, listOf(c.requestId)).statuses.single().result!!
        fun execute(c: GameCommand): GameResult { admit(c); assertTrue(games.executeOne()); return result(c) }
        fun execute(a: GameAction) = execute(command(a))
    }

    private val names = List(16) { if (it % 2 == 0) "Forest" else "Mountain" } +
        List(24) { listOf("Elvish Seer", "Storm Spark", "Thicketwood Elves")[it % 3] }

    private fun <T> withService(test: (ServiceRuntime) -> T): T = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(runtime)
        }
    }

    /** A started game; its play is engine state when [engine] (seats from the seam, or the decks with [decks]). */
    private inner class Fixture(runtime: ServiceRuntime, engine: Boolean, decks: Boolean = false, tag: String = "",
        bobDefaults: PlaySettings? = null) {
        val a = account(runtime, "EngineA$tag${if (engine) "" else "x"}"); val b = account(runtime, "EngineB$tag${if (engine) "" else "x"}")
        val games = GameService(runtime)
        val game: GameSummary
        val owner: Stream; val recipient: Stream
        init {
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Engine Spark")).body)
            owner = Stream(games, a, game); recipient = Stream(games, b, game)
            recipient.execute(GameAction.AcceptInvitation)
            for ((actor, stream) in listOf(a to owner, b to recipient)) {
                val service = DeckService(runtime)
                val definition = CardDefinition("Forest", listOf(DefinitionFace(id(), "Forest", typeLine = "Basic Land — Forest")))
                val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                assertEquals(200, service.submit(actor, SubmitDefinitions(id(), listOf(submitted))).status)
                val saved = service.create(actor, CreateDeck(id(), "Engine deck", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 40))))
                val deck = GameWire.json.decodeFromString<SavedDeck>(saved.body)
                stream.execute(GameAction.SelectDeck(deck.deck.id, deck.deck.revision))
            }
            owner.execute(GameAction.SetStarter(StarterChoice(a.account.accountId)))
            bobDefaults?.let { org.asyncttwiz.server.accounts.AccountSettingsService(runtime).set(b.account.accountId, AccountSettings(playDefaults = it)) }
            val seats = listOf(a, b).map { p -> PlayerId(p.account.accountId) }.map { p ->
                Seat(p, names.mapIndexed { i, n -> DeckCard(CardId("${p.value}.$i"), Cards.definition(n)) })
            }
            engineSeats = if (!decks) { _ -> seats } else null
            try {
                owner.execute(GameAction.Ready); recipient.execute(GameAction.Ready)
            } finally { engineSeats = null }
        }

        fun snapshot(actor: AccountService.Principal = a): EngineSnapshot = games.head(actor, game.id).engine!!
        fun codec(actor: AccountService.Principal = a) = EngineJson(EngineJson.decodeDefinitions(snapshot(actor).definitions))
        fun device(actor: AccountService.Principal): GameSession {
            val snapshot = snapshot(actor)
            return GameSession(codec(actor).decodeModel(snapshot.state), snapshot.headId, PlayerId(actor.account.accountId))
        }
        fun attention(actor: AccountService.Principal) = games.head(actor, game.id).summary.attentionHolder
        fun revisions(actor: AccountService.Principal, after: String) = games.revisions(actor, game.id, after).revisions
        val alice get() = PlayerId(a.account.accountId)
        val bob get() = PlayerId(b.account.accountId)
    }

    @Test fun `submissions are taken in arrival order, a stale parent is refused, and each event is broadcast`() = withService { runtime ->
        val f = Fixture(runtime, engine = true)
        val codec = f.codec()
        val alice = f.device(f.a)
        val bob = f.device(f.b)
        // The server started the game: Alice's mulligan declaration is open (starter as the lobby chose)
        assertEquals(f.alice, alice.model.engine.pending!!.decision.player)
        // TRN-027 (switchreview #5): Bob may declare his own mulligan meanwhile: his move too, as each viewer sees it
        assertEquals(f.b.account.accountId, f.attention(f.b))
        assertEquals(f.a.account.accountId, f.attention(f.a))
        val revision = f.games.head(f.a, f.game.id).summary.revision
        // Both act on the same event; Bob's arrives first (mulligans in any order, §8)
        val bobs = f.recipient.command(GameAction.Submit(bob.act(id(), Input.SetupAnswer(bob.me, Answer.Option("Keep")))))
        val open = alice.model.engine.pending!!.decision
        val alices = f.owner.command(GameAction.Submit(alice.act(id(), Input.Answer(open.id, Answer.Option("Keep")))))
        f.recipient.admit(bobs); f.owner.admit(alices)
        assertTrue(f.games.executeOne()); assertTrue(f.games.executeOne())
        assertEquals("accepted", f.recipient.result(bobs).outcome)
        val refused = f.owner.result(alices)
        assertEquals("rejected", refused.outcome); assertEquals("stale", refused.reason)
        // The broadcast event is Bob's own; Alice's device drops hers and updates
        val broadcast = f.revisions(f.a, revision).mapNotNull { it.engineEvent }.map(codec::decodeEvent)
        assertEquals(1, broadcast.size)
        assertEquals(GameSession.Received.Confirmed, bob.receivedShown(broadcast.single()))
        assertTrue(alice.receivedShown(broadcast.single()) is GameSession.Received.Applied)
        assertTrue(alice.model.sameAs(bob.model))
        // Alice acts again on the new state; an input of the other player's is refused
        val again = alice.model.engine.pending!!.decision
        assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(again.id, Answer.Option("Keep"))))).outcome)
        val illegal = Submission(id(), alice.pending.single().id, listOf(Act.Perform(Input.PassPriority(bob.me))))
        assertEquals("refusedByEngine", f.recipient.execute(GameAction.Submit(illegal)).reason)
        // The head serves the state at the main line's head, warm and after a restart (cold)
        val warm = f.snapshot()
        assertEquals(alice.pending.single().id, warm.headId)
        assertTrue(codec.decodeModel(warm.state).sameAs(alice.model))
        assertEquals(1, codec.decodeModel(warm.state).engine.turn.number)
        assertEquals(warm, GameService(runtime).head(f.a, f.game.id).engine!!)
        // Conceding is an engine input: only for oneself; it ends the play at once (I-D56 §11)
        val concede = { by: PlayerId -> Submission(id(), warm.headId, listOf(Act.Perform(Input.Concede(by)))) }
        assertEquals("refusedByEngine", f.owner.execute(GameAction.Submit(concede(bob.me))).reason)
        val start = f.games.head(f.a, f.game.id).summary.revision
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(concede(bob.me))).outcome)
        val ended = f.games.head(f.a, f.game.id).summary
        assertEquals(GameLifecycle.LOBBY, ended.lifecycle)
        assertNull(ended.attentionHolder)
        val last = codec.decodeEvent(f.revisions(f.a, start).last().engineEvent!!)
        assertTrue(last.engine!!.steps.any { it.input == Input.Concede(bob.me) })
    }

    // I-D56 §3, §7, §8, TRN-058: Alice assumes Bob's keep and plays on: her move for both viewers; the same real answer
    // confirms it
    @Test fun `an assumed keep is confirmed by the same answer, whose move it is per viewer`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "c")
        val codec = f.codec()
        val alice = f.device(f.a)
        val bob = f.device(f.b)
        assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id,
            Answer.Option("Keep"))))).outcome)
        val aliceKeep = alice.pending.single()
        bob.receivedShown(aliceKeep)
        alice.receivedShown(aliceKeep)
        val open = alice.model.engine.pending!!.decision
        val before = f.games.head(f.a, f.game.id).summary.revision
        assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(open.id, Answer.Option("Keep")), assumed = true))).outcome)
        val assumed = alice.pending.single()
        assertEquals(f.alice, assumed.assumed?.assumedBy)
        // TRN-058 (I-D57): Bob's next stop is the assumed keep, but Alice plays on: the game waits for her alone, so it is
        // her move for both viewers
        assertEquals(f.a.account.accountId, f.attention(f.b))
        assertEquals(f.a.account.accountId, f.attention(f.a))
        val stored = f.revisions(f.b, before).last()
        assertEquals(f.a.account.accountId, stored.summary.attentionHolder)
        assertNull(stored.engine!!.moves, "the per-player table stays on the server")
        // §15: the waits for a real input that began while that player wasn't live are logged (nobody has the game open);
        // at the start both players' mulligans wait at once (switchreview #5)
        val rounds = runtime.transaction { c -> org.jooq.impl.DSL.using(c, org.jooq.SQLDialect.POSTGRES)
            .fetch("SELECT account_id, kind FROM engine_async_round ORDER BY ordinal").map { it.get(0).toString() to it.get(1, String::class.java) } }
        assertEquals(setOf(f.a.account.accountId to "keep", f.b.account.accountId to "keep"), rounds.take(2).toSet())
        // Bob answers the same at his local head: confirmed, no new event
        val head = f.snapshot().headId
        val confirm = f.recipient.execute(GameAction.Submit(bob.act(id(), Input.Answer(open.id, Answer.Option("Keep")))))
        assertEquals("accepted", confirm.outcome)
        assertEquals(head, f.snapshot().headId)
        val revision = f.revisions(f.b, before).last()
        assertEquals(assumed.id, revision.engine!!.confirmed)
        assertEquals(assumed.id, f.snapshot().localHeads[f.b.account.accountId])
        assertTrue(codec.decodeModel(f.snapshot().state).engine.turn.number == 1)
    }

    // SYS-077, HIS-109 (owner 2026-11-07): Bob's keep crosses Alice's assumption of it on the wire, with a move of his made
    // on it right behind: the keep is the assumption's confirmation (accepted, no new event, his local head past it) and
    // his next move, made on his keep, is taken on the assumption instead of refused as stale
    @Test fun `a matching answer that crossed the assumption confirms it and the moves made on it are taken`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "x")
        val alice = f.device(f.a)
        val bob = f.device(f.b)
        f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep")))))
        val aliceKeep = alice.pending.single()
        bob.receivedShown(aliceKeep); alice.receivedShown(aliceKeep)
        val open = alice.model.engine.pending!!.decision
        assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(open.id, Answer.Option("Keep")), assumed = true))).outcome)
        val assumed = alice.pending.single()
        // Bob's keep and a table-only move of his on it, both made before the assumption reached him
        val keep = bob.act(id(), Input.Answer(open.id, Answer.Option("Keep")))
        val early = bob.act(id(), null, listOf(org.asyncttwiz.protocol.game.TableChange.EarlyAnswered("early:bob", null,
            org.asyncttwiz.protocol.game.EarlyAnswer(f.bob, "early:bob", Answer.YesNo(true)))))
        assertEquals(keep.id, early.parentId)
        val before = f.games.head(f.a, f.game.id).summary.revision
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(keep)).outcome)
        assertEquals(assumed.id, f.snapshot().headId, "no new event")
        assertEquals(assumed.id, f.revisions(f.b, before).last().engine!!.confirmed)
        assertEquals(assumed.id, f.snapshot().localHeads[f.b.account.accountId], "confirmed: his local head past it")
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(early)).outcome, "taken on the assumption, not stale")
        val head = f.codec().decodeEvent(f.snapshot().events.last())
        assertEquals(early.id, head.id)
        assertEquals(assumed.id, head.parentId)
        assertEquals(early.table, head.table)
    }

    // I-D56 §8, §9: another answer branches from Bob's local head: a new line; the discarded line is kept, shown to nobody
    @Test fun `another answer branches and starts a new line`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "b")
        val alice = f.device(f.a)
        val bob = f.device(f.b)
        f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep")))))
        val aliceKeep = alice.pending.single()
        bob.receivedShown(aliceKeep); alice.receivedShown(aliceKeep)
        val open = alice.model.engine.pending!!.decision
        f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(open.id, Answer.Option("Keep")), assumed = true)))
        val discarded = alice.pending.single()
        // Bob mulligans: a branch at his local head (the start, moved to Alice's keep by his submission), line 1
        val builder = EventBuilder(bob.model, id(), aliceKeep.id, f.b.account.accountId)
        builder.startLine(f.snapshot().lines + 1)
        builder.perform(Input.Answer(open.id, Answer.Option("Mulligan")))
        builder.answerRandoms()
        val branch = builder.build()
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(Submission.of(branch))).outcome)
        val snapshot = f.snapshot()
        assertEquals(branch.id, snapshot.headId)
        assertEquals(1, snapshot.lines)
        // Alice's local head fell back to the branch point; her assumed keep isn't on the main line any more
        assertEquals(aliceKeep.id, snapshot.localHeads[f.a.account.accountId])
        assertTrue(f.codec().decodeModel(snapshot.state).sameAs(bob.model))
        assertNotEquals(discarded.id, snapshot.headId)
        // I-D56 §7 (step B): the snapshot also holds the state at the earliest local head and the events after it, so
        // her device opens at her local head with Bob's branch buffered; KWKB3Y (HIS-084): and the line before it, up to
        // GameDevice.KEPT_EVENTS events (here from the game's first event), as a device that kept the game open holds it,
        // for Undo
        assertNotEquals(aliceKeep.id, snapshot.baseId)
        assertNotEquals(snapshot.rootId, snapshot.baseId)
        // LOB-116 (I-D56 §8): Alice, whose assumption Bob answered differently, gets a Game activity that says so
        assertEquals(true, runtime.transaction { c -> org.jooq.impl.DSL.using(c, org.jooq.SQLDialect.POSTGRES)
            .select(ACTIVITY_SUMMARY.BRANCHED).from(ACTIVITY_SUMMARY).where(ACTIVITY_SUMMARY.ACCOUNT_ID.eq(UUID.fromString(f.a.account.accountId)))
            .and(ACTIVITY_SUMMARY.GAME_ID.eq(UUID.fromString(f.game.id))).and(ACTIVITY_SUMMARY.KIND.eq("GAME")).fetchOne(ACTIVITY_SUMMARY.BRANCHED) })
        assertEquals(listOf(aliceKeep, branch), snapshot.events.map(f.codec()::decodeEvent).takeLast(2))
        val (opened, _) = org.asyncttwiz.protocol.game.GameDevice.load(snapshot, f.alice) { id() }
        assertEquals(aliceKeep.id, opened.session.localHead)
        assertEquals(listOf(branch), opened.session.buffered)
        opened.playToStop()
        assertTrue(opened.model.sameAs(bob.model))
        // HIS-106 (owner playtest 2026-11-06): the snapshot also holds what of the discarded line she had been shown, so a
        // device opening the game shows the branch: where she left it, then Bob's branch received as it is live
        assertEquals(listOf(discarded), snapshot.takenBack.map(f.codec()::decodeEvent))
        val (left, _) = org.asyncttwiz.protocol.game.GameDevice.load(snapshot, f.alice, takenBack = true) { id() }
        assertEquals(discarded.id, left.session.localHead)
        assertEquals(listOf(branch), left.branchOnLoad)
        left.revision(left.branchOnLoad, null)
        assertTrue(left.notes().any { it is org.asyncttwiz.protocol.game.GameDevice.Note.Branched }, "the branch's note")
        assertEquals(aliceKeep.id, left.session.localHead)
        assertEquals(listOf(branch), left.session.buffered)
        // A table-only event of hers at the head while she is behind doesn't move her local head (it confirms nothing)
        val behind = org.asyncttwiz.protocol.game.GameDevice.load(f.snapshot(), f.alice) { id() }.first
        val early = behind.session.headModel().let { m -> EventBuilder(m, id(), snapshot.headId, f.a.account.accountId).also {
            it.table(org.asyncttwiz.protocol.game.TableChange.Suggestion(null, org.asyncttwiz.protocol.game.ResultSuggestion(f.alice, EngineResult.Draw)))
        }.build() }
        assertEquals("accepted", f.owner.execute(GameAction.Submit(Submission.of(early))).outcome)
        assertEquals(aliceKeep.id, f.snapshot().localHeads[f.a.account.accountId])
        // Her later input on the discarded line is stale
        val late = Submission(id(), discarded.id, listOf(Act.Perform(Input.PassPriority(f.alice))))
        assertEquals("stale", f.owner.execute(GameAction.Submit(late)).reason)
        // HIS-106: shown Bob's branch, her local head moves on and the take-back is no longer sent
        assertEquals(listOf(discarded.id), f.snapshot().takenBack.map { f.codec().decodeEvent(it).id })
        assertEquals("accepted", f.owner.execute(GameAction.EngineSeen(branch.id)).outcome)
        assertEquals(emptyList<String>(), f.snapshot().takenBack)
    }

    // I-D56 §10, HIS-086: a free undo rewinds at once: it only pops events (owner's rule 2026-11-04), and the next event
    // there starts the new line; past the other player's input it is a request
    @Test fun `free undo, an undo request and its answer`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "u")
        val alice = f.device(f.a)
        val start = f.snapshot().headId
        f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep")))))
        // Only Alice's own keep after the start, nothing new shown: free
        val undo = f.owner.execute(GameAction.EngineRewind(start))
        assertEquals("accepted", undo.outcome)
        val after = f.snapshot()
        assertEquals(1, after.lines)
        assertEquals(start, after.headId, "nothing of the rewind's own is added")
        val rewind = f.revisions(f.a, undo.commitRevision!!.let { (it.toLong() - 1).toString() }).single()
        assertEquals(start, rewind.engine!!.target)
        assertNull(rewind.engineEvent)
        // Both keep; Alice's undo past Bob's keep needs his approval. Her keep made again must start the new line
        val fresh = f.device(f.a)
        val keep = Input.Answer(fresh.model.engine.pending!!.decision.id, Answer.Option("Keep"))
        assertEquals("stale", f.owner.execute(GameAction.Submit(f.device(f.a).act(id(), keep))).reason)
        assertEquals("accepted", f.owner.execute(GameAction.Submit(fresh.act(id(), keep, newLine = 1))).outcome)
        val target = f.snapshot().headId
        val bob = f.device(f.b)
        f.recipient.execute(GameAction.Submit(bob.act(id(), Input.Answer(bob.model.engine.pending!!.decision.id, Answer.Option("Keep")))))
        assertEquals("requestNeeded", f.owner.execute(GameAction.EngineRewind(target)).reason)
        assertEquals("accepted", f.owner.execute(GameAction.EngineRequestRewind(target, "misclick")).outcome)
        assertEquals(EngineRewindRequest(f.a.account.accountId, target, "misclick"), f.snapshot(f.b).rewindRequest)
        // While it is open nobody sends game inputs; the requester can't answer it
        val pass = Submission(id(), f.snapshot().headId, listOf(Act.Perform(Input.PassPriority(f.alice))))
        assertEquals("rewindRequested", f.owner.execute(GameAction.Submit(pass)).reason)
        assertEquals("otherPlayerRequired", f.owner.execute(GameAction.EngineAnswerRewind(true)).reason)
        assertEquals("accepted", f.recipient.execute(GameAction.EngineAnswerRewind(true)).outcome)
        val approved = f.snapshot()
        assertNull(approved.rewindRequest)
        assertEquals(2, approved.lines)
        assertEquals(target, approved.headId)
        assertEquals(approved.headId, approved.localHeads[f.b.account.accountId])
    }

    // HIS-108 (owner playtest 2026-11-07): Alice's free undo takes back her keep that Bob's device had shown: his local
    // head goes to the undo's point and his snapshot keeps what he had been shown with who undid, so a device opening
    // the game shows the undo (where he left it, then the rewind as it is live); his device's report of that head (the
    // undo shown) clears it
    @Test fun `an undo of events the other player had seen stays with their seat until shown`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "us")
        val alice = f.device(f.a)
        val start = f.snapshot().headId
        f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep")))))
        val keep = alice.pending.single()
        assertEquals("accepted", f.recipient.execute(GameAction.EngineSeen(keep.id)).outcome)
        assertEquals("accepted", f.owner.execute(GameAction.EngineRewind(start)).outcome)
        val snapshot = f.snapshot(f.b)
        assertEquals(start, snapshot.headId)
        assertEquals(start, snapshot.localHeads[f.b.account.accountId])
        assertEquals(listOf(keep.id), snapshot.takenBack.map { f.codec().decodeEvent(it).id })
        assertEquals(f.a.account.accountId, snapshot.undoneBy)
        // Nothing of it for the player who undid
        assertEquals(emptyList<String>(), f.snapshot().takenBack)
        assertNull(f.snapshot().undoneBy)
        val (left, _) = org.asyncttwiz.protocol.game.GameDevice.load(snapshot, f.bob, takenBack = true) { id() }
        assertEquals(keep.id, left.session.localHead)
        val undo = left.undoOnLoad!!
        left.revision(left.branchOnLoad, undo)
        assertTrue(left.notes().any { it is org.asyncttwiz.protocol.game.GameDevice.Note.TakenBack }, "the undo shown")
        assertEquals(start, left.session.localHead)
        assertEquals(listOf(GameAction.EngineSeen(start)), left.take(0).filterIsInstance<GameAction.EngineSeen>())
        assertEquals("accepted", f.recipient.execute(GameAction.EngineSeen(start)).outcome)
        assertEquals(emptyList<String>(), f.snapshot(f.b).takenBack)
        assertNull(f.snapshot(f.b).undoneBy)
        assertEquals("noChange", f.recipient.execute(GameAction.EngineSeen(start)).reason)
    }

    // R6 (engine responsiveness): a player's own report (their device showed events, their settings) works out only their
    // own seat again; a submission, an undo request or a rewind works out both. The seat left as stored is checked against
    // working it out (checkSkipped).
    @Test fun `a seen report or a settings change works out only the reporting seat`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "r6")
        val work = f.games.engineGames.seatsWorkedOut
        fun seats(block: () -> Unit): Long { val before = work.get(); block(); return work.get() - before }
        checkSkipped = true
        try {
            val alice = f.device(f.a)
            val keep = Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep"))
            assertEquals(2, seats { assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(), keep))).outcome) })
            val head = f.snapshot().headId
            assertNotEquals(head, f.snapshot().localHeads[f.b.account.accountId])
            assertEquals(1, seats { assertEquals("accepted", f.recipient.execute(GameAction.EngineSeen(head)).outcome) })
            assertEquals(head, f.snapshot().localHeads[f.b.account.accountId])
            assertEquals(1, seats { assertEquals("accepted", f.recipient.execute(GameAction.EngineSettings(
                PlaySettings(ResponseMode.FullControl))).outcome) })
            val bob = f.device(f.b)
            val bobKeep = Input.Answer(bob.model.engine.pending!!.decision.id, Answer.Option("Keep"))
            assertEquals(2, seats { assertEquals("accepted", f.recipient.execute(GameAction.Submit(bob.act(id(), bobKeep))).outcome) })
            val after = f.snapshot().headId
            assertEquals(1, seats { assertEquals("accepted", f.owner.execute(GameAction.EngineSeen(after)).outcome) })
            assertEquals(1, seats { assertEquals("accepted", f.owner.execute(GameAction.EngineSettings(PlaySettings(autotap = false))).outcome) })
            assertEquals(2, seats { assertEquals("accepted", f.owner.execute(GameAction.EngineRequestRewind(head, "misclick")).outcome) })
            assertEquals(2, seats { assertEquals("accepted", f.owner.execute(GameAction.EngineCancelRewind).outcome) })
        } finally { checkSkipped = false }
    }

    // R6 (engine responsiveness): opening a game reads in one statement each what it read one by one before (the engine
    // play's row, head and seats; the play, the chat's sync point and the own pod deck name): the same values, so the same
    // head on the wire, warm and cold (nothing kept: the definitions read with the rest)
    @Test fun `a head's joined reads are the separate reads`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "j")
        val play = UUID.fromString(f.games.head(f.a, f.game.id).summary.playId!!)
        fun same(games: GameService) = runtime.transaction { c ->
            val sql = org.jooq.impl.DSL.using(c, org.jooq.SQLDialect.POSTGRES)
            val engine = games.engineGames
            val joined = engine.opened(sql, play)!!
            val store = engine.open(sql, play)!!
            assertEquals(store.rootId, joined.store.rootId)
            assertEquals(store.codec.encodeDefinitions(), joined.store.codec.encodeDefinitions())
            assertEquals(engine.head(sql, play, store.rootId), joined.head)
            assertEquals(engine.playRow(sql, play), joined.row)
            assertEquals(engine.seats(sql, play), joined.seats)
            for (viewer in listOf(f.a, f.b).map { UUID.fromString(it.account.accountId) }) {
                val reads = games.headReads(sql, UUID.fromString(f.game.id), viewer)
                assertEquals(PlayStore.read(sql, UUID.fromString(f.game.id)), reads.play)
                assertEquals(ChatStore.sync(sql, UUID.fromString(f.game.id)), reads.chatSync)
                assertEquals(PreparationStore.ownPodDeckName(sql, UUID.fromString(f.game.id), viewer), reads.ownPodDeckName)
            }
        }
        same(f.games); same(GameService(runtime))
        // Bob behind the head, an open undo request, a chat message and a pod deck name of Alice's
        val alice = f.device(f.a)
        f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep")))))
        val bob = f.device(f.b)
        f.recipient.execute(GameAction.Submit(bob.act(id(), Input.Answer(bob.model.engine.pending!!.decision.id, Answer.Option("Keep")))))
        assertEquals("accepted", f.owner.execute(GameAction.EngineRequestRewind(alice.pending.single().id, "misclick")).outcome)
        assertEquals("accepted", f.owner.execute(GameCommand(id(), GameScope(f.game.id, playId = play.toString()), GameAction.SendChat("hello"),
            f.owner.stream.streamId, f.owner.stream.generation)).outcome)
        runtime.transaction { c -> org.jooq.impl.DSL.using(c, org.jooq.SQLDialect.POSTGRES).execute(
            "UPDATE player_preparation SET pod_deck_name = 'Pod deck' WHERE game_id = ?::uuid AND account_id = ?::uuid", f.game.id, f.a.account.accountId) }
        same(f.games); same(GameService(runtime))
        assertEquals("Pod deck", f.games.head(f.a, f.game.id).ownPodDeckName)
        assertNotNull(f.snapshot(f.b).rewindRequest)
    }

    // I-D56 §13: a player's play settings are their own, kept on the server, never shown to the other player
    @Test fun `play settings are private`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "s")
        val mine = PlaySettings(ResponseMode.FullControl, alwaysAssumeResolves = true, autotap = false)
        assertEquals("accepted", f.recipient.execute(GameAction.EngineSettings(mine)).outcome)
        assertEquals(mine, f.snapshot(f.b).settings)
        assertEquals(PlaySettings(), f.snapshot(f.a).settings)
        assertTrue(f.revisions(f.a, "0").none { GameWire.encode(it).contains("alwaysAssumeResolves") })
    }

    // STK-036, SYS-071 (owner 2026-11-06): each player's response mode is a per-game value kept on the server, outside
    // undo and history: both snapshots name it, and a change reaches the other player at once (its revision carries every
    // player's mode); the rest of the settings stay the player's own. An event keeps the mode its submission recorded.
    @Test fun `the response mode is shared at once and events record theirs`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, tag = "m")
        val a = f.a.account.accountId; val b = f.b.account.accountId
        assertEquals(mapOf(a to ResponseMode.AutoSkip, b to ResponseMode.AutoSkip), f.snapshot(f.a).modes)
        assertEquals("accepted", f.recipient.execute(GameAction.EngineSettings(PlaySettings(ResponseMode.FullControl, autotap = false))).outcome)
        assertEquals(ResponseMode.FullControl, f.snapshot(f.a).modes[b])
        assertEquals(PlaySettings(), f.snapshot(f.a).settings)
        val change = f.revisions(f.a, "0").last()
        assertEquals(mapOf(a to ResponseMode.AutoSkip, b to ResponseMode.FullControl), change.engine?.modes)
        assertTrue(!GameWire.encode(change).contains("autotap"), "only the mode is shared")
        // Alice's keep, recorded under Normal
        val alice = f.device(f.a)
        val keep = alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep"))).copy(mode = ResponseMode.Normal)
        assertEquals("accepted", f.owner.execute(GameAction.Submit(keep)).outcome)
        val event = f.codec().decodeEvent(f.revisions(f.b, "0").last().engineEvent!!)
        assertEquals(keep.id, event.id)
        assertEquals(ResponseMode.Normal, event.mode)
    }

    // LOB-113 (I-D56 §7, step D): a new game starts with each player's defaults kept on the server for their account
    @Test fun `a new game starts with the account's defaults`() = withService { runtime ->
        val defaults = PlaySettings(ResponseMode.Normal, alwaysAssumeResolves = true, autotap = false)
        val f = Fixture(runtime, engine = true, tag = "d", bobDefaults = defaults)
        assertEquals(defaults, org.asyncttwiz.server.accounts.AccountSettingsService(runtime).read(f.b.account.accountId).playDefaults)
        assertEquals(defaults, f.snapshot(f.b).settings)
        assertEquals(PlaySettings(), f.snapshot(f.a).settings)
    }

    // I-D54 switch-over item 1: the game's decks parsed on the server become the engine seats
    @Test fun `a game's decks are parsed on the server`() = withService { runtime ->
        val f = Fixture(runtime, engine = true, decks = true, tag = "d")
        val model: GameModel = f.codec().decodeModel(f.snapshot().state)
        val cards = model.engine.objects.values.filter { it.owner == f.alice }
        assertEquals(40, cards.size)
        assertTrue(cards.all { it.definition?.faces?.single()?.name == "Forest" })
        // The game's secret is in the shared state; nobody's "Skip when I have no choice" is on there (I-D56 §13)
        assertTrue(model.table.secret.isNotEmpty())
        assertTrue(model.engine.players.none { it.skipWhenNoChoice })
        assertNull(Engine.result(model.engine))
    }

}

/** The server's [event] received and played back at once (a device that shows events as they arrive, I-D56 §7). */
private fun GameSession.receivedShown(event: org.asyncttwiz.protocol.game.GameEvent): GameSession.Received =
    received(event).also { while (behind) showNext() }
