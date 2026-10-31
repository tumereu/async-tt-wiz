package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LibraryOrderIntegrationTest {
    // ZON-019
    @Test fun `non owner can draw mill mulligan shuffle and author panels on opponents library`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 12)
                val actor = f.a.account.accountId
                val owner = f.b.account.accountId
                var tree = GameplayTree(f.root)
                fun state() = tree.stateAt(tree.currentNodeId)!!
                fun zone(zone: PlayZone) = state().board.containers.single { it.zone == zone && it.accountId == owner }
                fun play(action: PlayAction) {
                    val command = f.owner.command(action)
                    val prediction = HistoryContinuation.change(tree, actor, command)
                    assertNotNull(prediction.tree, prediction.reason)
                    val result = f.owner.execute(command)
                    assertEquals("accepted", result.outcome, result.reason)
                    tree = prediction.tree!!
                    assertEquals(tree.nodes[tree.currentNodeId], result.mutation)
                    assertEquals(state().board, f.games.head(f.a, f.game.id).play!!.board)
                }
                val library = zone(PlayZone.LIBRARY).id
                val hand = zone(PlayZone.HAND).id
                val graveyard = zone(PlayZone.GRAVEYARD).id
                val drawn = zone(PlayZone.LIBRARY).cards.first()
                LibraryActions.draw(state().board, library, hand, 1).forEach(::play)
                assertEquals(hand, state().board.cards.single { it.id == drawn }.containerId)
                val milled = zone(PlayZone.LIBRARY).cards.first()
                LibraryActions.moveSlice(state().board, library, ContentsSelection.TOP, 1,
                    MoveDestination(graveyard, MoveInsertion.UNORDERED)).forEach(::play)
                assertEquals(graveyard, state().board.cards.single { it.id == milled }.containerId)
                val seed = RandomV1.encodeSeed(ByteArray(32))
                play(PlayAction.ShuffleLibrary(library, seed))
                for (selection in ContentsSelection.entries) for (reveal in listOf(false, true)) {
                    play(PlayAction.ShowContents(library, selection, 2, reveal))
                    val panel = state().board.contentsDisplays.single()
                    assertEquals(actor, panel.author)
                    assertEquals(library, panel.containerId)
                    assertTrue(panel.cardIds.all { id -> state().board.cards.single { it.id == id }.owner == owner })
                    play(PlayAction.DismissContents(panel.id, ContentsRest.BOTTOM, seed))
                    assertTrue(state().board.contentsDisplays.isEmpty())
                    if (!panel.search) assertEquals(panel.cardIds, zone(PlayZone.LIBRARY).cards.takeLast(panel.cardIds.size))
                }
                val plan = LibraryActions.mulligan(state().board, library, hand, zone(PlayZone.HAND).cards, seed, count = 3)
                plan.prefix().forEach(::play)
                LibraryActions.draw(state().board, library, hand, plan.drawCount).forEach(::play)
                assertEquals(3, zone(PlayZone.HAND).cards.size)
                assertTrue(zone(PlayZone.HAND).cards.all { id -> state().board.cards.single { it.id == id }.owner == owner })
            }
        }
    }

    @Test fun `server secret persisted forks and own library undo match independent client prediction and sync`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var final: GameplayTree
            lateinit var f: GameplayFixture
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                f = GameplayFixture(runtime, deckSize = 60)
                assertNotNull(f.root.librarySecret)
                assertEquals(32, RandomV1.decodeSeed(f.root.librarySecret!!).size)
                val a = f.a.account.accountId; val b = f.b.account.accountId
                // Independent wire-decoded client, not the server's disposable checkpoint.
                var predicted = GameplayTree(GameWire.json.decodeFromString<PlayState>(GameWire.json.encodeToString(PlayState.serializer(), f.root)))
                var replica = TreeSync.generation(predicted)
                fun state() = predicted.stateAt(predicted.currentNodeId)!!
                fun zone(zone: PlayZone, actor: String = a) = state().board.containers.single {
                    it.zone == zone && (zone == PlayZone.STACK || it.accountId == actor) }
                fun step(command: GameCommand, stream: GameplayFixture.Stream = f.owner): GameResult {
                    val actor = stream.actor.account.accountId
                    val next = HistoryContinuation.change(predicted, actor, command)
                    assertNotNull(next.tree, "Client ${command.action}: ${next.reason}")
                    val result = stream.execute(command)
                    assertEquals("accepted", result.outcome, "Server ${command.action}: ${result.reason}")
                    val before = predicted
                    predicted = next.tree!!
                    if (command.action is GameAction.Play) assertEquals(predicted.nodes[predicted.currentNodeId], result.mutation)
                    val server = db.connection().use {
                        GameplayStore().readFull(DSL.using(it, SQLDialect.POSTGRES), f.games.head(f.a, f.game.id).play!!)
                    }
                    assertEquals(predicted, server)
                    assertEquals(state().copy(annotations = EventAnnotationState()), f.games.head(f.a, f.game.id).play)
                    replica = TreeSync.apply(replica, TreeSync.delta(before, server))
                    assertEquals(state(), replica.tree.stateAt(replica.tree.currentNodeId))
                    return result
                }
                fun play(action: PlayAction, stream: GameplayFixture.Stream = f.owner, target: String? = null) = step(
                    stream.command(action).copy(continuation = target?.let { Continuation(it, true) }), stream)
                fun control(action: GameAction, stream: GameplayFixture.Stream = f.owner) = step(
                    stream.command(PlayAction.NextStep).copy(action = action), stream)
                fun draw() = play(LibraryActions.draw(state().board, zone(PlayZone.LIBRARY).id, zone(PlayZone.HAND).id, 1).single())
                for (stream in listOf(f.owner, f.recipient)) {
                    val actor = stream.actor.account.accountId
                    val library = zone(PlayZone.LIBRARY, actor)
                    play(PlayAction.MoveCard(library.cards[2], MoveSource(library.id), MoveDestination(library.id, MoveInsertion.TOP)), stream)
                    play(PlayAction.MoveCard(library.cards[5], MoveSource(library.id), MoveDestination(library.id, MoveInsertion.BOTTOM)), stream)
                }
                draw()
                control(GameAction.Undo)
                play(PlayAction.AdjustLife(b, "1"), f.recipient)
                draw() // Same fixed top despite an intervening opponent action.
                val cast = play(PlayAction.AdjustLife(a, "-1")).mutation!!.id
                val beforeLook = state()
                val lib = zone(PlayZone.LIBRARY).id
                play(PlayAction.ShowContents(lib, ContentsSelection.TOP))
                val panel = state().board.contentsDisplays.single()
                val x = panel.cardIds.single()
                control(GameAction.Undo); control(GameAction.Redo) // Own Look is no longer blocked.
                play(PlayAction.DismissContents(panel.id))
                control(GameAction.Undo); control(GameAction.Redo) // Scry placement has an exact inverse.
                draw()
                val original = state()
                val originalHead = predicted.currentNodeId
                val fork = play(PlayAction.AdjustLife(b, "-1"), f.recipient, cast).mutation!!
                val reseeds = fork.transition.delta!!.libraryReseeds
                assertEquals(2, reseeds.size)
                for (reseed in reseeds) {
                    val old = beforeLook.board.containers.single { it.id == reseed.containerId }
                    val next = state().board.containers.single { it.id == reseed.containerId }
                    assertEquals(old.knownTop, next.knownTop); assertEquals(old.knownBottom, next.knownBottom)
                    assertEquals(old.unknownMiddle, reseed.from)
                    assertEquals(next.unknownMiddle, reseed.to)
                    assertNotEquals(reseed.from, reseed.to)
                }
                // X is now merely one member of the unknown block, not a guaranteed top card.
                assertTrue(x in zone(PlayZone.LIBRARY).unknownMiddle)
                assertEquals(original, predicted.prepareReview(originalHead))
                assertEquals(beforeLook, predicted.prepareReview(cast))
                control(GameAction.Undo, f.recipient)
                control(GameAction.Redo, f.recipient)
                val repeated = play(PlayAction.AdjustLife(b, "-1"), f.recipient, cast).mutation!!
                assertEquals(reseeds, repeated.transition.delta!!.libraryReseeds)
                val different = play(PlayAction.AdjustLife(b, "-1"), f.owner, cast).mutation!!
                assertTrue(different.transition.delta!!.libraryReseeds.zip(reseeds).all { (l, r) -> l.to != r.to })
                // All rest choices work through the real FIFO and persisted delta codec.
                val seed = RandomV1.encodeSeed(ByteArray(32))
                for (rest in ContentsRest.entries) {
                    play(PlayAction.ShowContents(lib, ContentsSelection.BOTTOM, 3, reveal = true))
                    val display = state().board.contentsDisplays.single()
                    play(PlayAction.DismissContents(display.id, rest, seed))
                    control(GameAction.Undo); control(GameAction.Redo)
                }
                play(PlayAction.ShowContents(lib, ContentsSelection.ALL))
                play(PlayAction.DismissContents(state().board.contentsDisplays.single().id, ContentsRest.TOP, seed))
                assertTrue(zone(PlayZone.LIBRARY).knownTop.isEmpty())
                assertTrue(zone(PlayZone.LIBRARY).knownBottom.isEmpty())
                final = predicted
            }
            // Restart verifies the initial secret and fork deltas survive SQL, not just runtime caches.
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val games = GameService(runtime)
                val endpoint = games.head(f.a, f.game.id).play!!
                assertEquals(f.root.librarySecret, endpoint.librarySecret)
                val stored = db.connection().use {
                    GameplayStore().readFull(DSL.using(it, SQLDialect.POSTGRES), endpoint)
                }
                assertEquals(final, stored)
                assertEquals(final.stateAt(final.currentNodeId), stored.stateAt(stored.currentNodeId))
            }
        }
    }
}
