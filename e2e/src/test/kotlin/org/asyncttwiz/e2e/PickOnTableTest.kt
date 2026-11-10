package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import arcanum.api.CardType
import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.TableMarks
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest, 2026-11-09: a source with several ways to pay pays the sensible one when tapped, and its corner chip changes it
 * (STK-024); the step button is the cast panel's Cast and every panel's OK, a quick second tap ignored (TRN-063, STK-052); whatever
 * is on the battlefield is picked on the battlefield with the slim bar, its OK on the step button: a payment while something resolves
 * (STK-049), a sacrifice, crew (STK-047, STK-030).
 */
class PickOnTableTest : E2e() {
    private fun deck(vararg cards: Pair<SubmittedDefinition, Int>) = TestDeck("Picks", cards.toList())

    /** A game with Alice holding at least these cards (by name, how many); both have kept. */
    private fun start(aliceDeck: TestDeck, aliceHolds: Map<String, Int>, aliceSettings: PlaySettings? = null) {
        startGame(aliceDeck, TestDeck.glimmers(), aliceSettings = aliceSettings, deal = { a, _ ->
            aliceHolds.all { (name, n) -> hand(a).count { it == name } >= n }
        })
        bothOpen()
        bothKeep(bobAway = false)
    }

    /** Alice plays each of [cards] from her hand, one by one, on the server before the next. */
    private fun plays(vararg cards: String) {
        for (card in cards) {
            val before = alice.field(card).size
            alice.playFromHand(card, this)
            waitUntil("$card on Alice's battlefield", along = listOf(bob)) {
                alice.field(card).size == before + 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
            }
        }
    }

    private fun E2ePlayer.marks(name: String, index: Int = 0): Set<String> =
        runCatching { battlefieldCard(name, index = index).fetchSemanticsNode().config.getOrElse(TableMarks) { emptySet() } }.getOrDefault(emptySet())

