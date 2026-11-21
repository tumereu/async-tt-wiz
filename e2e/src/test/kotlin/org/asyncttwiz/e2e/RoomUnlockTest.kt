package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import org.asyncttwiz.android.EngineTableProbe
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.android.PlayShowProbe
import org.asyncttwiz.android.TableBounds
import org.asyncttwiz.android.TableChange
import org.asyncttwiz.android.TableChangeKey
import org.asyncttwiz.android.TableChangeKind
import org.asyncttwiz.android.TableItemKind
import org.asyncttwiz.android.TableScene
import org.asyncttwiz.android.playShowStart
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import kotlin.test.assertNotNull
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.Fault
import org.asyncttwiz.protocol.Faults
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prod faults 9SF97R, P86WUK (0.2.1, 2026-11-19): unlocking a Room's door was offered as the placeholder "Special action", and
 * (owner, 2026-11-19) the unlock showed nothing to the other player or in the history: a special action doesn't use the Stack
 * (CR 116.2m, 709.5e), so no play was shown for it, and its event read only as an ability activated.
 */
class RoomUnlockTest : E2e() {
    private val faults = mutableListOf<Fault>()
    private var sink: ((Fault) -> Unit)? = null
    @Before fun watchFaults() { sink = Faults.sink; Faults.sink = { faults += it; sink?.invoke(it) } }
    @After fun stopWatching() { Faults.sink = sink }

    /** Alice's Rooms on the battlefield, by either name the table gives one (a locked door has no name, CR 709.5). */
    private fun roomsOnField() = alice.field("Ember Foyer").size + alice.field("Ember Foyer // Ember Vault").size

    // TBL-115, TBL-122, HIS-091, HIS-031: Alice casts her Room's Ember Foyer, then a tap on the Room unlocks its other door (its only use
    // now, "Unlock Ember Vault"); its trigger gains her 2 life, and both tables tell it: "You unlocked Ember Vault" on hers,
    // "<Alice> unlocked Ember Vault" on Bob's; no placeholder anywhere
    @Test fun unlockingADoorIsNamedAndToldToBoth() {
        startGame(TestDeck("Rooms", listOf(TestCards.mountain to 20, TestCards.foyer to 20)), TestDeck.glimmers(), deal = { a, _ ->
            hand(a).any { it.startsWith("Ember Foyer") } && "Mountain" in hand(a)
        })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        waitUntil("Alice's game settled") { alice.settled(this) }
        val room = listOf("Ember Foyer // Ember Vault", "Ember Foyer").first { runCatching { alice.handCard(it) }.isSuccess }
        alice.handCard(room).performClick()
        waitUntil("the Room's chips: ${alice.screen()}") { alice.shows(hasContentDescription("Way Cast {R}")) }
        alice.node(hasContentDescription("Way Cast {R}")).performClick()
        waitUntil("the Foyer on the battlefield, its trigger resolved", along = listOf(bob)) {
            roomsOnField() == 1 && alice.inOwnMain() && alice.state.stack.isEmpty() &&
                alice.engine { device.session.pending.isEmpty() } && alice.state.player(alice.me).life == 21
        }
        waitUntil("Alice's game settled") { alice.settled(this) }
        listOf("Ember Foyer", "Ember Foyer // Ember Vault").firstNotNullOf { runCatching { alice.battlefieldCard(it) }.getOrNull() }.performClick()
        waitUntil("the Vault unlocked, its trigger resolved", along = listOf(bob)) {
            alice.inOwnMain() && alice.state.stack.isEmpty() && alice.engine { device.session.pending.isEmpty() } &&
                alice.state.player(alice.me).life == 23
        }
        waitUntil("Alice's table told her: ${alice.shownTexts}") { alice.told("You unlocked Ember Vault") }
        waitUntil("Bob's table told him: ${bob.shownTexts}", along = listOf(alice)) { bob.told("${alice.name} unlocked Ember Vault") }
        assertTrue(alice.shownTexts.none { "Special action" in it } && bob.shownTexts.none { "Special action" in it })
        assertEquals(emptyList(), faults.filter { "without text" in it.what || "placeholder" in it.what }, "no placeholder")
    }

