package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaceDownRuntimeTest {
    @Test fun `face-down casts, a face-down resolve and manifest top are accepted and replay exactly`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                val opponent = f.b.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun batch(actions: List<PlayAction>) = f.owner.execute(f.owner.command(PlayAction.NextStep)
                    .copy(action = GameAction.PlayBatch(actions)))
                val library = state().board.zoneContainer(owner, PlayZone.LIBRARY)!!
                val hand = state().board.zoneContainer(owner, PlayZone.HAND)!!
                assertEquals("accepted", batch(LibraryActions.draw(state().board, library.id, hand.id, 2)).outcome)
                val ids = state().board.containers.single { it.id == hand.id }.cards.take(2)
                // Each cast is lowered against the predicted state, like the client's batch draft.
                var predicted = state()
                val casts = ids.map { id ->
                    HandActions.castFaceDown(predicted, id).also { cast ->
                        predicted = GameplayRules.change(predicted, owner, f.owner.command(cast)).state!!
                    }
                }
                val cast = batch(casts)
                assertEquals("accepted", cast.outcome, cast.reason)
                val stack = CombatStackRules.stack(state().board)
                assertEquals(ids.reversed(), stack.cards)
                for (id in ids) {
                    val card = state().board.cards.single { it.id == id }
                    assertTrue(card.isFaceDown)
                    assertFalse(CardVisibility.isVisibleFaceUp(state().board, card, opponent))
                }
                val top = stack.cards.first()
                val resolve = f.owner.execute(f.owner.command(
                    StackQuickActions.actions(state(), top, PlayZone.BATTLEFIELD).single()))
                assertEquals("accepted", resolve.outcome, resolve.reason)
                assertTrue(resolve.mutation!!.transition.disclosures.isEmpty())
                val resolved = state().board.cards.single { it.id == top }
                assertTrue(resolved.isFaceDown)
                assertEquals("2/2", resolved.corner)
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
}
