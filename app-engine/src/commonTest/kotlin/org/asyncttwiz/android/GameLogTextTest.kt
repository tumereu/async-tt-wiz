@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.android

import arcanum.api.CardDefinition
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerState
import arcanum.engine.state.PriorityState
import arcanum.engine.state.TurnState
import arcanum.engine.state.of
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HIS-240 (owner 2026-11-20, the game log: "let's keep the colors"): the game log's texts are the timeline's (HIS-091) with
 * each player's name and each card named in its player's colour: the names an event's text holds, with whose they are.
 */
class GameLogTextTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val names = mapOf("alice" to "Alice", "bob" to "Bob")
    private val spark = ObjectId.Card(CardId("bob.1"), 0)
    private val secret = ObjectId.Card(CardId("bob.2"), 0)
    private val mountain = ObjectId.Card(CardId("alice.1"), 0)
    private fun card(id: ObjectId.Card, owner: PlayerId, zone: Zone, name: String, time: Long) =
        id to GameObject(id, owner, owner, zone, CardDefinition(Layout.Normal, listOf(FaceDefinition(name))), timestamp = time)
    private val state = GameState.of(
        players = listOf(PlayerState(alice, 20), PlayerState(bob, 20, hand = listOf(secret))),
        objects = mapOf(card(spark, bob, Zone.Battlefield, "Storm Spark", 1), card(secret, bob, Zone.Hand, "Ember Herald", 2),
            card(mountain, alice, Zone.Battlefield, "Mountain", 3)),
        battlefield = listOf(spark, mountain), turn = TurnState(3, bob, TurnStep.PrecombatMain), priority = PriorityState(bob))

    private fun event(author: String, input: Input) = GameEvent("e", "p", author, EnginePart(0, listOf(EngineStep(input, emptyList()))))

    // HIS-240, HIS-091: Bob's name and his card in his colour for Alice; "You" and the same card in his for himself
    @Test fun theActorAndTheCardAreTheirPlayers() {
        val cast = event("bob", Input.Cast(bob, spark))
        val forAlice = EngineEventText.of(cast, state, "alice", names)
        assertEquals(listOf("Bob" to "bob", " cast " to null, "Storm Spark" to "bob"),
            EngineEventText.pieces(forAlice, EngineEventText.marks(cast, state, "alice", names)))
        val forBob = EngineEventText.of(cast, state, "bob", names)
        assertEquals(listOf("You" to "bob", " cast " to null, "Storm Spark" to "bob"),
            EngineEventText.pieces(forBob, EngineEventText.marks(cast, state, "bob", names)))
        val land = event("alice", Input.PlayLand(alice, mountain))
        assertEquals(listOf("Alice" to "alice", " played " to null, "Mountain" to "alice"),
            EngineEventText.pieces(EngineEventText.of(land, state, "bob", names), EngineEventText.marks(land, state, "bob", names)))
    }

    // HIS-062: a card the viewer can't see gives no name to colour ("a card" stays plain)
    @Test fun aHiddenCardIsNoName() {
        val cast = event("bob", Input.Cast(bob, secret))
        val marks = EngineEventText.marks(cast, state, "alice", names)
        assertEquals(emptyList(), marks.filter { it.text == "Ember Herald" })
        assertEquals(listOf("Bob" to "bob", " cast a card" to null), EngineEventText.pieces(EngineEventText.of(cast, state, "alice", names), marks))
    }

    // HIS-240: a name is found only as a whole word, the longest first, and the pieces' words are the text
    @Test fun namesAreWholeWords() {
        val marks = listOf(EventTextMark("Bob", "bob"), EventTextMark("Bob Spark", "alice"), EventTextMark("You", "alice"))
        assertEquals(listOf("Bobby saw " to null, "Bob Spark" to "alice", " and " to null, "Bob" to "bob", "'s Youth" to null),
            EngineEventText.pieces("Bobby saw Bob Spark and Bob's Youth", marks))
        assertEquals(listOf("Turn 4 → " to null, "Bob" to "bob"), EngineEventText.pieces("Turn 4 → Bob", marks))
        assertEquals(listOf("The game began" to null), EngineEventText.pieces("The game began", marks))
    }
}
