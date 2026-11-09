package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.PlayZone
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Castability on Alice's table (castability spec §6's gallery): the raised card's smallest cost + {?} with Autotap's sources for it, a target
 * bar narrowed to what can be paid, a selection narrowed as it is picked, a Signet chain in a payment, a partial override.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CastabilityScreensTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var kit: EngineTableKit
    private val toolkit get() = kit.toolkit
    private var version by mutableIntStateOf(0)

    @After fun forget() = AutomatumBoards.reset()

    /** Alice's table after her opening keep, her deck [cards] (the rest of it their first), dealt so that she holds [wanted]: the seats of the
     * opening hand don't depend on what the cards are. */
    private fun dealt(cards: List<String>, vararg wanted: String) {
        val probe = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(cards.first()))))
        probe.opening()
        val held = probe.state().player(probe.me).hand.map(probe::atw).toSet()
        val seats = (0 until 30).filter { TableStatesFixture.id("${probe.f.a}/card/$it") in held }
        val deck = MutableList(30) { cards.first() }
        wanted.forEachIndexed { i, name -> deck[seats[i]] = name }
        AutomatumBoards.reset()
        kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = deck)))
        kit.opening()
        wanted.groupBy { it }.forEach { (name, copies) -> assertTrue(kit.hand(name).size >= copies.size, "$name in hand") }
    }

    private fun render() {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            version
                            TableSurface(kit.f.game, kit.alice.play(), kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun refresh() { version++; compose.waitForIdle() }

    /** Casts each of [names] from the hand (free permanents), on the battlefield before the next. */
    private fun plays(vararg names: String): List<String> = names.map { name ->
        val before = kit.field(name).size
        val card = kit.atw(kit.hand(name).first())
        assertTrue(toolkit.cast.start(card), "$name is cast: ${toolkit.error}")
        kit.playOn("$name on the battlefield") { kit.field(name).size > before && kit.aliceMain() }
        card
    }

    private fun raised(name: String): String {
        val card = kit.atw(kit.hand(name).first())
        kit.tap(kit.item(card, PlayZone.HAND))
        assertEquals(card, toolkit.hand.raised, "raised")
        return card
    }

    private val rebukes = listOf(Choices.REBUKE, Choices.GEODE, Choices.PUP, Choices.DROWSY, Choices.GEODE, Choices.SHARD, Choices.SHARD)

    // ZON-080: "{?}" is drawn as a symbol, our own notation
    @Test fun theQuestionMarkIsASymbol() {
        assertEquals(listOf(CardTextPart.Symbol("R"), CardTextPart.Plain(" + "), CardTextPart.Symbol("?")), parseCardText("{R} + {?}"))
    }

    // ZON-080, ZON-079, CR 601.2f: the smallest cost + {?}, outlined for the minimum
    @Test fun theRaisedCardShowsItsSmallestCost() {
        dealt(rebukes, Choices.REBUKE, Choices.GEODE, Choices.GEODE, Choices.PUP, Choices.DROWSY)
        plays(Choices.GEODE, Choices.GEODE, Choices.PUP, Choices.DROWSY)
        render()
        raised(Choices.REBUKE)
        refresh()
        compose.onNodeWithContentDescription("Way Cast {1}{R} + {?}").assertExists()
        assertEquals(1, toolkit.hand.chips!!.sources.size, "Autotap's sources for the minimum only")
        captureShot("$SCREENSHOTS/castability/smallest-cost.png",
            "Alice raised her Ember Rebuke ({3}{R}, {2} less on a tapped creature) with two Ember Geodes ({R}{R} each), a tapped and an untapped creature out: its chip shows the smallest cost, {1}{R} + {?}, and one Geode is outlined for that minimum.")
    }

    // TBL-118, STK-047, CR 601.2f: exact mana for the cheap target: the other legal target doesn't glow
    @Test fun aLegalTargetYouCanNotPayForDoesNotGlow() {
        dealt(rebukes, Choices.REBUKE, Choices.GEODE, Choices.PUP, Choices.DROWSY)
        plays(Choices.GEODE, Choices.PUP, Choices.DROWSY)
        render()
        raised(Choices.REBUKE)
        assertTrue(toolkit.hand.chip(0), "the chip casts")
        refresh()
        val options = toolkit.cast.pick!!.options
        assertEquals(1, options.size, "only the tapped creature")
        captureShot("$SCREENSHOTS/castability/target-unaffordable.png",
            "Alice cast her Ember Rebuke with one Ember Geode out: the target bar is open, her tapped Drowsy Pup glows as a target she can pay for, and her untapped Ash Pup doesn't glow, its {3}{R} being more than she has.")
    }

    // STK-047, CR 601.2f: enough for both: both glow
    @Test fun bothTargetsGlowWithEnoughForBoth() {
        dealt(rebukes, Choices.REBUKE, Choices.GEODE, Choices.GEODE, Choices.PUP, Choices.DROWSY)
        plays(Choices.GEODE, Choices.GEODE, Choices.PUP, Choices.DROWSY)
        render()
        raised(Choices.REBUKE)
        assertTrue(toolkit.hand.chip(0), "the chip casts")
        refresh()
        assertEquals(2, toolkit.cast.pick!!.options.size)
        captureShot("$SCREENSHOTS/castability/target-both.png",
            "Alice cast her Ember Rebuke with two Ember Geodes out: the target bar is open and both creatures glow, the tapped Drowsy Pup at {1}{R} and the untapped Ash Pup at {3}{R}.")
    }

    // STK-047, CR 601.2c, 601.2f: the owner's example: a selection narrows what can still be picked
    @Test fun aSelectionNarrowsWhatCanBePicked() {
        dealt(listOf(Choices.GEODE, Choices.TOLL, Choices.ELF_BEAR, Choices.OX_HUMAN, Choices.GOAT),
            Choices.TOLL, Choices.GEODE, Choices.GEODE, Choices.GEODE, Choices.ELF_BEAR, Choices.OX_HUMAN, Choices.GOAT)
        plays(Choices.GEODE, Choices.GEODE, Choices.GEODE, Choices.ELF_BEAR, Choices.OX_HUMAN, Choices.GOAT)
        render()
        raised(Choices.TOLL)
        assertTrue(toolkit.hand.chip(0), "the chip casts")
        refresh()
        assertEquals(3, toolkit.cast.pick!!.options.size, "each alone is within the six mana")
        kit.tap(kit.item(kit.atw(kit.field(Choices.ELF_BEAR).single())))
        refresh()
        assertFalse(toolkit.cast.pick!!.options.any { toolkit.cast.flow!!.ref(it).toString().contains(kit.atw(kit.field(Choices.OX_HUMAN).single())) }, "two more types are too many")
        assertTrue(toolkit.cast.pickDone, "two types pay")
        assertEquals(2, toolkit.cast.pick!!.options.size, "the pick and the one-type creature")
        captureShot("$SCREENSHOTS/castability/multi-target.png",
            "Alice cast her Ember Toll ({R}{R} for each creature type among its targets) with three Ember Geodes out and picked her Elf Bear: it shows picked and Done is enabled; her Ox Human, with two more types, no longer glows, and her one-type Lone Goat does.")
    }

    // STK-059, CR 605.3a: a Signet in the payment, fed by a Shard
    @Test fun aSignetChainInThePayment() {
        dealt(listOf(Choices.BLUE_NOTE, Choices.SIGNET, Choices.SHARD, Choices.KIT), Choices.BLUE_NOTE, Choices.SIGNET, Choices.SHARD)
        toolkit.cast.autotap = false
        val (signet, shard) = plays(Choices.SIGNET, Choices.SHARD)
        render()
        val card = kit.atw(kit.hand(Choices.BLUE_NOTE).first())
        assertTrue(toolkit.cast.start(card), "the panel opens with Autotap off")
        kit.tap(kit.item(signet))
        kit.tap(kit.item(shard))
        refresh()
        assertTrue(toolkit.cast.paid, "the Shard pays the Signet's {1}, its {U} the Note")
        toolkit.layoutState.value = toolkit.layoutState.value.copy(camera = TableCamera(y = 150f))
        refresh()
        captureShot("$SCREENSHOTS/castability/signet-chain.png",
            "Alice cast her Blue Note ({U}) with Autotap off, the table panned up, and tapped her Ember Signet and an Ember Shard: both are outlined in amber on the table and the panel names neither, the Shard paying the Signet's {1} and the Signet's {U} the Note.")
    }

    // STK-024 (owner 2026-11-08): a pick stays and Autotap fills in the rest
    @Test fun aPartialOverride() {
        dealt(listOf(Choices.BROOD, Choices.SHARD, Choices.ELF, Choices.STONE), Choices.BROOD, Choices.SHARD, Choices.ELF, Choices.STONE)
        val (shard, elf, stone) = plays(Choices.SHARD, Choices.ELF, Choices.STONE)
        render()
        val card = kit.atw(kit.hand(Choices.BROOD).first())
        kit.tap(kit.item(card, PlayZone.HAND))
        kit.tap(kit.item(card, PlayZone.HAND))
        assertTrue(toolkit.cast.handExtra(0), "offspring on")
        assertEquals(setOf(shard, stone), toolkit.cast.paySources(), "Autotap takes the rocks, not the creature")
        kit.tap(kit.item(elf))
        assertTrue(elf in toolkit.cast.paySources(), "the pick stays")
        assertEquals(2, toolkit.cast.paySources().size, "Autotap fills in the rest and no more")
        assertTrue(toolkit.cast.paid)
        toolkit.layoutState.value = toolkit.layoutState.value.copy(camera = TableCamera(y = 150f))
        refresh()
        captureShot("$SCREENSHOTS/castability/partial-override.png",
            "Alice opened her Ember Brood's cast panel, turned on its Offspring {2} and tapped her Ember Elf on the table, panned up: the Elf stays picked and Autotap fills in one rock for the rest, both outlined in amber, and Cast is enabled.")
    }
}
