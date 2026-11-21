package org.asyncttwiz.android

import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.AbilityOrigin
import org.asyncttwiz.protocol.game.GameDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HIS-112, HIS-031 (owner, 2026-11-20): the other player's special action shows as a play does, but never goes on the Stack: it
 * shows by itself, keeps no Stack entry off the table, keeps its card as it was until its play lands in it, and then the card
 * flashes.
 */
class SpecialActionPlayTest {
    private val bob = PlayerId("bob")
    private val room = ObjectId.Card(CardId("bob.1"), 0)
    private val unlock = GameDevice.PlayShow("e1", bob, room, GameDevice.PlayKind.Special, room.card, ability = AbilityOrigin.Rule("unlock-right"))
    private val trigger = GameDevice.PlayShow("e1", bob, ObjectId.Card(CardId("bob.9"), 1), GameDevice.PlayKind.Triggered, room.card)

    // HIS-112, HIS-237, HIS-134 (owner, 2026-11-20): an unlock shows by itself, before the trigger it put on the Stack in the same event
    @Test fun aSpecialActionShowsByItselfBeforeItsTriggers() {
        assertEquals(listOf(unlock), playShowGroup(listOf(unlock, trigger), emptyList()))
        assertEquals(listOf(trigger), playShowGroup(listOf(unlock, trigger), listOf(unlock)))
        val cast = trigger.copy(kind = GameDevice.PlayKind.Cast, entry = ObjectId.Card(CardId("bob.2"), 1))
        assertEquals(listOf(cast), playShowGroup(listOf(cast, unlock), emptyList()), "a play before it in its event shows without it")
    }

    // HIS-112, HIS-238, HIS-132 (owner, 2026-11-20): a special action keeps no Stack entry off the table, but its card as it was until it lands;
    // landing in its card runs the table's flash and ends it there
    @Test fun aSpecialActionHoldsItsCardUntilItLands() {
        val animation = StackAnimation("alice") { 0L }
        assertEquals(setOf("entry:${trigger.entry}"), animation.hidden(listOf(unlock, trigger)))
        assertEquals(setOf("bob.1"), animation.heldCards(listOf(unlock, trigger)))
        val flashed = mutableListOf<GameDevice.PlayShow>()
        animation.specialLanded = { flashed += it }
        animation.land(unlock)
        assertEquals(listOf(unlock), flashed)
        assertEquals(emptySet(), animation.heldCards(listOf(unlock, trigger)), "landed: the card changes")
        assertTrue(animation.landed.isEmpty(), "no Stack entry appears for it")
    }

    // HIS-031, HIS-237 (owner, 2026-11-20): the special action's flash starts as its play lands, the motion's cursor kept
    @Test fun theLandingFlashStartsThen() {
        var now = 0L
        val changes = TableChanges { now }
        changes.applyEngine("c1", jump = false, emptyList(), emptySet())
        now = 5_000
        changes.flash(listOf(TableChange(TableChangeKey("bob.1", TableChangeKind.SPECIAL), owner = "bob")))
        val active = assertNotNull(changes.active[TableChangeKey("bob.1", TableChangeKind.SPECIAL)])
        assertEquals(5_000, active.started)
        assertTrue(changes.animates("c1"), "the motion's cursor as it was")
    }

    // HIS-237: a face-down card a special action left (a foretold card) still flashes, as a control change does; its values stay hidden
    @Test fun theFlashShowsOnACoveredCard() {
        val (play, id) = ChangeTargetFixture.stack(projection = false, covered = true)
        val items = ChangeTargetFixture.scene(play).items.associateBy { it.id }
        assertTrue(assertNotNull(tableChangeTarget(TableChangeKey(id, TableChangeKind.SPECIAL), items::get)).covered)
    }
}
