package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import arcanum.api.Mechanic
import arcanum.engine.Engine
import arcanum.engine.input.Input
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.protocol.game.EnginePreferences
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CMB-098–CMB-101 (owner, 2026-11-25): the costs panel the main button's declaration opens when it leaves something to decide:
 * an optional cost to attack (exert), mana to pay by hand with Autotap off, both, a block's cost; with Autotap on and only costs
 * the creatures must pay, no panel; Back to staging.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CombatCostPanelTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private val charger = EngineGameFixture.Choices.CHARGER
    private val bulwark = EngineGameFixture.Choices.BULWARK
    private val chime = EngineGameFixture.Choices.CHIME

    /** Who is drawn and how often: a new value draws [kit]'s table afresh from the viewer's live board. */
    private var phone by mutableStateOf("" to 0)
    private var drawing: EngineTableKit? = null

    /** [viewer]'s table of [kit] as the engine table draws it, drawn afresh from their live board. */
    private fun render(kit: EngineTableKit, viewer: String = kit.f.a) {
        if (drawing == null) {
            drawing = kit
            compose.setContent {
                val (who, n) = phone
                val game = if (who == kit.f.a) kit.alice else kit.bob
                val routes = if (who == kit.f.a) kit.routes else kit.bobRoutes
                val toolkit = if (who == kit.f.a) kit.toolkit else kit.bobToolkit
                if (who.isNotEmpty()) androidx.compose.runtime.key(who, n) {
                    MaterialTheme {
                        NoRipples {
                            CompositionLocalProvider(LocalEngineRoutes provides routes) {
                                Box(Modifier.fillMaxSize()) {
                                    TableSurface(kit.f.game, game.play(), who, false, Modifier.fillMaxSize(), connected = true,
                                        onIntent = toolkit::handle, toolkit = toolkit,
                                        runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() }, controlsConnected = true))
                                }
                            }
                        }
                    }
                }
            }
        }
        phone = viewer to phone.second + 1
        compose.waitForIdle()
    }

    private fun showCombat(kit: EngineTableKit, toolkit: TableToolkitController = kit.toolkit, game: EngineGame = kit.alice) =
        toolkit.combat.show(game.play(), AutomatumBoards.combat(game.play()))

    /** Alice stages [card] (a tap, CMB-028) in her declare attackers step, the combat worked out after. */
    private fun stage(kit: EngineTableKit, card: String) {
        showCombat(kit)
        kit.tap(kit.item(card))
        kit.settle()
        showCombat(kit)
        assertEquals(listOf(card), CombatFlow.lanes(kit.alice.play().board.combat).first.map { it.cardId }, "staged")
    }

    /** The main button's declaration (TRN-057). */
    private fun pressMain(kit: EngineTableKit, toolkit: TableToolkitController = kit.toolkit) =
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(TableActionScope(kit.f.game.id, kit.alice.play().playId))), "the main button")

    /** The costs panel's Attack (Block) on the main button (CMB-100). */
    private fun pressPanelMain(toolkit: TableToolkitController, label: String) {
        val action = assertNotNull(toolkit.mainSlot.action, "the panel's go-on on the main button")
        assertEquals(label, action.label)
        assertNull(action.refusal, "not greyed")
        action.run()
    }

    /** Alice in her first turn with an Ember Charger cast (haste), declaring attackers; with Bob's [bobCards] cast in his turn
     * before when given, and [lands] Mountains of hers. Returns the Charger. */
    private fun chargerDeclaring(kit: EngineTableKit, bobCards: List<String> = emptyList(), lands: Int = 0): String {
        if (bobCards.isNotEmpty()) {
            kit.bobMain = { s ->
                val held = bobCards.filter { kit.scenario.cards(s, kit.other, s.battlefield, it).isEmpty() }
                    .firstNotNullOfOrNull { kit.scenario.cards(s, kit.other, s.player(kit.other).hand, it).firstOrNull() }
                if (held == null) false else { kit.bob.send(kit.scenario.cast(s, kit.other, Input.Cast(kit.other, held))); true }
            }
            kit.opening()
            kit.playLand()
            assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
            kit.playOn("Alice's second turn") { kit.aliceMain() && kit.state().turn.number >= 3 }
            kit.bobMain = null
            repeat(lands - 1) { kit.playLand() }
        } else {
            kit.opening()
            repeat(lands) { kit.playLand() }
        }
        val card = kit.atw(kit.hand(charger).first())
        assertTrue(kit.toolkit.cast.start(card), "the Charger is cast: ${kit.toolkit.error}")
        kit.playOn("the Charger on the battlefield") { card in kit.field(charger).map(kit::atw) && kit.aliceMain() }
        kit.toAttacks()
        return card
    }

    /** CMB-044: Bob has an Ember Bulwark and an Ember Chime, Alice two Mountains and a ready Cub, staged against him ({2}). */
    private fun cubWithACost(kit: EngineTableKit): String {
        kit.bobMain = { s ->
            val held = listOf(bulwark, chime).filter { kit.scenario.cards(s, kit.other, s.battlefield, it).isEmpty() }
                .firstNotNullOfOrNull { kit.scenario.cards(s, kit.other, s.player(kit.other).hand, it).firstOrNull() }
            if (held == null) false else { kit.bob.send(kit.scenario.cast(s, kit.other, Input.Cast(kit.other, held))); true }
        }
        val cub = kit.toReadyCub()
        kit.bobMain = null
        kit.playLand()
        kit.toAttacks()
        stage(kit, cub)
        return cub
    }

    private fun cubKit() = EngineTableKit(EngineCombatScenario(EngineGameFixture(creatures = true, bobCards = listOf(bulwark, chime))))

    // CMB-098, CMB-037 (CR 508.1g, 701.43d): Alice stages her Ember Charger ("You may exert this creature as it attacks") and the
    // main button opens the costs panel, Autotap on: the Charger's card under "As they attack, you may pay", not paid, nothing
    // declared; a tap on it pays its exert (the card ringed, the box checked, an amber "Exert" chip on its copy); Attack declares
    // with it exerted
    @Test fun anOptionalCostIsChosenInThePanel() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(charger, "Mountain"))))
        val card = chargerDeclaring(kit)
        stage(kit, card)
        pressMain(kit)
        assertTrue(kit.toolkit.combat.costPanelOpen(kit.alice.play()), "the costs panel")
        assertNull(kit.state().combat, "nothing declared yet")
        render(kit)
        val optional = compose.onNodeWithTag(COSTS_OPTIONAL_TAG, useUnmergedTree = true)
        optional.assert(hasStateDescription("not paid"))
        captureShot("$SCREENSHOTS/combat-costs/optional.png",
            "Alice staged her Ember Charger and pressed Attack (1): the costs panel above her hand shows the Charger's card under \"As they attack, you may pay\" with an empty Exert box and the ability's whole text; nothing is declared yet and the main button reads Attack.")
        optional.performClick()
        kit.settle()
        showCombat(kit)
        render(kit)
        optional.assert(hasStateDescription("paid"))
        compose.onNode(hasContentDescription("Pays Exert"), useUnmergedTree = true).assertExists()
        captureShot("$SCREENSHOTS/combat-costs/optional-paid.png",
            "Alice tapped the Charger's card in the costs panel: it is ringed in violet with its Exert box checked, and the Charger's copy on the band carries an amber Exert chip (a choice: the chosen cost shows on the band as a cost to pay does).")
        pressPanelMain(kit.toolkit, "Attack")
        kit.settle()
        val obj = kit.field(charger).single()
        assertEquals(listOf(obj), kit.state().combat?.attackers?.map { it.id }, "the Charger attacks")
        assertTrue(Engine.view(kit.state())[obj]?.mechanics.orEmpty().any { it.mechanic == Mechanic.DoesntUntap }, "exerted (CR 701.43a)")
        assertFalse(kit.toolkit.combat.costPanelOpen(kit.alice.play()), "the panel closed with the declaration")
    }

    // CMB-098, CMB-099, CMB-100, CMB-101 (CR 508.1h, 508.1i, 508.1j): Bob's Ember Bulwark and Chime make Alice's Cub's attack cost
    // {2}; with Autotap off the main button opens the panel with nothing chosen: "Still owed {2}" in red and Attack greyed, the
    // staging's quick actions greyed and a tap on the staged copy only shaking it; Alice taps her two Mountains on the table and
    // Attack declares, paid with exactly those
    @Test fun aRequiredCostIsPaidByHandWithAutotapOff() {
        val kit = cubKit()
        val cub = cubWithACost(kit)
        kit.toolkit.cast.autotap = false
        pressMain(kit)
        assertTrue(kit.toolkit.combat.costPanelOpen(kit.alice.play()), "the costs panel")
        render(kit)
        val owed = "Still owed {2}: produce the mana on the board."
        compose.onNode(hasContentDescription("Not paid: $owed"), useUnmergedTree = true).assertExists()
        assertEquals(owed, kit.toolkit.mainSlot.action?.refusal, "Attack greyed with why")
        captureShot("$SCREENSHOTS/combat-costs/required-autotap-off.png",
            "Autotap off: Alice staged her Ember Cub, which Bob's Ember Bulwark makes pay {2}, and pressed Attack (1): the costs panel shows the Cub's card with its amber {2} under \"To attack, they must pay\", the Bulwark's whole text under its name, \"Total to pay: {2}\" and in red \"Still owed {2}\"; Attack is greyed, and so are Rest and Clear.")
        // CMB-101: the staging doesn't change under the panel
        kit.tap(kit.item(cub))
        kit.settle()
        assertEquals(listOf(cub), CombatFlow.lanes(kit.alice.play().board.combat).first.map { it.cardId }, "still staged")
        val mountains = kit.field("Mountain").map(kit::atw)
        assertEquals(2, mountains.size)
        // A tap on each Mountain on the table, as the table hands it to the payment (STK-024; which member of a stack a tap means is
        // TBL-130's)
        for (m in mountains) { assertTrue(kit.toolkit.tapSource(m), "$m taken by the payment"); compose.waitForIdle() }
        render(kit)
        assertTrue(kit.toolkit.flows.paid, "the two Mountains pay: ${AppLog.text().takeLast(6000)}")
        assertEquals(mountains.toSet(), kit.toolkit.flows.paySources(), "outlined on the table")
        captureShot("$SCREENSHOTS/combat-costs/required-paid.png",
            "Alice tapped her two Mountains on the table: they are outlined in amber, the panel's mana section says \"Outlined sources pay\" and Attack is the main button.")
        val life = kit.state().player(kit.other).life
        pressPanelMain(kit.toolkit, "Attack")
        kit.settle()
        assertEquals(life - 2, kit.state().player(kit.other).life, "the Cub attacked, its damage dealt")
        assertTrue(kit.field("Mountain").all { kit.state().obj(it)?.tapped == true }, "both Mountains paid")
    }

    // CMB-098, CMB-099 (CR 508.1g, 508.1h): Alice's Charger staged against Bob's Bulwark and Chime: one panel holds its optional
    // exert and the {2} it must pay, Autotap's Mountains outlined; Attack declares it exerted, the Mountains paying
    @Test fun anOptionalAndARequiredCostTogether() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(charger, "Mountain"), bobCards = listOf(bulwark, chime))))
        val card = chargerDeclaring(kit, listOf(bulwark, chime), lands = 2)
        stage(kit, card)
        pressMain(kit)
        render(kit)
        compose.onNodeWithTag(COSTS_OPTIONAL_TAG, useUnmergedTree = true).performClick()
        kit.settle()
        showCombat(kit)
        render(kit)
        assertTrue(kit.toolkit.flows.paid, "Autotap's payment")
        compose.onNode(hasContentDescription("Total to pay: {2}, Exert"), useUnmergedTree = true).assertExists()
        captureShot("$SCREENSHOTS/combat-costs/both.png",
            "Alice's Ember Charger staged against Bob's Ember Bulwark and Chime: the panel holds its exert, paid, and the {2} it must pay, \"Total to pay: {2}, Exert\"; Autotap's two Mountains are outlined on the table.")
        pressPanelMain(kit.toolkit, "Attack")
        kit.settle()
        val obj = kit.field(charger).single()
        assertEquals(listOf(obj), kit.state().combat?.attackers?.map { it.id })
        assertTrue(Engine.view(kit.state())[obj]?.mechanics.orEmpty().any { it.mechanic == Mechanic.DoesntUntap }, "exerted")
        assertTrue(kit.field("Mountain").all { kit.state().obj(it)?.tapped == true }, "the Mountains paid")
    }

    // CMB-075 (CR 508.1h): with Autotap on and only the {2} the Cub must pay there is nothing to decide: no panel, the main button
    // declares at once, Autotap's Mountains paying
    @Test fun withAutotapOnARequiredCostOpensNoPanel() {
        val kit = cubKit()
        val cub = cubWithACost(kit)
        val life = kit.state().player(kit.other).life
        pressMain(kit)
        assertFalse(kit.toolkit.combat.costPanelOpen(kit.alice.play()), "no panel")
        kit.settle()
        assertEquals(life - 2, kit.state().player(kit.other).life, "the Cub was declared at once and dealt its damage")
        assertTrue(kit.state().obj(kit.field("Ember Cub").single { kit.atw(it) == cub })?.tapped == true, "it attacked")
        assertTrue(kit.field("Mountain").all { kit.state().obj(it)?.tapped == true }, "Autotap paid {2}")
        render(kit)
        captureShot("$SCREENSHOTS/combat-costs/autotap-no-panel.png",
            "Autotap on: Alice pressed Attack (1) with her Ember Cub staged against Bob's Ember Bulwark ({2}): no costs panel opened, the attack went ahead at once with both Mountains tapped by Autotap for its cost; the Cub is tapped from attacking and Bob is at 18.")
    }

    // CMB-100: Back closes the panel with nothing declared: the staging as it was, the main button Attack (1) again
    @Test fun backReturnsToTheStaging() {
        val kit = cubKit()
        val cub = cubWithACost(kit)
        kit.toolkit.cast.autotap = false
        pressMain(kit)
        render(kit)
        compose.onNodeWithTag(COSTS_BACK_TAG, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(kit.toolkit.combat.costPanelOpen(kit.alice.play()), "closed")
        assertNull(kit.state().combat, "nothing declared")
        assertEquals(listOf(cub), CombatFlow.lanes(kit.alice.play().board.combat).first.map { it.cardId }, "the staging as it was")
        captureShot("$SCREENSHOTS/combat-costs/back.png",
            "Alice pressed Back in the costs panel: it is gone, nothing declared; her Ember Cub is still staged with its amber {2} chip and the main button reads Attack (1) again.")
        // Undo closes it the same way, taking nothing back
        pressMain(kit)
        assertTrue(kit.toolkit.combat.costPanelOpen(kit.alice.play()))
        assertTrue(kit.routes.undo())
        assertFalse(kit.toolkit.combat.costPanelOpen(kit.alice.play()), "Undo closed it")
        assertEquals(listOf(cub), CombatFlow.lanes(kit.alice.play().board.combat).first.map { it.cardId }, "and took nothing back")
    }

    // CMB-098, CMB-099 (CR 509.1d): Alice's Ember Tollward attacks ("creatures can't block unless their controller pays {1}");
    // Bob, Autotap off, places his Ash Pup to block it and the main button opens his panel, "Costs to block"; his Mountain tapped
    // on his table pays it and Block declares
    @Test fun aBlockCostIsPaidInThePanel() {
        val tollward = EngineGameFixture.Choices.TOLLWARD
        val pup = EngineGameFixture.Choices.PUP
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(tollward, "Mountain"), bobCards = listOf(pup, "Mountain"))))
        kit.bobMain = { s ->
            val land = kit.scenario.cards(s, kit.other, s.player(kit.other).hand, "Mountain").firstOrNull()
            val held = kit.scenario.cards(s, kit.other, s.player(kit.other).hand, pup).firstOrNull()
            when {
                (s.turn.landsPlayed[kit.other] ?: 0) == 0 && land != null -> { kit.bob.send(listOf(Input.PlayLand(kit.other, land))); true }
                held != null && kit.scenario.cards(s, kit.other, s.battlefield, pup).isEmpty() ->
                    { kit.bob.send(kit.scenario.cast(s, kit.other, Input.Cast(kit.other, held))); true }
                else -> false
            }
        }
        kit.opening()
        val attacker = kit.atw(kit.hand(tollward).first())
        assertTrue(kit.toolkit.cast.start(attacker), "the Tollward is cast: ${kit.toolkit.error}")
        kit.playOn("the Tollward on the battlefield") { attacker in kit.field(tollward).map(kit::atw) && kit.aliceMain() }
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice's second turn") { kit.aliceMain() && kit.state().turn.number >= 3 }
        kit.bobMain = null
        kit.toAttacks()
        stage(kit, attacker)
        pressMain(kit)
        assertFalse(kit.toolkit.combat.costPanelOpen(kit.alice.play()), "nothing for Alice to decide")
        val bobState = { kit.bob.device.model.engine }
        kit.playOn("Bob declaring blockers", bobActs = false) {
            Engine.options(bobState(), kit.other, EnginePreferences.of(bobState())).any { it is Input.DeclareBlockers }
        }
        val blocker = kit.atw(kit.scenario.cards(bobState(), kit.other, bobState().battlefield, pup).single())
        showCombat(kit, kit.bobToolkit, kit.bob)
        kit.tap(kit.item(blocker, viewer = kit.f.b), kit.bobToolkit, kit.f.b)
        kit.tap(kit.item(attacker, viewer = kit.f.b), kit.bobToolkit, kit.f.b)
        kit.settle(bobActs = false)
        showCombat(kit, kit.bobToolkit, kit.bob)
        assertEquals(listOf(blocker), CombatFlow.lanes(kit.bob.play().board.combat).second.map { it.blockerId }, "the Pup blocks")
        kit.bobToolkit.cast.autotap = false
        assertTrue(kit.bobToolkit.automatumStep(TableActionIntent.Next(TableActionScope(kit.f.game.id, kit.bob.play().playId))), "Block (1)")
        assertTrue(kit.bobToolkit.combat.costPanelOpen(kit.bob.play()), "Bob's costs panel")
        render(kit, kit.f.b)
        val mountain = kit.atw(kit.scenario.cards(bobState(), kit.other, bobState().battlefield, "Mountain").single())
        kit.tap(kit.item(mountain, viewer = kit.f.b), kit.bobToolkit, kit.f.b)
        render(kit, kit.f.b)
        assertTrue(kit.bobToolkit.flows.paid, "his Mountain pays {1}")
        captureShot("$SCREENSHOTS/combat-costs/block.png",
            "Bob placed his Ash Pup to block Alice's attacking Ember Tollward, whose ability makes each blocker cost {1}, and pressed Block (1) with Autotap off: the panel \"Costs to block\" shows the Pup with its amber {1}, the Tollward's whole text, and his Mountain, tapped on the table, outlined as paying it.")
        val life = bobState().player(kit.other).life
        pressPanelMain(kit.bobToolkit, "Block")
        kit.settle(bobActs = false)
        // The 1/1 Pup blocked the 2/3 Tollward and died; Bob took no damage
        assertTrue(kit.scenario.cards(bobState(), kit.other, bobState().player(kit.other).graveyard, pup).isNotEmpty(), "the Pup blocked and died")
        assertEquals(life, bobState().player(kit.other).life, "blocked: no damage to Bob")
        assertTrue(bobState().obj(kit.scenario.cards(bobState(), kit.other, bobState().battlefield, "Mountain").single())?.tapped == true, "his Mountain paid")
    }
}
