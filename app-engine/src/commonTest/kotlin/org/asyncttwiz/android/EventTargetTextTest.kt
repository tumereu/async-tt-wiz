@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.android

import arcanum.api.CardDefinition
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Move
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerState
import arcanum.engine.state.PriorityState
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import arcanum.engine.state.of
import arcanum.engine.view.AbilityOrigin
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HIS-091 (owner 2026-11-21: "very valuable information, not sure if the log is very helpful otherwise"): event texts name
 * what a spell or ability targeted and what an attack attacked, as the viewer may see it (HIS-062), with each seen target
 * in its player's colour in the game log (HIS-240).
 */
class EventTargetTextTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val names = mapOf("alice" to "Alice", "bob" to "Bob")
    private fun id(name: String) = ObjectId.Card(CardId(name), 0)
    private val spark = id("bob.spark")
    private val herald = id("bob.herald")
    private val cub = id("alice.cub")
    private val hidden = id("alice.morph")
    private val inHand = id("alice.hand")
    private val inLibrary = id("alice.library")
    private val inGraveyard = id("alice.graveyard")
    private val sage = id("alice.sage")
    private val siege = id("alice.siege")
    private val bobCub = id("bob.cub")
    private val raider = id("bob.raider")
    private val gone = id("alice.gone")
    private fun card(id: ObjectId.Card, owner: PlayerId, zone: Zone, name: String, time: Long, faceDown: Boolean = false) =
        GameObject(id, owner, owner, zone, CardDefinition(Layout.Normal, listOf(FaceDefinition(name))), timestamp = time, faceDown = faceDown)
    private val goneBefore = card(gone, alice, Zone.Battlefield, "Ash Pup", 20)
    private val goneAfter = card(ObjectId.Card(gone.card, 1), alice, Zone.Graveyard, "Ash Pup", 21)
    private val state = GameState.of(
        players = listOf(PlayerState(alice, 20, hand = listOf(inHand), library = listOf(inLibrary),
            graveyard = listOf(inGraveyard, goneAfter.id)), PlayerState(bob, 20)),
        objects = listOf(card(spark, bob, Zone.Stack, "Storm Spark", 1), card(herald, bob, Zone.Battlefield, "Ember Herald", 2),
            card(cub, alice, Zone.Battlefield, "Ember Cub", 3), card(hidden, alice, Zone.Battlefield, "Ember Lurker", 4, faceDown = true),
            card(inHand, alice, Zone.Hand, "Ember Secret", 5), card(inLibrary, alice, Zone.Library, "Ember Depths", 6),
            card(inGraveyard, alice, Zone.Graveyard, "Ember Bones", 7), card(sage, alice, Zone.Battlefield, "Ember Sage", 8),
            card(siege, alice, Zone.Battlefield, "Ember Siege", 9), card(bobCub, bob, Zone.Battlefield, "Ember Cub", 10),
            card(raider, bob, Zone.Battlefield, "Ember Raider", 11), goneAfter).associateBy { it.id },
        battlefield = listOf(herald, cub, hidden, sage, siege, bobCub, raider), stack = listOf(spark),
        turn = TurnState(3, bob, TurnStep.PrecombatMain), priority = PriorityState(bob),
        // The Ash Pup Bob targeted died since: the engine's record of the turn knows where it was
        log = listOf(LoggedChange(3, Change.ZoneChange(listOf(Move(goneBefore, goneAfter, 0, 0))))))

    private fun item(id: ObjectId, kind: StackItemKind, source: ObjectId, vararg targets: TargetRef, origin: AbilityOrigin? = null) =
        StackItem(id, kind, bob, source, null, origin, targets = mapOf("target" to targets.toList()), timestamp = 30)
    private fun cast(vararg targets: TargetRef) = GameEvent("e", "p", "bob", EnginePart(0, listOf(
        EngineStep(Input.Cast(bob, ObjectId.Card(spark.card, 0)), listOf(Change.StackItemAdded(item(spark, StackItemKind.Spell, spark, *targets), null))))))
    private fun text(event: GameEvent, viewer: String) = EngineEventText.of(event, state, viewer, names)
    private fun pieces(event: GameEvent, viewer: String) =
        EngineEventText.pieces(text(event, viewer), EngineEventText.marks(event, state, viewer, names))
    private fun card(of: ObjectId) = TargetRef.Object(of)

    // HIS-243, HIS-244, HIS-240: a creature target by whose it is, for both players, in its controller's colour
    @Test fun aSpellNamesItsTarget() {
        assertEquals("Bob cast Storm Spark targeting your Ember Cub", text(cast(card(cub)), "alice"))
        assertEquals("You cast Storm Spark targeting Alice's Ember Cub", text(cast(card(cub)), "bob"))
        assertEquals(listOf("Bob" to "bob", " cast " to null, "Storm Spark" to "bob", " targeting " to null, "your Ember Cub" to "alice"),
            pieces(cast(card(cub)), "alice"))
        // Two Ember Cubs: each in its own controller's colour
        assertEquals(listOf("You" to "bob", " cast " to null, "Storm Spark" to "bob", " targeting " to null, "Alice's Ember Cub" to "alice",
            " and " to null, "your Ember Cub" to "bob"), pieces(cast(card(cub), card(bobCub)), "bob"))
    }

    // HIS-243, HIS-240: a player as "you" or by name, in their colour; the caster's own self as "yourself"
    @Test fun aPlayerTarget() {
        assertEquals("Bob cast Storm Spark targeting you", text(cast(TargetRef.Player(alice)), "alice"))
        assertEquals("You cast Storm Spark targeting Alice", text(cast(TargetRef.Player(alice)), "bob"))
        assertEquals("You cast Storm Spark targeting yourself", text(cast(TargetRef.Player(bob)), "bob"))
        assertEquals("Bob cast Storm Spark targeting Bob", text(cast(TargetRef.Player(bob)), "alice"))
        assertEquals(listOf("Bob" to "bob", " cast " to null, "Storm Spark" to "bob", " targeting " to null, "you" to "alice"),
            pieces(cast(TargetRef.Player(alice)), "alice"))
        assertEquals(listOf("You" to "bob", " cast " to null, "Storm Spark" to "bob", " targeting " to null, "yourself" to "bob"),
            pieces(cast(TargetRef.Player(bob)), "bob"))
    }

    // HIS-243: several targets as a list; past three, "and N more"; one target chosen twice once
    @Test fun severalTargets() {
        assertEquals("Bob cast Storm Spark targeting you and your Ember Cub", text(cast(TargetRef.Player(alice), card(cub)), "alice"))
        assertEquals("Bob cast Storm Spark targeting you, your Ember Cub and your Ember Sage",
            text(cast(TargetRef.Player(alice), card(cub), card(sage)), "alice"))
        assertEquals("Bob cast Storm Spark targeting you, your Ember Cub, your Ember Sage and 2 more",
            text(cast(TargetRef.Player(alice), card(cub), card(sage), card(siege), card(herald)), "alice"))
        assertEquals("Bob cast Storm Spark targeting your Ember Cub", text(cast(card(cub), card(cub)), "alice"))
    }

    // HIS-243: an ability names its targets after what was activated
    @Test fun anAbilityNamesItsTarget() {
        val ability = StackItem(ObjectId.StackItem("a1"), StackItemKind.Activated, bob, herald, null, AbilityOrigin.Printed(0, 0),
            targets = mapOf("target" to listOf(TargetRef.Player(alice))), timestamp = 31)
        val event = GameEvent("e", "p", "bob", EnginePart(0, listOf(
            EngineStep(Input.Activate(bob, herald, AbilityOrigin.Printed(0, 0)), listOf(Change.StackItemAdded(ability, 0))))))
        assertEquals("Bob activated Ember Herald's ability targeting you", text(event, "alice"))
        assertEquals("You activated Ember Herald's ability targeting Alice", text(event, "bob"))
        // A spell targeting that ability on the Stack names it by its source, in its controller's colour
        val onStack = GameState.of(players = state.players, objects = state.objects, battlefield = state.battlefield,
            stack = listOf(spark, ability.id), stackItems = mapOf(ability.id to ability), turn = state.turn)
        assertEquals("You cast Storm Spark targeting Ember Herald's ability",
            EngineEventText.of(cast(card(ability.id)), onStack, "bob", names))
    }

    // HIS-245, HIS-091 (owner 2026-11-21): what was attacked: a player, a planeswalker, a battle, with how many each
    @Test fun anAttackNamesWhatItAttacked() {
        fun attack(vararg attacks: Attack) = GameEvent("e", "p", "bob", EnginePart(0, listOf(
            EngineStep(Input.DeclareAttackers(bob, attacks.toList()), emptyList()))))
        val atAlice = attack(Attack(herald, TargetRef.Player(alice)), Attack(raider, TargetRef.Player(alice)))
        assertEquals("Bob attacked you with 2", text(atAlice, "alice"))
        assertEquals("You attacked Alice with 2", text(atAlice, "bob"))
        val spread = attack(Attack(herald, TargetRef.Player(alice)), Attack(raider, card(sage)), Attack(bobCub, card(siege)))
        assertEquals("Bob attacked you with 1, your Ember Sage with 1 and your Ember Siege with 1", text(spread, "alice"))
        assertEquals("You attacked Alice with 1, Alice's Ember Sage with 1 and Alice's Ember Siege with 1", text(spread, "bob"))
        assertEquals(listOf("Bob" to "bob", " attacked " to null, "you" to "alice", " with 1, " to null, "your Ember Sage" to "alice",
            " with 1 and " to null, "your Ember Siege" to "alice", " with 1" to null), pieces(spread, "alice"))
        assertEquals("Bob: no attack", text(attack(), "alice"))
    }

    // HIS-244, HIS-062: a hidden target reads only as the viewer may see it, with no name to colour; its owner reads its name
    @Test fun hiddenTargetsAreNeverNamed() {
        assertEquals("You cast Storm Spark targeting Alice's face-down creature", text(cast(card(hidden)), "bob"))
        assertEquals("Bob cast Storm Spark targeting your face-down Ember Lurker", text(cast(card(hidden)), "alice"))
        assertEquals("You cast Storm Spark targeting a card in Alice's hand", text(cast(card(inHand)), "bob"))
        assertEquals("Bob cast Storm Spark targeting Ember Secret in your hand", text(cast(card(inHand)), "alice"))
        assertEquals("You cast Storm Spark targeting a card in Alice's library", text(cast(card(inLibrary)), "bob"))
        assertEquals("Bob cast Storm Spark targeting a card in your library", text(cast(card(inLibrary)), "alice"))
        for (target in listOf(hidden, inHand, inLibrary)) {
            val marks = EngineEventText.marks(cast(card(target)), state, "bob", names)
            assertTrue(marks.none { "Ember Lurker" in it.text || "Ember Secret" in it.text || "Ember Depths" in it.text }, "$marks")
            assertTrue(pieces(cast(card(target)), "bob").none { (text, _) -> "Ember" in text }, "${pieces(cast(card(target)), "bob")}")
        }
        assertEquals(listOf("You" to "bob", " cast " to null, "Storm Spark" to "bob", " targeting a card in " to null, "Alice" to "alice",
            "'s hand" to null), pieces(cast(card(inHand)), "bob"))
    }

    // HIS-244: a public card off the battlefield by where it is; a target that left since reads as it was when targeted
    @Test fun whereTheTargetWas() {
        assertEquals("You cast Storm Spark targeting Ember Bones in Alice's graveyard", text(cast(card(inGraveyard)), "bob"))
        assertEquals(listOf("You" to "bob", " cast " to null, "Storm Spark" to "bob", " targeting " to null,
            "Ember Bones in Alice's graveyard" to "alice"), pieces(cast(card(inGraveyard)), "bob"))
        assertEquals("You cast Storm Spark targeting Alice's Ash Pup", text(cast(card(gone)), "bob"))
        assertEquals("Bob cast Storm Spark targeting your Ash Pup", text(cast(card(gone)), "alice"))
    }
}
