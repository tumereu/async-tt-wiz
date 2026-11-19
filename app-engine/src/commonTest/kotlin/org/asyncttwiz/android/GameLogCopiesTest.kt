@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.android

import arcanum.api.CardDefinition
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.state.CardId
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerState
import arcanum.engine.state.PriorityState
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TurnState
import arcanum.engine.state.of
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D10 (problem report MDLPM7, owner rule 2026-11-09: logging with problem reports): the game log tells a copy of a trigger from its
 * original, by id, what each is and what the copy copies (CR 707.10).
 */
class GameLogCopiesTest {
    @Test fun aCopiedTriggerAndItsOriginalReadApart() {
        val alice = PlayerId("alice")
        val herald = ObjectId.Card(CardId("alice.1"), 0)
        val original = ObjectId.StackItem("trigger:5")
        val copy = ObjectId.StackItem("copy:9")
        fun item(id: ObjectId, copyOf: ObjectId?) = StackItem(id, StackItemKind.Triggered, alice, herald, null, timestamp = 5, copyOf = copyOf)
        val state = GameState.of(
            players = listOf(PlayerState(alice, 20), PlayerState(PlayerId("bob"), 20)),
            objects = mapOf(herald to GameObject(herald, alice, alice, Zone.Battlefield,
                CardDefinition(Layout.Normal, listOf(FaceDefinition("Ember Herald"))), timestamp = 1)),
            battlefield = listOf(herald), stack = listOf(original, copy),
            stackItems = mapOf(original to item(original, null), copy to item(copy, original)),
            turn = TurnState(3, alice, TurnStep.End), priority = PriorityState(alice))
        val line = GameLog("game-1", alice).board(state)
        assertTrue("stack:trigger:5 Ember Herald's ability" in line, line)
        assertTrue("stack:copy:9 copy of Ember Herald's triggered ability (copies stack:trigger:5)" in line, line)
    }
}
