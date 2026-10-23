package org.asyncttwiz.server.game

import java.util.UUID
import java.util.zip.GZIPInputStream
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HistoryRuntimeIntegrationTest {
    private fun control(stream: GameplayFixture.Stream, action: GameAction) = stream.command(PlayAction.NextStep).copy(action = action)
    private fun tree(db: OwnedPostgres, f: GameplayFixture) = db.connection().use {
        GameplayStore().read(DSL.using(it, SQLDialect.POSTGRES), f.games.head(f.a, f.game.id).play!!) }
    private fun sql(db: OwnedPostgres, text: String) = db.connection().use { it.createStatement().execute(text) }
    private fun download(games: GameService, f: GameplayFixture, ids: List<String> = emptyList()): Pair<GameplayManifest, GameplayTree> {
        val manifest = games.baseline(f.a, f.game.id, ids)
        GameplaySync.validate(manifest)
        val records = manifest.chunks.flatMap { chunk ->
            val bytes = GZIPInputStream(games.baselineChunk(f.a, manifest.id, chunk.index).inputStream()).use { it.readAllBytes() }
            assertEquals(chunk.bytes, bytes.size); assertEquals(chunk.digest, GameplaySync.digest(bytes))
            // Small runtime fixtures use one chunk; large/split byte streams covered by I26 staging tests.
            bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.map {
                GameWire.json.decodeFromString<TreeSyncRecord>(it) }.toList()
        }
        assertEquals(manifest.recordCount, records.size)
        val tree = TreeSync.decode(records.asSequence(), f.game.id, f.root.id).tree
        assertEquals(manifest.head!!.play, tree.stateAt(tree.currentNodeId))
        return manifest to tree
    }

    @Test fun `real decks retain undo for Stack quick actions and text after deliberate branch switch`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 60, rulesText = "First paragraph\n\nSecond paragraph")
                val actor = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun zone(kind: PlayZone, owner: String = actor) = state().board.containers.single {
                    it.zone == kind && (kind == PlayZone.STACK || it.accountId == owner) }
                fun play(action: PlayAction, stream: GameplayFixture.Stream = f.owner): GameResult =
                    stream.execute(stream.command(action)).also { assertNull(it.reason, "$action: ${it.reason}") }
                fun reversible(action: PlayAction) {
                    val before = tree(db, f)
                    val result = play(action)
                    if (result.outcome == "noOp") { assertEquals(before, tree(db, f)); return }
                    val after = tree(db, f)
                    val undo = control(f.owner, GameAction.Undo)
                    val undone = f.owner.execute(undo)
                    assertEquals("accepted", undone.outcome, "$action: ${undone.reason}")
                    assertEquals(result.mutation!!.id, undone.targetNodeId)
                    val removed = tree(db, f)
                    assertEquals(before.nodes, removed.nodes)
                    assertEquals(before.stateAt(before.currentNodeId)!!.board, state().board)
                    assertEquals(removed, download(f.games, f).second)
                    assertEquals(undone, f.owner.admit(undo)) // Original result, not a second removal.
                    val redo = f.owner.execute(control(f.owner, GameAction.Redo))
                    assertEquals("accepted", redo.outcome, redo.reason)
                    assertEquals(after, tree(db, f))
                }
                val bob = f.b.account.accountId
                val bobLibrary = zone(PlayZone.LIBRARY, bob)
                val bobCard = bobLibrary.cards.first()
                play(PlayAction.MoveCard(bobCard, MoveSource(bobLibrary.id, edge = MoveSourceEdge.TOP),
                    MoveDestination(zone(PlayZone.HAND, bob).id, MoveInsertion.UNORDERED)), f.recipient)
                play(PlayAction.MoveCard(bobCard, MoveSource(zone(PlayZone.HAND, bob).id),
                    MoveDestination(zone(PlayZone.STACK).id, MoveInsertion.TOP), GameplayRules.stackDigest(state())), f.recipient)
                val library = zone(PlayZone.LIBRARY)
                val card = library.cards.first()
                play(PlayAction.MoveCard(card, MoveSource(library.id, edge = MoveSourceEdge.TOP),
                    MoveDestination(zone(PlayZone.BATTLEFIELD).id, MoveInsertion.EMPTY, BoardPosition(3, 2))))
                val retained = tree(db, f)
                play(PlayAction.Ping(EventReference(f.root.id, retained.currentNodeId,
                    retained.contextVersion(retained.currentNodeId)), PingTarget(PingTargetKind.CARD, card)))
                repeat(32) { play(PlayAction.AdjustLife(actor, if (it % 2 == 0) "1" else "-1")) }
                for (branched in listOf(false, true)) {
                    if (branched) {
                        val displaced = state().currentNodeId
                        val fork = f.owner.command(PlayAction.AdjustLife(actor, "1"))
                            .copy(continuation = Continuation(f.root.root.id, true))
                        assertEquals("accepted", f.owner.execute(fork).outcome)
                        assertEquals("accepted", f.owner.execute(control(f.owner, GameAction.MakeCurrent(displaced))).outcome)
                        assertEquals(2, tree(db, f).branchHeads.size)
                    }
                    for (projection in listOf(false, true)) {
                        val source = state().board.cards.single { it.id == card }
                        reversible(if (projection) PlayAction.Stack(StackAction.AddEffect(card, GameplayRules.stackDigest(state())))
                            else PlayAction.MoveCard(card, MoveSource(source.containerId, source.placement),
                                MoveDestination(zone(PlayZone.STACK).id, MoveInsertion.TOP), GameplayRules.stackDigest(state())))
                        val entry = CombatStackRules.stack(state().board).cards.first()
                        reversible(PlayAction.Stack(StackAction.MarkText(entry, 0)))
                        reversible(PlayAction.Stack(StackAction.MarkText(entry, null)))
                        // Both projection removal + source move are separate Undo boundaries.
                        val actions = StackQuickActions.actions(state(), entry, PlayZone.GRAVEYARD)
                        actions.forEach(::reversible)
                        repeat(if (projection) 2 else 1) {
                            assertEquals("accepted", f.owner.execute(control(f.owner, GameAction.Undo)).outcome)
                        }
                        StackQuickActions.actions(state(), entry, PlayZone.BATTLEFIELD).forEach(::reversible)
                    }
                }
                // Alice can also resolve Bob's real Stack card, then Undo her own latest mutation.
                StackQuickActions.actions(state(), bobCard, PlayZone.GRAVEYARD).forEach(::reversible)
            }
        }
    }

    @Test fun `historical failure and noop preserve both account queues first effective fork clears both atomically`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready); val f = GameplayFixture(runtime)
                f.owner.execute(f.owner.command(PlayAction.SetLife(f.a.account.accountId, "21")))
                f.recipient.execute(f.recipient.command(PlayAction.SetLife(f.b.account.accountId, "21")))
                f.owner.execute(f.owner.command(PlayAction.SetNote(f.root.board.cards.first().id, "A")))
                f.recipient.execute(f.recipient.command(PlayAction.SetNote(f.root.board.cards.last().id, "B")))
                assertEquals("accepted", f.owner.execute(control(f.owner, GameAction.Undo)).outcome)
                assertEquals("accepted", f.recipient.execute(control(f.recipient, GameAction.Undo)).outcome)
                val before = tree(db, f)
                assertEquals(setOf(f.a.account.accountId, f.b.account.accountId), before.redoByAccount.keys)
                val card = f.root.board.cards.first().id
                val noop = f.owner.command(PlayAction.SetTapped(card, false)).copy(continuation = Continuation(f.root.root.id, true))
                val failure = f.owner.command(PlayAction.SetNote(gameplayId(), "missing")).copy(continuation = noop.continuation)
                val head = f.games.head(f.a, f.game.id)
                assertEquals("noOp", f.owner.execute(noop).outcome)
                assertEquals("rejected", f.owner.execute(failure).outcome)
                assertEquals(before, tree(db, f)); assertEquals(head, f.games.head(f.a, f.game.id))
                assertNotEquals(f.root.root.id, before.currentNodeId)
                val successful = f.owner.command(PlayAction.SetNote(card, "real historical fork"))
                    .copy(continuation = Continuation(f.root.root.id, true))
                assertEquals("accepted", f.owner.execute(successful).outcome)
                val after = tree(db, f)
                assertTrue(after.redoByAccount.isEmpty()); assertEquals(1, after.branches.size)
                assertEquals(successful.requestId, after.selectionControlId)
                assertNotNull(after.stateAt(before.currentNodeId)); assertEquals(after, download(f.games, f).second)
            }
        }
    }

    @Test fun `opponent suffix preserves relevant tuples and dependent retained reference blocks latest own without skipping`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready); val f = GameplayFixture(runtime)
                val library = f.root.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a.account.accountId }
                val card = library.cards.first()
                f.owner.execute(f.owner.command(PlayAction.SetLife(f.a.account.accountId, "21")))
                // Use a nonrevealing reorder: drawing now correctly blocks selective Undo.
                val move = f.owner.execute(f.owner.command(PlayAction.MoveCard(card, MoveSource(library.id, edge = MoveSourceEdge.TOP),
                    MoveDestination(library.id, MoveInsertion.BOTTOM))))
                f.recipient.execute(f.recipient.command(PlayAction.SetTapped(card, true)))
                val withMove = tree(db, f)
                assertEquals(card, withMove.stateAt(withMove.currentNodeId)!!.board.containers.single { it.id == library.id }.cards.last())
                assertEquals("accepted", f.owner.execute(control(f.owner, GameAction.Undo)).outcome)
                val removed = tree(db, f)
                assertEquals(withMove.currentNodeId, removed.currentNodeId)
                assertTrue(removed.stateAt(removed.currentNodeId)!!.board.cards.single { it.id == card }.tapped)
                assertEquals(library.id, removed.stateAt(removed.currentNodeId)!!.board.cards.single { it.id == card }.containerId)
                assertEquals(card, removed.stateAt(removed.currentNodeId)!!.board.containers.single { it.id == library.id }.cards.first())
                assertEquals("accepted", f.owner.execute(control(f.owner, GameAction.Redo)).outcome)
                val retained = tree(db, f)
                val reference = EventReference(f.root.id, move.mutation!!.id, retained.contextVersion(move.mutation!!.id))
                assertEquals("accepted", f.recipient.execute(f.recipient.command(PlayAction.EventComment(reference, "depends on move"))).outcome)
                val before = tree(db, f)
                val result = f.owner.execute(control(f.owner, GameAction.Undo))
                assertEquals("rejected", result.outcome); assertEquals(move.mutation!!.id, result.targetNodeId)
                assertEquals(before, tree(db, f)); assertTrue(before.redoByAccount.isEmpty())
                val branchTarget = f.owner.execute(f.owner.command(PlayAction.SetLife(f.a.account.accountId, "24"))
                    .copy(continuation = Continuation(f.root.root.id, true))).mutation!!.id
                val crossReference = EventReference(f.root.id, branchTarget, tree(db, f).contextVersion(branchTarget))
                assertEquals("accepted", f.recipient.execute(f.recipient.command(PlayAction.EventComment(crossReference,
                    "inactive cross-branch reference")).copy(continuation = Continuation(f.root.root.id, true))).outcome)
                f.owner.execute(control(f.owner, GameAction.MakeCurrent(branchTarget)))
                val crossBranch = tree(db, f)
                val blocked = f.owner.execute(control(f.owner, GameAction.Undo))
                assertEquals("referenceBlocked", blocked.reason); assertEquals(branchTarget, blocked.targetNodeId)
                assertEquals(crossBranch, tree(db, f)); assertEquals(crossBranch, download(f.games, f).second)
                db.connection().use { connection ->
                    connection.createStatement().executeQuery("SELECT count(*) FROM game_revision WHERE payload::jsonb #>> '{scope,playId}' IS NOT NULL AND payload::jsonb -> 'play' <> 'null'::jsonb").use {
                        assertTrue(it.next()); assertEquals(0, it.getInt(1), "no per-node board snapshot journal") }
                }
            }
        }
    }

    @Test fun `real FIFO selects successive own candidates across devices and original controls never execute twice`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var f: GameplayFixture
            lateinit var original: GameCommand
            lateinit var result: GameResult
            lateinit var finalTree: GameplayTree
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready); f = GameplayFixture(runtime)
                val device = f.Stream(f.a)
                val first = f.owner.execute(f.owner.command(PlayAction.SetLife(f.a.account.accountId, "21")))
                val second = device.execute(device.command(PlayAction.SetNote(f.root.board.cards.first().id, "device two")))
                val u1 = control(f.owner, GameAction.Undo); val u2 = control(device, GameAction.Undo)
                val r1 = f.owner.admit(u1) as GameReceipt; val r2 = device.admit(u2) as GameReceipt
                assertTrue(r1.ingressOrdinal.toLong() < r2.ingressOrdinal.toLong())
                repeat(2) { assertTrue(f.games.executeOne()) }
                assertEquals(second.mutation!!.id, f.owner.result(u1).targetNodeId)
                assertEquals(first.mutation!!.id, device.result(u2).targetNodeId)
                assertEquals(listOf(second.mutation!!.id, first.mutation!!.id), tree(db, f).redoByAccount[f.a.account.accountId]!!.map { it.mutation.id })
                original = control(device, GameAction.Redo); result = device.execute(original)
                assertEquals(first.mutation!!.id, result.targetNodeId)
                val switch = control(f.recipient, GameAction.MakeCurrent(f.root.root.id))
                assertEquals("accepted", f.recipient.execute(switch).outcome)
                assertTrue(tree(db, f).redoByAccount.isEmpty())
                // Newer stacks must survive old accepted Undo/Redo/switch delivery retries.
                val edit = f.owner.command(PlayAction.SetLife(f.a.account.accountId, "40")); f.owner.execute(edit)
                f.owner.execute(control(f.owner, GameAction.Undo))
                finalTree = tree(db, f)
                assertEquals(result, device.admit(original))
                assertEquals("accepted", f.recipient.admit(switch).let { (it as GameResult).outcome })
                assertEquals(finalTree, tree(db, f))
                sql(db, "DELETE FROM game_revision")
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    f.games.baseline(f.a, f.game.id, listOf(switch.requestId)) }.code)
                val (manifest, exported) = download(f.games, f, listOf(original.requestId))
                assertEquals(finalTree, exported); assertTrue(manifest.statuses.all { it.status == "final" })
                sql(db, "UPDATE game_play SET payload=jsonb_set(payload::jsonb, '{board,players,0,life}', '\"corrupt checkpoint\"')::text")
            }
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime)
                runtime.attachWorker(games::validatePending, { true })
                runtime.start(); gameplayAwait(runtime::ready)
                assertEquals(result, games.results(f.a, f.game.id, listOf(original.requestId)).statuses.single().result)
                val exported = download(games, f).second
                assertEquals(finalTree, exported); assertFalse(games.executeOne())
            }
        }
    }

    @Test fun `all merged families use real relational tree replay selective tuples and archive return catalog`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready); val f = GameplayFixture(runtime)
                val actor = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun step(action: PlayAction, undo: Boolean = true): GameResult {
                    val before = state(); val input = f.owner.command(action); val result = f.owner.execute(input)
                    assertEquals("accepted", result.outcome, "$action: ${result.reason}")
                    val after = state(); val retained = tree(db, f)
                    assertEquals(after, retained.stateAt(retained.currentNodeId))
                    if (undo) {
                        val removed = f.owner.execute(control(f.owner, GameAction.Undo))
                        if (result.mutation!!.revealsInformation) {
                            assertEquals("revealingTarget", removed.reason); assertEquals(after, state())
                        } else {
                            assertEquals("accepted", removed.outcome, "$action Undo: ${removed.reason}")
                            assertEquals(before.board, state().board)
                            assertEquals("accepted", f.owner.execute(control(f.owner, GameAction.Redo)).outcome)
                            assertEquals(after, state())
                        }
                    }
                    return result
                }
                val library = state().board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == actor }
                val field = state().board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == actor }
                val card = library.cards.first()
                step(PlayAction.MoveCard(card, MoveSource(library.id, edge = MoveSourceEdge.TOP),
                    MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(0, 0))))
                assertEquals("noOp", f.owner.execute(f.owner.command(PlayAction.SetCovered(card, false))).outcome)
                listOf<PlayAction>(PlayAction.SetTapped(card, true), PlayAction.SetNote(card, "retained"),
                    PlayAction.SetCorner(card, "9/9"), PlayAction.ResetCorner(card),
                    PlayAction.SetCounter(CounterTarget.CARD, card, CounterColor.RED, "2"),
                    PlayAction.ClearCardCounter(card, CounterColor.RED), PlayAction.SetOwner(card, f.b.account.accountId),
                    PlayAction.SetCovered(card, true),
                    PlayAction.InspectFace(card, state().board.cards.single { it.id == card }.faceId, field.id),
                    PlayAction.SetCovered(card, false), PlayAction.AdjustLife(actor, "3"),
                    PlayAction.SetPlayerCounter(actor, CounterColor.BLUE, "1"), PlayAction.NextStep,
                    PlayAction.EndTurn, PlayAction.RollDie(6, 4), PlayAction.FlipCoin(CoinFace.HEADS),
                    PlayAction.ShuffleLibrary(library.id, RandomV1.encodeSeed(ByteArray(32))),
                    PlayAction.ShowContents(library.id, ContentsSelection.TOP, 2)).forEach { step(it) }
                val display = state().board.contentsDisplays.single().id
                step(PlayAction.DismissContents(display))
                step(PlayAction.Combat(CombatAction.Open))
                step(PlayAction.Combat(CombatAction.Declare(state().board.combat!!.id, card)))
                step(PlayAction.Stack(StackAction.AddEffect(card, GameplayRules.stackDigest(state()))))
                step(PlayAction.CloneToken(card))
                val token = state().board.cards.single { it.token }
                step(PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, token.id), LinkEndpoint(LinkEndpointKind.PLAYER, actor)))
                step(PlayAction.RemoveToken(token.id))
                val reference = EventReference(f.root.id, f.root.root.id)
                step(PlayAction.EventComment(reference, "root comment"))
                step(PlayAction.Ping(reference, PingTarget(PingTargetKind.PLAYER, actor)))
                step(PlayAction.Unping(reference, PingTarget(PingTargetKind.PLAYER, actor)))
                val beforeFork = tree(db, f)
                val failed = f.owner.command(PlayAction.SetTapped(gameplayId(), true)).copy(continuation = Continuation(f.root.root.id, true))
                assertEquals("rejected", f.owner.execute(failed).outcome); assertEquals(beforeFork, tree(db, f))
                val fork = f.owner.command(PlayAction.SetNote(card, "alternative")).copy(continuation = Continuation(f.root.root.id, true))
                assertEquals("accepted", f.owner.execute(fork).outcome)
                val alternative = tree(db, f); val catalog = alternative.archiveCatalog()
                val preview = BranchArchives.previewReturn(alternative, catalog, alternative.currentBranchId)
                assertFalse(preview.needsSelection, preview.reason)
                val archive = control(f.owner, GameAction.BranchArchive(BranchArchiveIntent.ArchiveAndReturn(
                    alternative.currentBranchId, preview.target!!, expectedReturnHead = preview.returnHead)))
                assertEquals("accepted", f.owner.execute(archive).outcome)
                val archived = tree(db, f)
                assertEquals(archive.requestId, archived.selectionControlId)
                assertEquals(beforeFork.currentNodeId, archived.currentNodeId)
                val flat = TreeSync.records(TreeSync.generation(archived)).toList()
                assertEquals(archived, TreeSync.decode(flat.asSequence(), f.game.id, f.root.id).tree)
                GameplaySync.validate(GameplaySnapshot(f.games.head(f.a, f.game.id), emptyList(), flat))
                assertEquals(archived, download(f.games, f).second)
                f.owner.execute(control(f.owner, GameAction.MakeCurrent(f.root.root.id)))
                f.owner.execute(control(f.owner, GameAction.MakeCurrent(beforeFork.currentNodeId)))
                assertEquals("changedSelection", f.owner.execute(control(f.owner, GameAction.BranchArchive(
                    BranchArchiveIntent.Recover(archive.requestId, true)))).reason)
                assertEquals("accepted", f.owner.admit(archive).let { (it as GameResult).outcome })
            }
        }
    }

    @Test fun `late fault rolls tree topology stacks checkpoint revision result and outbox back together`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready); val f = GameplayFixture(runtime)
                f.owner.execute(f.owner.command(PlayAction.SetLife(f.a.account.accountId, "21")))
                val before = tree(db, f); val head = f.games.head(f.a, f.game.id)
                val undo = control(f.owner, GameAction.Undo); f.owner.admit(undo)
                sql(db, """CREATE FUNCTION fail_history_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned history late fault'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_history_commit BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_history_commit();""")
                assertThrows(Exception::class.java) { f.games.executeOne() }; gameplayAwait(runtime::ready)
                assertEquals(before, tree(db, f)); assertEquals(head, f.games.head(f.a, f.game.id))
                assertEquals("pending", f.games.results(f.a, f.game.id, listOf(undo.requestId)).statuses.single().status)
                sql(db, "DROP TRIGGER fail_history_commit ON game_outbox; DROP FUNCTION fail_history_commit()")
                assertTrue(f.games.executeOne()); assertEquals("accepted", f.owner.result(undo).outcome)
                assertEquals(1, tree(db, f).redoByAccount[f.a.account.accountId]!!.size)
                assertEquals(f.owner.result(undo), f.owner.admit(undo))
            }
        }
    }
}
