package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.asyncttwiz.android.COSTS_BACK_TAG
import org.asyncttwiz.android.COSTS_OPTIONAL_TAG
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CMB-098–CMB-101 (owner, 2026-11-25): the costs panel the main button's declaration opens when it leaves something to decide: an
 * optional cost to attack, chosen there; with Autotap off a cost to attack paid there by hand, Back to staging first; a cost to
 * block the same way.
 */
class CombatCostPanelTest : E2e() {
    private fun goOn(player: E2ePlayer) = player.once {
        if (player.status == GameDevice.Status.YourMove || player.status is GameDevice.Status.Stopped)
            player.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(player::tap)
    }

    private fun E2ePlayer.costsPanel(what: String = "attack") = shows(hasContentDescription("Costs to $what"))
    private fun E2ePlayer.mainSays(label: String, enabled: Boolean = true) =
        shows(hasTestTag("main-button") and hasContentDescription(label) and if (enabled) isEnabled() else androidx.compose.ui.test.isNotEnabled())

    // CMB-098, CMB-100, CMB-037 (CR 508.1g, 701.43d): Alice casts her Ember Charger (haste) and stages it: Attack (1) opens the costs
    // panel with nothing declared; a tap on the Charger's card pays its exert (Bob's phone shows the amber Exert chip); Attack on the
    // main button declares it, exerted
    @Test fun anOptionalCostToAttackIsChosenInThePanel() {
        startGame(TestDeck("Chargers", listOf(TestCards.mountain to 20, TestCards.charger to 20)), TestDeck.thirds(),
            deal = { a, _ -> "Ember Charger" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Charger", this)
        waitUntil("the Charger", along = listOf(bob)) { alice.field("Ember Charger").isNotEmpty() && alice.inOwnMain() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ember Charger", this)
        alice.declare("Attack (1)", this)
        waitUntil("the costs panel: ${alice.screen()}") { alice.costsPanel() && alice.mainSays("Attack") }
        assertTrue(alice.state.combat == null, "nothing declared yet")
        alice.tagged(COSTS_OPTIONAL_TAG).performClick()
        waitUntil("the exert paid: ${alice.screen()}") { alice.shows(hasTestTag(COSTS_OPTIONAL_TAG) and hasStateDescription("paid")) }
        waitUntil("the amber Exert chip on Bob's phone", along = listOf(alice)) { bob.shows(hasContentDescription("Pays Exert")) }
        alice.tap("Attack")
        waitUntil("the Charger attacking, exerted", along = listOf(bob)) {
            val charger = alice.field("Ember Charger").single()
            alice.state.combat?.attackers?.map { it.id } == listOf(charger) &&
                arcanum.engine.Engine.view(alice.state)[charger]?.mechanics.orEmpty().any { it.mechanic == arcanum.api.Mechanic.DoesntUntap }
        }
        assertFalse(alice.costsPanel(), "the panel closed")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // CMB-098, CMB-099, CMB-100, CMB-101 (CR 508.1h, 508.1i, 508.1j): Autotap off, Bob's Ember Bulwark and Chime make Alice's Pup
    // cost {2} to attack: Attack (1) opens the panel saying what is still owed; Back returns to the staging, nothing declared; again,
    // her two Mountains tapped on the table pay it and Attack declares
    @Test fun aCostToAttackIsPaidByHandInThePanel() {
        startGame(TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20)),
            TestDeck("Wards", listOf(TestCards.mountain to 14, TestCards.bulwark to 13, TestCards.chime to 13)),
            aliceSettings = PlaySettings(autotap = false),
            deal = { a, b -> "Ash Pup" in hand(a) && hand(a).count { it == "Mountain" } >= 2 && "Ember Bulwark" in hand(b) && "Ember Chime" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ash Pup", this)
        waitUntil("the Pup", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's first Mountain", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        for (card in listOf("Ember Bulwark", "Ember Chime")) {
            bob.playFromHand(card, this)
            waitUntil("Bob's $card", along = listOf(alice)) { bob.field(card).isNotEmpty() && bob.inOwnMain() }
        }
        bob.hold(requireNotNull(bob.stepArrow()))
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's second Mountain", along = listOf(bob)) { alice.field("Mountain").size == 2 && alice.inOwnMain() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ash Pup", this)
        alice.declare("Attack (1)", this)
        val owed = "Not paid: Still owed {2}: produce the mana on the board."
        waitUntil("the costs panel, nothing paying: ${alice.screen()}") { alice.costsPanel() && alice.shows(hasContentDescription(owed)) }
        assertTrue(alice.mainSays("Attack", enabled = false) || alice.shows(hasTestTag("main-button") and hasContentDescription("Attack")),
            "Attack on the main button: ${alice.screen()}")
        // CMB-100: Back to the staging, nothing declared
        alice.tagged(COSTS_BACK_TAG).performClick()
        waitUntil("back at the staging: ${alice.screen()}") { !alice.costsPanel() && alice.stepArrow() == "Attack (1)" }
        assertTrue(alice.state.combat == null && alice.state.turn.step == TurnStep.DeclareAttackers, "nothing declared")
        alice.declare("Attack (1)", this)
        waitUntil("the costs panel again") { alice.costsPanel() && alice.shows(hasContentDescription(owed)) }
        // The Mountains lie in one stack: a tap on it adds its topmost one not paying yet (TBL-130), which then lies apart
        alice.battlefieldCard("Mountain").performClick()
        waitUntil("one Mountain paying: ${alice.screen()}") {
            alice.shows(hasContentDescription("Not paid: Not enough to pay {2}: produce more mana on the board."))
        }
        run(skip = java.time.Duration.ofSeconds(1))
        alice.battlefieldCard("Mountain").performClick()
        waitUntil("paid by the two Mountains, Attack usable: ${alice.screen()}") { alice.mainSays("Attack") && !alice.shows(hasContentDescription(owed)) }
        alice.tap("Attack")
        waitUntil("the Pup attacking, both Mountains tapped for it", along = listOf(bob)) {
            alice.state.combat?.attackers?.map { it.id } == alice.field("Ash Pup") &&
                alice.field("Mountain").all { alice.state.obj(it)?.tapped == true }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // CMB-098, CMB-099 (CR 509.1d): Alice's Ember Tollward attacks ("creatures can't block unless their controller pays {1}"); Bob,
    // Autotap off, blocks with his Ash Pup: Block (1) opens his panel, "Costs to block"; his Mountain tapped on the table pays it and
    // Block declares
    @Test fun aCostToBlockIsPaidInThePanel() {
        startGame(TestDeck("Tollwards", listOf(TestCards.mountain to 20, TestCards.tollward to 20)),
            TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20)), bobSettings = PlaySettings(autotap = false),
            deal = { a, b -> "Ember Tollward" in hand(a) && "Ash Pup" in hand(b) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.hold("To combat")
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ash Pup", this)
        waitUntil("Bob's Pup", along = listOf(alice)) { bob.field("Ash Pup").isNotEmpty() && bob.inOwnMain() }
        bob.hold(requireNotNull(bob.stepArrow()))
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        alice.playFromHand("Ember Tollward", this)
        waitUntil("the Tollward", along = listOf(bob)) { alice.field("Ember Tollward").isNotEmpty() && alice.inOwnMain() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ember Tollward", this)
        alice.declare("Attack (1)", this)
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        assertFalse(alice.costsPanel(), "Alice had nothing to decide")
        bob.pickBlocker("Ash Pup", this)
        run(skip = java.time.Duration.ofSeconds(1))
        bob.bandCard("Ember Tollward", owner = alice).performClick()
        waitUntil("Bob's block staged") { bob.stepArrow() == "Block (1)" }
        bob.declare("Block (1)", this)
        waitUntil("Bob's costs panel: ${bob.screen()}") {
            bob.costsPanel("block") && bob.shows(hasContentDescription("Not paid: Still owed {1}: produce the mana on the board."))
        }
        bob.battlefieldCard("Mountain").performClick()
        waitUntil("paid by his Mountain: ${bob.screen()}") { bob.mainSays("Block") }
        bob.tap("Block")
        waitUntil("the Pup blocking, the Mountain tapped for it", along = listOf(alice)) {
            bob.state.combat?.blockers?.map { it.id } == bob.field("Ash Pup") && bob.field("Mountain").all { bob.state.obj(it)?.tapped == true }
        }
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal: ${alice.shownTexts} ${bob.shownTexts}")
    }
}