    // HIS-112, HIS-237, HIS-238, HIS-031 (owner, 2026-11-20: Alice unlocked a Room in prod and nothing showed it on the other table): Alice's
    // unlock shows on Bob's table large in the middle as an ability does ("<Alice> unlocked Ember Vault"), then flies into the Room
    // itself instead of to the Stack, and the Room flashes as it lands; then its trigger shows and resolves
    @Test fun theUnlockShowsOnTheOtherTableAndFliesIntoTheRoom() {
        val flights = java.util.concurrent.CopyOnWriteArrayList<Triple<GameDevice.PlayShow, TableBounds?, TableScene>>()
        val flashes = java.util.concurrent.CopyOnWriteArrayList<List<TableChange>>()
        try {
            startGame(TestDeck("Rooms", listOf(TestCards.mountain to 20, TestCards.foyer to 20)), TestDeck.glimmers(), deal = { a, _ ->
                hand(a).any { it.startsWith("Ember Foyer") } && "Mountain" in hand(a)
            })
            // Plays shown on Bob's table, at the longest time (3 s), so the unlock can be looked at while it shows
            org.asyncttwiz.android.HistoryPlaybackPreferences(org.asyncttwiz.appui.AppPlatform.current, bob.id).showPlaysMillis =
                org.asyncttwiz.android.HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
            bothOpen()
            bothKeep(bobAway = false)
            alice.playFromHand("Mountain", this)
            waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
            waitUntil("Alice's game settled") { alice.settled(this) }
            val room = listOf("Ember Foyer // Ember Vault", "Ember Foyer").first { runCatching { alice.handCard(it) }.isSuccess }
            alice.handCard(room).performClick()
            waitUntil("the Room's chips: ${alice.screen()}") { alice.shows(hasContentDescription("Way Cast {R}")) }
            alice.node(hasContentDescription("Way Cast {R}")).performClick()
            waitUntil("the Foyer on the battlefield, its trigger resolved, Bob's table past it", along = listOf(bob)) {
                roomsOnField() == 1 && alice.inOwnMain() && alice.state.stack.isEmpty() &&
                    alice.engine { device.session.pending.isEmpty() } && bob.shown.show == null && bob.state.stack.isEmpty()
            }
            waitUntil("Alice's game settled") { alice.settled(this) }
            PlayShowProbe.flight = { me, show, target, scene -> if (me == bob.id) flights += Triple(show, target, scene) }
            EngineTableProbe.flashed = { me, changes -> if (me == bob.id) flashes += changes }
            listOf("Ember Foyer", "Ember Foyer // Ember Vault").firstNotNullOf { runCatching { alice.battlefieldCard(it) }.getOrNull() }.performClick()
            // The frames' clock in step with the real one: a show can't end between two looks (E2e.clockInStep)
            val show = clockInStep {
                waitUntil("the unlock shown on Bob's table: ${bob.screen()}", along = listOf(alice)) {
                    bob.shown.show?.kind == GameDevice.PlayKind.Special &&
                        bob.shows(hasTestTag(PLAY_SHOW_TAG) and hasContentDescription("${alice.name} unlocked Ember Vault"))
                }
                assertTrue(flashes.isEmpty(), "the Room flashes only once the play lands")
                assertNotNull(bob.shown.show)
            }
            val card = assertNotNull(show.card).value
            waitUntil("the unlock flown into the Room and the Room flashed", along = listOf(alice)) {
                flights.any { it.first == show } && flashes.isNotEmpty()
            }
            val (_, target, scene) = flights.first { it.first == show }
            val roomItems = scene.items.filter { it.kind == TableItemKind.CARD && it.cardId == card && it.zone == PlayZone.BATTLEFIELD && !it.projection }
            val roomItem = roomItems.firstOrNull { it.visible.isNotEmpty() } ?: roomItems.first()
            assertEquals(playShowStart(assertNotNull(scene.boardArea), roomItem.bounds), target, "into the Room, not the Stack")
            assertTrue(flashes.single().any { it.key == TableChangeKey(card, TableChangeKind.SPECIAL) }, "the Room flashes: ${flashes.single()}")
            waitUntil("Bob's table past the unlock and its trigger", along = listOf(alice)) {
                bob.shown.show == null && bob.state.player(alice.me).life == 23
            }
            assertEquals(emptyList(), faults.filter { "without" in it.what || "placeholder" in it.what }, "no placeholder")
        } finally {
            PlayShowProbe.flight = null
            EngineTableProbe.flashed = null
        }
    }
}
