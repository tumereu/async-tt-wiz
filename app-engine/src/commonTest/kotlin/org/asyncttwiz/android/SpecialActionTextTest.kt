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
import arcanum.engine.view.AbilityOrigin
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HIS-091 (prod faults 9SF97R, P86WUK, owner 2026-11-19: unlocking a door showed nothing anywhere): a special action's event
 * reads as what it did, to both players, instead of an ability activated.
 */
class SpecialActionTextTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val names = mapOf("alice" to "Alice", "bob" to "Bob")
    private val room = ObjectId.Card(CardId("alice.1"), 0)
    private val mask = ObjectId.Card(CardId("alice.2"), 0)
    private val state = GameState.of(
        players = listOf(PlayerState(alice, 20), PlayerState(bob, 20)),
        objects = mapOf(
            room to GameObject(room, alice, alice, Zone.Battlefield,
                CardDefinition(Layout.Split, listOf(FaceDefinition("Lamplit Parlor"), FaceDefinition("Dusty Attic"))), timestamp = 1),
            mask to GameObject(mask, alice, alice, Zone.Battlefield, CardDefinition(Layout.Normal, listOf(FaceDefinition("Ember Mask"))),
                timestamp = 2)),
        battlefield = listOf(room, mask), turn = TurnState(3, alice, TurnStep.PrecombatMain), priority = PriorityState(alice))

    private fun event(source: ObjectId, rule: String) = GameEvent("e", "p", "alice",
        EnginePart(0, listOf(EngineStep(Input.Activate(alice, source, AbilityOrigin.Rule(rule)), emptyList()))))

    // CR 709.5e: the door's name, for the one who unlocked it and the other player
    @Test fun anUnlockReadsAsTheDoorUnlocked() {
        assertEquals("Alice unlocked Dusty Attic", EngineEventText.of(event(room, "unlock-right"), state, "bob", names))
        assertEquals("You unlocked Lamplit Parlor", EngineEventText.of(event(room, "unlock-left"), state, "alice", names))
    }

    // HIS-031 (owner 2026-11-19): the Room whose door the event unlocked pulses, in its controller's colour, as a control change does
    @Test fun anUnlockedRoomPulses() {
        fun board() = TableBoard("g", "p", "n", org.asyncttwiz.protocol.BoardState(
            players = listOf(org.asyncttwiz.protocol.PlayPlayer("alice"), org.asyncttwiz.protocol.PlayPlayer("bob")),
            cards = listOf(org.asyncttwiz.protocol.PlayCard("alice.1", "d", "alice", "bf-alice", "f", covered = false)),
            containers = listOf(org.asyncttwiz.protocol.PlayContainer("bf-alice", org.asyncttwiz.protocol.PlayZone.BATTLEFIELD, "alice",
                listOf("alice.1"))),
            turn = org.asyncttwiz.protocol.TurnState("alice")), emptyList())
        val unlock = GameEvent("e", "p", "alice", EnginePart(0, listOf(EngineStep(Input.Activate(alice, room, AbilityOrigin.Rule("unlock-right")),
            listOf(arcanum.engine.change.Change.Happened(arcanum.engine.change.GameEvent.Unlocked(alice, room, 1, fully = true)))))))
        val changes = EngineChanges.between(board(), board(), emptyMap(), emptyMap(), listOf(unlock))
        assertEquals(listOf(TableChange(TableChangeKey("alice.1", TableChangeKind.UNLOCK), owner = "alice")), changes)
        assertEquals(emptyList(), EngineChanges.between(board(), board(), emptyMap(), emptyMap(), listOf(event(room, "unlock-right"))))
    }

    // CR 701.40b: a manifested card turned face up is named, as it now shows face up
    @Test fun turningFaceUpReadsAsIt() {
        assertEquals("Alice turned Ember Mask face up", EngineEventText.of(event(mask, "manifested"), state, "bob", names))
    }
}
