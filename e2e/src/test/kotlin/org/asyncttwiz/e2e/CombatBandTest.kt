package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.Engine
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.DECLARING_BAND_TAG
import org.asyncttwiz.android.DECLARING_CHIP_TAG
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The combat band (owner playtest 2026-11-06): while Alice declares attackers with none staged the band between the
 * battlefields stays collapsed with Bob's target chip; she stages two attackers by tapping her creatures (nothing covers
 * them), which opens it, and declares with the main button; Bob blocks one of them on the band's lanes; the combat damage
 * is dealt. While each declares, their combat band glows orange with the declaring chip at their table's top (owner
 * 2026-11-21, E13), and only on the declaring player's table.
 */
class CombatBandTest : E2e() {
    /** Alice's Ember Cubs that can attack: untapped, not summoning sick. */
    private fun readyCubs() = alice.state.let { s ->
        val view = Engine.view(s)
        alice.field("Ember Cub").filter { s.obj(it)?.tapped == false && view[it]?.summoningSick == false }
    }

    /** Taps [player]'s main button while it passes or goes on (not a declaration); also at a stop at the other
     * player's assumption about them (HIS-095: Bob's pass after Alice's attack is assumed at once, HIS-096, HIS-180). */
    private fun goOn(player: E2ePlayer) = player.once {
        if (player.status == GameDevice.Status.YourMove || player.status is GameDevice.Status.Stopped) player.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(player::tap)
    }

    /** Alice casts an Ember Cub from her hand (Autotap) and is back in her Main 1 with it on the battlefield. */
    private fun castCub() {
        val before = alice.field("Ember Cub").size
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on Alice's battlefield", along = listOf(bob)) { alice.field("Ember Cub").size == before + 1 && alice.inOwnMain() }
    }

    /** CMB-095, CMB-097: whether [player]'s table shows the declaring chip and the orange combat band. */
    private fun declaringShown(player: E2ePlayer) = player.shows(hasTestTag(DECLARING_CHIP_TAG)) && player.shows(hasTestTag(DECLARING_BAND_TAG))
    private fun noneDeclaringShown(player: E2ePlayer) = !player.shows(hasTestTag(DECLARING_CHIP_TAG)) && !player.shows(hasTestTag(DECLARING_BAND_TAG))

    /** The number of cards in [owner]'s [zone] on [player]'s table. */
    private fun count(player: E2ePlayer, zone: PlayZone, owner: E2ePlayer) =
        player.shown.board.board.containers.filter { it.zone == zone && it.accountId == owner.id }.sumOf { it.cards.size }

    /**
     * CMB-095, CMB-096 (owner 2026-11-22, F2): [player]'s chip reads only [chip], and their table dimmed their hand and all of
     * [other]'s permanents (as its log says).
     */
    private fun declaringLooks(player: E2ePlayer, other: E2ePlayer, chip: String) {
        assertTrue(player.shows(hasTestTag(DECLARING_CHIP_TAG) and hasContentDescription(chip)), "the chip reads \"$chip\": ${player.screen()}")
        val hand = count(player, PlayZone.HAND, player)
        val others = count(player, PlayZone.BATTLEFIELD, other)
        assertTrue(hand > 0 && others > 0, "cards in ${player.name}'s hand and on ${other.name}'s battlefield")
        val logged = "$chip on ${player.id.take(8)}'s table: dimmed $hand hand card(s), $others of the other players' permanents"
        waitUntil("${player.name}'s dims logged: $logged") { logged in AppLog.text() }
    }