    private fun E2ePlayer.payment(): String? = nodes(hasContentDescription("Payment: ", substring = true)).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }?.joinToString()

    /** The main button saying [label], when it can be pressed. */
    private fun E2ePlayer.mainSays(label: String) =
        shows(hasTestTag("main-button") and hasContentDescription(label) and isEnabled())

    private fun E2ePlayer.chip(mana: String) = hasContentDescription("Pays $mana: ", substring = true)

    private val bar = hasContentDescription("Choose on the table")

    // STK-024 (owner playtest 2026-11-09), CR 601.2h: with Autotap off, a tap on Alice's Ember Fork ({R} or {U}) for Blue Note ({U})
    // takes {U}, the way that leaves nothing unpaid, though {R} is listed first; its corner chip opens its ways: {R} leaves {U} unpaid,
    // {U} pays again; Cast on the step button casts it
    @Test fun aSourceTappedPaysTheSensibleWayAndItsCornerChipChangesIt() {
        start(deck(TestCards.fork to 20, TestCards.blueNote to 20), mapOf("Ember Fork" to 1, "Blue Note" to 1), PlaySettings(autotap = false))
        plays("Ember Fork")
        alice.playFromHand("Blue Note", this)
        waitUntil("the panel, nothing paying: ${alice.screen()}") { alice.shows(hasContentDescription("Not paid: Tap sources on the table to pay {U}.")) }
        assertFalse(alice.shows(hasContentDescription("Cast panel") and androidx.compose.ui.test.hasAnyDescendant(hasClickAction() and hasContentDescription("Cast"))),
            "no Cast in the panel: ${alice.screen()}")
        alice.tap("Hide cast panel")
        waitUntil("the panel hidden") { alice.showsControl("Show cast panel") }
        alice.battlefieldCard("Ember Fork").performClick()
        waitUntil("the Fork paying {U}: ${alice.screen()}") { alice.shows(alice.chip("{U}")) && "source" in alice.marks("Ember Fork") }
        assertTrue(alice.mainSays("Cast"), "Cast on the main button: ${alice.screen()}")
        alice.node(alice.chip("{U}")).performClick()
        waitUntil("the Fork's ways") { alice.shows(hasContentDescription("Pay with {R}")) }
        alice.node(hasContentDescription("Pay with {R}")).performClick()
        waitUntil("the Fork paying {R}, Cast greyed: ${alice.screen()}") {
            alice.shows(alice.chip("{R}")) && alice.shows(hasTestTag("main-button") and hasContentDescription("Cast") and isNotEnabled())
        }
        alice.node(alice.chip("{R}")).performClick()
        waitUntil("the Fork's ways again") { alice.shows(hasContentDescription("Pay with {U}")) }
        alice.node(hasContentDescription("Pay with {U}")).performClick()
        waitUntil("the Fork paying {U} again, Cast usable") { alice.shows(alice.chip("{U}")) && alice.mainSays("Cast") }
        val hand = alice.state.player(alice.me).hand.size
        alice.tap("Cast")
        waitUntil("Blue Note resolved, the Fork tapped", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.inOwnMain() && alice.state.obj(alice.field("Ember Fork").single())?.tapped == true &&
                alice.state.player(alice.me).hand.size == hand
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // TRN-063, STK-052 (owner playtest 2026-11-09): Cast is the step button; a quick double tap on it casts once, the second tap
    // ignored whatever the button says by then
    @Test fun castIsTheStepButtonAndAQuickDoubleTapCastsOnce() {
        start(deck(TestCards.shard to 20, TestCards.redFlash to 20), mapOf("Ember Shard" to 1, "Red Flash" to 1), PlaySettings(autotap = false))
        plays("Ember Shard")
        alice.playFromHand("Red Flash", this)
        waitUntil("the panel: ${alice.screen()}") { alice.shows(hasContentDescription("Not paid: Tap sources on the table to pay {R}.")) }
        alice.tap("Hide cast panel")
        waitUntil("the panel hidden") { alice.showsControl("Show cast panel") }
        alice.battlefieldCard("Ember Shard").performClick()
        waitUntil("paid, Cast usable on the main button") { alice.mainSays("Cast") }
        val logged = PickLog.count()
        val flashes = alice.hand("Red Flash").size
        // Two taps with no time between them on this phone's clock: the second lands on whatever the button says by then
        alice.node(hasTestTag("main-button")).performClick()
        val second = alice.stepArrow() ?: alice.nodes(hasTestTag("main-button")).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }?.joinToString()
        alice.node(hasTestTag("main-button")).performClick()
        waitUntil("Red Flash on the Stack, sent", along = emptyList()) {
            alice.state.stack.size == 1 && alice.engine { device.session.pending.isEmpty() } && !alice.showsControl("Show cast panel")
        }
        repeat(20) { run() }
        // Bob doesn't play along: the Flash stays on the Stack, cast once
        assertEquals(1, alice.state.stack.size, "cast once: ${alice.screen()}")
        assertEquals(flashes - 1, alice.hand("Red Flash").size, "one Red Flash cast")
        assertTrue(PickLog.count() > logged, "the second tap (on \"$second\") was ignored, and the log says so")
    }

    // STK-049, STK-024 (owner playtest 2026-11-09), CR 118.12: "you may pay {1}" while Ember Tollkeeper's trigger resolves is the slim
    // bar "Pay {1}", the Mountain Autotap chose outlined on the table; a tap there takes it out (not paid), another puts it back; Pay is
    // the step button
    @Test fun aPaymentWhileResolvingIsPaidOnTheTable() {
        start(deck(TestCards.mountain to 20, TestCards.tollkeeper to 20), mapOf("Mountain" to 1, "Ember Tollkeeper" to 1))
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val before = alice.state.player(alice.me).hand.size
        alice.playFromHand("Ember Tollkeeper", this)
        waitUntil("Alice asked to pay {1} on the table: ${alice.screen()}", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.May && alice.shows(hasContentDescription("Pay on the table"))
        }
        assertFalse(alice.shows(hasContentDescription("Automatum choice")), "no panel: ${alice.screen()}")
        waitUntil("Autotap's Mountain outlined, Pay usable") { "source" in alice.marks("Mountain") && alice.mainSays("Pay") }
        assertTrue(alice.showsText("Don't pay"), "a decline: ${alice.screen()}")
        alice.battlefieldCard("Mountain").performClick()
        waitUntil("the Mountain taken out, not paid") {
            "source" !in alice.marks("Mountain") && alice.showsText("Not paid yet") && alice.shows(hasTestTag("main-button") and hasContentDescription("Pay") and isNotEnabled())
        }
        alice.battlefieldCard("Mountain").performClick()
        waitUntil("the Mountain back, paid") { "source" in alice.marks("Mountain") && alice.mainSays("Pay") }
        alice.tap("Pay")
        waitUntil("the cost paid and a card drawn", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.pending == null && alice.field("Ember Tollkeeper").size == 1 &&
                alice.state.player(alice.me).hand.size == before
        }
        assertTrue(alice.state.obj(alice.field("Mountain").single())!!.tapped, "the Mountain paid")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-047, STK-030 (owner playtest 2026-11-09), CR 601.2b: Altar Offering's sacrifice is picked on the table, no panel: the slim bar,
    // both Pups glowing; a tap on one sacrifices it, Autotap pays and it resolves
    @Test fun aSacrificeIsPickedOnTheTable() {
        start(deck(TestCards.shard to 14, TestCards.offering to 13, TestCards.pup to 13),
            mapOf("Ember Shard" to 1, "Altar Offering" to 1, "Ash Pup" to 2))
        plays("Ember Shard", "Ash Pup", "Ash Pup")
        val hand = alice.state.player(alice.me).hand.size
        alice.playFromHand("Altar Offering", this)
        waitUntil("the sacrifice asked on the table: ${alice.screen()}") { alice.shows(bar) }
        assertFalse(alice.shows(hasContentDescription("Cast panel")), "no panel: ${alice.screen()}")
        waitUntil("a Pup glows") { "target" in alice.marks("Ash Pup") }
        alice.battlefieldCard("Ash Pup").performClick()
        if (alice.shows(bar) && alice.field("Ash Pup").size == 2) {
            // TBL-130: a tap on a stack of identical Pups opens it first; a tap on a member picks it
            waitUntil("the Pups opened") { alice.marks("Ash Pup", 1).contains("target") }
            alice.battlefieldCard("Ash Pup", index = 1).performClick()
        }
        waitUntil("one Pup sacrificed and the Offering resolved", along = listOf(bob)) {
            alice.field("Ash Pup").size == 1 && alice.state.stack.isEmpty() && alice.inOwnMain() && alice.state.player(alice.me).hand.size == hand
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-047, STK-030 (owner playtest 2026-11-09), CR 702.122a: crewing Ember Cart (crew 2) with two 1/1 Pups is picked on the table:
    // the slim bar with the count, Done on the step button greyed until the Pups' power reaches 2; Done crews it
    @Test fun crewIsPickedOnTheTableWithDoneOnTheStepButton() {
        start(deck(TestCards.cart to 14, TestCards.pup to 26), mapOf("Ember Cart" to 1, "Ash Pup" to 2))
        plays("Ember Cart", "Ash Pup", "Ash Pup")
        alice.battlefieldCard("Ember Cart").performClick()
        waitUntil("the crew asked on the table: ${alice.screen()}") { alice.shows(bar) }
        assertFalse(alice.shows(hasContentDescription("Cast panel")), "no panel: ${alice.screen()}")
        waitUntil("None on the main button, greyed") { alice.shows(hasTestTag("main-button") and hasContentDescription("None") and isNotEnabled()) }
        alice.battlefieldCard("Ash Pup").performClick()
        if (alice.marks("Ash Pup").none { it == "picked" } && alice.marks("Ash Pup", 1).none { it == "picked" }) {
            // TBL-130: the first tap on the Pups' stack opened it
            waitUntil("the Pups opened") { "target" in alice.marks("Ash Pup", 1) }
            alice.battlefieldCard("Ash Pup").performClick()
        }
        waitUntil("one Pup picked, the count says so, Done greyed") {
            alice.shows(hasContentDescription("Picked: 1 picked", substring = true)) && alice.shows(hasTestTag("main-button") and hasContentDescription("Done") and isNotEnabled())
        }
        alice.battlefieldCard("Ash Pup", index = 1).performClick()
        waitUntil("both picked, Done usable") { alice.mainSays("Done") }
        alice.tap("Done")
        waitUntil("the crew ability on the Stack, both Pups tapped", along = emptyList()) {
            alice.state.stack.size == 1 && alice.field("Ash Pup").all { alice.state.obj(it)?.tapped == true }
        }
        waitUntil("the Cart crewed", along = listOf(bob)) {
            if (alice.state.stack.isNotEmpty() && alice.mainSays("Resolve")) alice.tap("Resolve")
            alice.state.stack.isEmpty() && Engine.view(alice.state)[alice.field("Ember Cart").single()]?.characteristics?.isType(CardType.Creature) == true
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

/** What the app log says of ignored quick second taps on the main button (TRN-063). */
private object PickLog {
    fun count(): Int = E2ePlayer.log().split('\n').count { "a quick second tap on" in it }
}
