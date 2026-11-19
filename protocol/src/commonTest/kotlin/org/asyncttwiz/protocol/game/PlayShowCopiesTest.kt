package org.asyncttwiz.protocol.game

import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent as EngineEvent
import arcanum.engine.change.Meaning
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HIS-231, D6 (owner 2026-11-19, problem report MDLPM7): a copy of a spell or ability put on the Stack by something resolving (CR 707.10) is
 * shown like a play to every player, its controller too, flying in from what copied it (HIS-112's family).
 */
class PlayShowCopiesTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val bre = ObjectId.Card(CardId("alice.7"), 0)
    private val kirol = ObjectId.Card(CardId("alice.9"), 0)
    private val copy = ObjectId.StackItem("copy:221")

    private fun event(): GameEvent {
        val item = StackItem(copy, StackItemKind.Triggered, alice, bre, null, timestamp = 221, copyOf = ObjectId.StackItem("trigger:219"))
        return GameEvent("e1", "root", alice.value, EnginePart(0, listOf(EngineStep(null, listOf(
            Change.StackItemAdded(item, 1, listOf(Meaning.Copied)),
            Change.Happened(EngineEvent.Copied(alice, copy, kirol)))))))
    }

    @Test fun aCopyIsShownToBothPlayersFromItsCopier() {
        for (viewer in listOf(alice, bob)) {
            val shown = GameDevice.showsIn(event(), viewer).single()
            assertEquals(GameDevice.PlayKind.Copied, shown.kind)
            assertEquals(copy, shown.entry)
            assertEquals(kirol.card, shown.card, "it flies in from the copier")
            assertEquals(alice, shown.by)
        }
    }
}
