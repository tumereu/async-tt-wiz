package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05 (battlefield placement, Arena style): the battlefield lays itself out, identical permanents lie in one
 * stack and a card with Equipment is one group with it; a tap on a group acts at once when it can mean only one thing,
 * else the first tap opens the group and the next one acts on the member tapped.
 */
class BattlefieldGroupsTest : E2e() {
    private fun E2ePlayer.tapped(ids: List<ObjectId>) = ids.count { state.obj(it)?.tapped == true }
    private fun E2ePlayer.pool() = state.player(me).manaPool.size

    /** Every Activate Alice's device has made on its line, by the source card's id. */
    private fun activated(): List<String> = alice.engine { device.session.events }.flatMap { e ->
        e.engine?.steps.orEmpty().mapNotNull { (it.input as? Input.Activate)?.source?.let(::atw) } }

    /** Alice casts the free [name] from her hand and waits for it on her battlefield (Bob playing along). */
    private fun cast(name: String) {
        val before = alice.field(name).size
        alice.playFromHand(name, this)
        waitUntil("$name on Alice's battlefield", along = listOf(bob)) { alice.field(name).size > before && alice.inOwnMain() }
    }

    // TBL-053, TBL-054, TBL-130, TBL-122: two identical Ember Shards lie in one stack (×2); a tap on the stack taps one of
    // them for its mana at once (no menu, no opening); the next tap taps the other, the untapped one; the two stay one stack
    @Test fun aTapOnAStackOfIdenticalCardsTapsOneOfThem() {
        startGame(TestDeck("Shards", listOf(TestCards.mountain to 20, TestCards.shard to 20)),
            aliceSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { a, _ -> hand(a).count { it == "Ember Shard" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        cast("Ember Shard")
        cast("Ember Shard")
        val shards = alice.field("Ember Shard")
        assertEquals(2, shards.size)
        val stacked = hasContentDescription("Ember Shard, owner ${alice.name},", substring = true) and
            hasContentDescription("stack of 2", substring = true)
        waitUntil("the Shards in one stack on Alice's screen") { alice.shows(stacked) }
        val pool = alice.pool()
        alice.battlefieldCard("Ember Shard").performClick()
        waitUntil("one Shard tapped for its mana") { alice.tapped(shards) == 1 && alice.pool() == pool + 1 }
        assertTrue(!alice.shows(hasContentDescription("Card action ring")), "no menu")
        assertTrue(alice.shows(stacked), "tapped or not, still one stack: ${alice.screen()}")
        alice.battlefieldCard("Ember Shard").performClick()
        waitUntil("the other Shard tapped too") { alice.tapped(shards) == 2 && alice.pool() == pool + 2 }
    }

    // TBL-130, TBL-117, TBL-053: Alice's Ash Hound wears an Ember Blade; both have an ability she can use now (the pump,
    // the Equip), so the first tap on the Hound only opens the group (the Blade beside it, nothing used); the second tap on
    // the Hound pumps it, which closes the group again (the Blade back behind the Hound)
    @Test fun anEquippedCreatureOpensOnTheFirstTapAndActsOnTheSecond() {
        startGame(TestDeck("Blades", listOf(TestCards.mountain to 10, TestCards.ashHound to 10, TestCards.pup to 10, TestCards.blade to 10)),
            aliceSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { a, _ -> listOf("Mountain", "Ash Hound", "Ash Pup", "Ember Blade").all { it in hand(a) } })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        cast("Ash Hound")
        cast("Ash Pup")
        cast("Ember Blade")
        val hound = alice.field("Ash Hound").single()
        val blade = alice.field("Ember Blade").single()
        // Equip: a tap on the Blade (its one ability), then on the Hound as its target
        alice.battlefieldCard("Ember Blade").performClick()
        waitUntil("the Equip asking for its target") { alice.shows(hasContentDescription("Choose targets")) }
        alice.battlefieldCard("Ash Hound").performClick()
        // ... and the Equip on the server: activated() counts confirmed events only, and Bob's own pass racing Alice's
        // assumed one can make her Equip be sent again later (counted after the snapshot below otherwise)
        waitUntil("the Blade on the Hound", along = listOf(bob)) { alice.state.obj(blade)?.attachedTo?.let { (it as? arcanum.engine.state.TargetRef.Object)?.id == hound } == true &&
            alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } }
        val activations = activated().size
        fun bounds(name: String) = alice.battlefieldCard(name).fetchSemanticsNode().boundsInRoot
        waitUntil("the Blade peeking out above the Hound") { bounds("Ember Blade").top < bounds("Ash Hound").top }

        alice.battlefieldCard("Ash Hound").performClick()
        waitUntil("the group open: the Blade beside the Hound") { bounds("Ember Blade").left >= bounds("Ash Hound").right - 1f }
        run(skip = java.time.Duration.ofMillis(500))
        assertEquals(activations, activated().size, "the first tap used nothing")

        alice.battlefieldCard("Ash Hound").performClick()
        waitUntil("the Hound's pump activated", along = listOf(bob)) { activated().drop(activations).contains(atw(hound)) }
        waitUntil("the group closed again") { bounds("Ember Blade").top < bounds("Ash Hound").top }
        assertTrue(atw(blade) !in activated().drop(activations), "the Blade wasn't used")
    }
}
