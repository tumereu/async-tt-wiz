package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaceDownRuntimeTest {
    // STK-003: a face-down entry on the Stack, resolved face down; manifest top too; both replay exactly.
    @Test fun `face-down casts go through a face-down Stack entry onto the battlefield, manifest top too, and both replay exactly`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                val opponent = f.b.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun batch(actions: List<PlayAction>) = f.owner.execute(f.owner.command(PlayAction.NextStep)
                    .copy(action = automatedBatch(actions)))
                val library = state().board.zoneContainer(owner, PlayZone.LIBRARY)!!
                val hand = state().board.zoneContainer(owner, PlayZone.HAND)!!
                val field = state().board.zoneContainer(owner, PlayZone.BATTLEFIELD)!!
                assertEquals("accepted", batch(LibraryActions.draw(state().board, library.id, hand.id, 2)).outcome)
                val ids = state().board.containers.single { it.id == hand.id }.cards.take(2)
                // Each cast and its resolve are lowered against the predicted state, like the client's batch draft.
                val envelope = f.owner.command(PlayAction.NextStep).copy(action = automatedBatch(emptyList()))
                var predicted = state()
                val actions = mutableListOf<PlayAction>()
                fun add(action: PlayAction) {
                    predicted = GameplayRules.change(predicted, owner, PlayBatches.member(envelope, actions.size, action)).state!!
                    actions += action
                }
                for (id in ids) {
                    add(HandActions.castFaceDown(predicted, id)!!)
                    val entry = predicted.board.stackEffects.single { it.sourceId == id }
                    assertTrue(entry.faceDown)
                    StackQuickActions.actions(predicted, entry.id, PlayZone.BATTLEFIELD).forEach(::add)
                }
                val cast = f.owner.execute(envelope.copy(action = automatedBatch(actions)))
                assertEquals("accepted", cast.outcome, cast.reason)
                assertTrue(CombatStackRules.stack(state().board).cards.isEmpty())
                for (id in ids) {
                    val card = state().board.cards.single { it.id == id }
                    assertEquals(field.id, card.containerId)
                    assertTrue(card.isFaceDown)
                    assertEquals("2/2", card.corner)
                    assertFalse(CardVisibility.isVisibleFaceUp(state().board, card, opponent))
                }
                val manifestCard = state().board.containers.single { it.id == library.id }.cards.first()
                val manifest = f.owner.execute(f.owner.command(LibraryActions.manifestTop(state().board, library.id)!!))
                assertEquals("accepted", manifest.outcome, manifest.reason)
                assertTrue(manifest.mutation!!.transition.disclosures.isEmpty())
                val manifested = state().board.cards.single { it.id == manifestCard }
                assertEquals(state().board.zoneContainer(owner, PlayZone.BATTLEFIELD)!!.id, manifested.containerId)
                assertTrue(manifested.isFaceDown)
                assertEquals("2/2", manifested.corner)
                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
            }
        }
    }

    // ZON-073, ZON-075, TBL-109: what the server sends the opponent never shows a face-down entry's card.
    @Test fun `a face-down Stack entry reveals nothing to the opponent in heads, results or history`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                val opponent = f.b.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun opponentView() = f.games.head(f.b, f.game.id).play!!
                val library = state().board.zoneContainer(owner, PlayZone.LIBRARY)!!
                val hand = state().board.zoneContainer(owner, PlayZone.HAND)!!
                val draw = f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action =
                    automatedBatch(LibraryActions.draw(state().board, library.id, hand.id, 1))))
                assertEquals("accepted", draw.outcome, draw.reason)
                val id = state().board.containers.single { it.id == hand.id }.cards.single { it !in hand.cards }
                val history = f.games.history(f.b, f.game.id, f.root.id, "0").mutations.size

                // Either player may cast the owner's hand card face down: the opponent too, still without seeing it.
                for ((stream, actor) in listOf(f.owner to owner, f.recipient to opponent)) {
                    val cast = stream.execute(stream.command(HandActions.castFaceDown(state(), id)!!))
                    assertEquals("accepted", cast.outcome, cast.reason)
                    val event = cast.mutation!!
                    assertTrue(event.transition.disclosures.isEmpty(), "cast by $actor")
                    assertFalse(event.revealsInformation, "cast by $actor")
                    val view = opponentView()
                    val entry = view.board.stackEffects.single()
                    assertTrue(entry.faceDown)
                    assertNull(entry.frozen)
                    assertEquals(listOf(entry.id), CombatStackRules.stack(view.board).cards)
                    val card = view.board.cards.single { it.id == id }
                    assertEquals(hand.id, card.containerId, "the real card stays in the Hand")
                    assertFalse(CardVisibility.isVisibleFaceUp(view.board, card, opponent))
                    assertEquals(listOf(owner), CardVisibility.viewers(view.board, card))
                    assertTrue(ManualDisclosure.permittedFaces(view, CombatStackRules.inspection(view.board, entry.id)!!, opponent, true).isEmpty())
                    assertFalse(ManualDisclosure.permittedFaces(view, CombatStackRules.inspection(view.board, entry.id)!!, owner, true).isEmpty())
                    // A copy of it is face down too.
                    val copy = stream.execute(stream.command(PlayAction.Stack(StackAction.CopyEffect(entry.id, GameplayRules.stackDigest(view)))))
                    assertEquals("accepted", copy.outcome, copy.reason)
                    assertTrue(copy.mutation!!.transition.disclosures.isEmpty())
                    assertTrue(opponentView().board.stackEffects.all { it.faceDown })
                    assertFalse(CardVisibility.isVisibleFaceUp(opponentView().board, card, opponent))
                    for (effect in opponentView().board.stackEffects)
                        assertEquals("accepted", stream.execute(stream.command(PlayAction.Stack(StackAction.RemoveEffect(effect.id)))).outcome)
                }
                // Resolving keeps it face down; nothing in the opponent's history ever disclosed it.
                val cast = f.owner.execute(f.owner.command(HandActions.castFaceDown(state(), id)!!))
                assertEquals("accepted", cast.outcome, cast.reason)
                val entry = state().board.stackEffects.single().id
                val resolve = f.owner.execute(f.owner.command(StackQuickActions.actions(state(), entry, PlayZone.BATTLEFIELD).single()))
                assertEquals("accepted", resolve.outcome, resolve.reason)
                assertTrue(resolve.mutation!!.transition.disclosures.isEmpty())
                val down = opponentView().board.cards.single { it.id == id }
                assertTrue(down.isFaceDown)
                assertFalse(CardVisibility.isVisibleFaceUp(opponentView().board, down, opponent))
                val later = f.games.history(f.b, f.game.id, f.root.id, "0").mutations.drop(history)
                assertTrue(later.isNotEmpty())
                assertTrue(later.none { it.revealsInformation || it.transition.disclosures.any { d -> opponent in d.viewers } })
                // A face-down cast of a card outside a Hand is refused.
                val refused = f.owner.execute(f.owner.command(PlayAction.Stack(StackAction.AddEffect(id, GameplayRules.stackDigest(state()), faceDown = true))))
                assertEquals("inapplicable", refused.reason)
            }
        }
    }
}