    // CMB-001, CMB-007, CMB-013, CMB-028, CMB-031, CMB-034, CMB-095, CMB-096, CMB-097, TBL-128, TRN-063, TRN-113
    @Test fun attackersAreStagedInTheBandAndBlockedOnItsLanes() {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.herald),
            deal = { a, b -> "Mountain" in hand(a) && hand(a).count { it == "Ember Cub" } >= 2 && "Mountain" in hand(b) &&
                "Ember Herald" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        // Turn 1: a Mountain and a Cub
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        castCub()
        alice.hold("To combat")
        // Bob's turn: a Mountain and an Ember Herald, so he has creatures to block with
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ember Herald", this)
        waitUntil("Bob's Herald and its token", along = listOf(alice)) {
            bob.field("Ember Herald").isNotEmpty() && bob.state.battlefield.any { bob.state.obj(it)?.token == true } && bob.inOwnMain()
        }
        bob.hold("To combat")
        // Turn 3: Alice's second Cub; then on to her turn 5 with both ready
        waitUntil("Alice's second Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        castCub()
        alice.hold("To combat")
        waitUntil("Alice's Main 1 with two ready Cubs", along = listOf(bob)) { alice.inOwnMain() && readyCubs().size == 2 }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
        // CMB-001, CMB-051, CMB-053, CMB-013, CMB-028, CMB-066 (owner playtest 2026-11-06): the collapsed band shows Bob's target chip; a tap on it
        // keeps him the active target and stages nothing; the band stays collapsed until an attacker is staged
        waitUntil("Bob's target chip in the band") { alice.showsControl("Attack ${bob.name}") }
        // CMB-095, CMB-097 (owner 2026-11-21, E13): Alice's band glows orange with the declaring chip; Bob, waiting, sees neither
        waitUntil("Alice's declaring band and chip: ${alice.screen()}") { declaringShown(alice) }
        assertTrue(noneDeclaringShown(bob), "Bob declares nothing: ${bob.screen()}")
        // CMB-095, CMB-096 (owner 2026-11-22, F2): "Declaring attackers" only; her hand and Bob's permanents dimmed
        declaringLooks(alice, bob, "Declaring attackers")
        // CMB-096, TBL-128, TBL-212 (owner 2026-11-22, F2 follow-up): a tap on a card of her dimmed hand only shakes it, saying why;
        // nothing is raised or cast
        val hand = alice.state.player(alice.me).hand
        val tapped = assertNotNull(cardName(alice.state, hand.first()))
        alice.handCard(tapped).performClick()
        waitUntil("the hand card's refusal bubble: ${alice.screen()}") {
            alice.told("Not while declaring attackers.") && alice.shows(hasTestTag("refusal-bubble"))
        }
        assertTrue(alice.raisedInHand() == null, "not raised")
        run()
        assertEquals(hand, alice.state.player(alice.me).hand, "nothing left her hand")
        assertTrue(alice.state.stack.isEmpty() && alice.state.turn.step == TurnStep.DeclareAttackers, "nothing cast, still declaring")
        assertFalse(alice.bandOpen(), "collapsed")
        alice.tap("Attack ${bob.name}")
        run()
        assertEquals("No attack", alice.stepArrow())
        assertFalse(alice.bandOpen(), "a chip opens nothing")
        // CMB-028: a tap on each ready Cub stages it in the band's lane; nothing covers them
        val ownField = alice.battlefieldBounds()
        for (n in 0..1) {
            alice.stageAttacker("Ember Cub", this, index = n)
            waitUntil("Cub ${n + 1} staged") { alice.shown.board.engine!!.table.attackDraft.orEmpty().size == n + 1 }
            assertFalse(alice.refusalSeen, "the Cub is staged")
        }
        assertEquals("Attack (2)", alice.stepArrow())
        assertTrue(alice.bandOpen(), "the band opened by itself with the first attacker")
        // CMB-007, CMB-054 (owner playtest 2026-11-11): it opened away from Alice's battlefield, which stayed where it was
        waitUntil("the band open and settled") { alice.battlefieldBounds() == ownField }
        val life = alice.state.player(bob.me).life
        alice.declare("Attack (2)", this)
        // Bob reaches his blocks (passing where he may respond to the attack)
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        // CMB-095, CMB-097: now Bob's band glows with the blockers' chip, and Alice's no longer
        waitUntil("Bob's declaring band and chip: ${bob.screen()}") { declaringShown(bob) && noneDeclaringShown(alice) }
        // CMB-095, CMB-096 (F2): "Declaring blockers" only; his hand and Alice's permanents dimmed (the attackers' copies on the band aren't)
        declaringLooks(bob, alice, "Declaring blockers")
        // CMB-034: his Herald picked as the blocker, then Alice's first attacking Cub's copy in the band blocks it
        bob.pickBlocker("Ember Herald", this)
        run(skip = java.time.Duration.ofSeconds(1)) // past the double-tap window: the tap is a single tap
        bob.bandCard("Ember Cub", owner = alice).performClick()
        waitUntil("Bob's block staged") { bob.stepArrow() == "Block (1)" }
        bob.declare("Block (1)", this)
        waitUntil("the combat damage", along = listOf(bob)) {
            goOn(alice)
            alice.state.player(bob.me).life < life
        }
        assertEquals(life - 2, alice.state.player(bob.me).life, "one Cub unblocked")
        waitUntil("nobody declaring any more") { noneDeclaringShown(alice) && noneDeclaringShown(bob) }
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }
}
