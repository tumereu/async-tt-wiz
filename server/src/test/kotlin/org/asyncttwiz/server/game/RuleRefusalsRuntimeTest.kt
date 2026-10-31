package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** SYS-053: the server refuses actions the rules don't allow with a reason the app turns into a message; nothing
 * is recorded, the game is unchanged and a retry gets the same answer. */
class RuleRefusalsRuntimeTest {
    // SYS-053, STK-003, TBL-094
    @Test fun `off-battlefield damage and EOT marks and any real card into the Stack are refused with their reason`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun zone(kind: PlayZone) = state().board.zoneContainer(owner, kind)!!
                fun accept(action: PlayAction) = assertEquals("accepted", f.owner.execute(f.owner.command(action)).outcome)
                fun refused(reason: String, input: GameCommand, stream: GameplayFixture.Stream = f.owner) {
                    val before = state()
                    val result = stream.execute(input)
                    assertEquals("rejected", result.outcome, "${input.action}")
                    assertEquals(reason, result.reason, "${input.action}")
                    assertNull(result.commitRevision)
                    assertNull(result.mutation)
                    assertEquals(before, state(), "a refusal changes nothing")
                    assertEquals(result, stream.admit(input), "a retry gets the original refusal")
                }
                val library = zone(PlayZone.LIBRARY)
                val (inHand, inGraveyard, onField) = library.cards.take(3)
                accept(PlayAction.MoveCard(inHand, MoveSource(library.id), MoveDestination(zone(PlayZone.HAND).id, MoveInsertion.UNORDERED)))
                accept(PlayAction.MoveCard(inGraveyard, MoveSource(library.id), MoveDestination(zone(PlayZone.GRAVEYARD).id, MoveInsertion.UNORDERED)))
                accept(PlayAction.MoveCard(onField, MoveSource(library.id),
                    MoveDestination(zone(PlayZone.BATTLEFIELD).id, MoveInsertion.EMPTY, BoardPosition(0, 1))))

                // (a) Damage and every until-end-of-turn mark go only on a card on the Battlefield.
                for (id in listOf(inHand, inGraveyard, library.cards[3])) {
                    for (action in listOf(PlayAction.SetDamage(id, "2"), PlayAction.AdjustDamage(id, "1"),
                        PlayAction.SetEotCorner(id, "+1/+1"), PlayAction.AdjustEotCorner(id, CornerPart.BOTH, 1),
                        PlayAction.SetEotNote(id, "flying"), PlayAction.SetNote(id, "target")))
                        refused(RuleRefusals.OFF_BATTLEFIELD, f.owner.command(action))
                }
                // The opponent is refused the same way (either player may act on either player's cards).
                refused(RuleRefusals.OFF_BATTLEFIELD, f.recipient.command(PlayAction.SetDamage(inGraveyard, "3")), f.recipient)
                // A batch with one refused member is refused whole.
                refused(RuleRefusals.OFF_BATTLEFIELD, f.owner.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(listOf(
                    PlayAction.SetDamage(onField, "1"), PlayAction.SetDamage(inHand, "1")))))
                accept(PlayAction.SetDamage(onField, "2"))
                accept(PlayAction.SetEotCorner(onField, "+1/+1"))

                // (b) A real card never goes into the Stack, from any zone, face up or face down, guarded or not.
                val stack = CombatStackRules.stack(state().board).id
                for (id in listOf(inHand, inGraveyard, onField, zone(PlayZone.LIBRARY).cards.first())) {
                    val card = state().board.cards.single { it.id == id }
                    for (faceDown in listOf(null, true)) for (digest in listOf(GameplayRules.stackDigest(state()), null)) {
                        refused(RuleRefusals.REAL_CARD_ON_STACK, f.owner.command(PlayAction.MoveCard(id,
                            MoveSource(card.containerId, card.placement), MoveDestination(stack, MoveInsertion.TOP), digest, faceDown = faceDown)))
                    }
                }
                val card = state().board.cards.single { it.id == inHand }
                refused(RuleRefusals.REAL_CARD_ON_STACK, f.owner.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(listOf(
                    PlayAction.AdjustLife(owner, "1"), PlayAction.MoveCard(inHand, MoveSource(card.containerId),
                        MoveDestination(stack, MoveInsertion.BOTTOM), GameplayRules.stackDigest(state()))))))
                // The same card goes onto the Stack as an entry pointing at it, and stays in the Hand.
                accept(PlayAction.Stack(StackAction.AddEffect(inHand, GameplayRules.stackDigest(state()))))
                assertEquals(zone(PlayZone.HAND).id, state().board.cards.single { it.id == inHand }.containerId)
                assertTrue(state().board.stackEffects.single().let { it.sourceId == inHand && it.id in CombatStackRules.stack(state().board).cards })

                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
            }
        }
    }
}
