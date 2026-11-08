package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner-approved casting rework (2026-11-07) on Alice's table: a raised hand card's chips are its complete ways to cast it
 * (ZON-080), a chip's tap casts at once, a tap on the raised card opens the cast panel when the card has any choice (the
 * chips flash) and casts one without any; in the panel the payment shows and is chosen by tapping sources on the table
 * (STK-024). Her deck: Ember Kits (free, offspring {R}), Ember Shards (a free mana rock), Mountains and Ember Broodraiders
 * (eight complete ways).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CastChipsTest {
    @get:Rule val compose = createComposeRule()
    /** Alice's deck (in turn); a test holding a Broodraider sets it before the game begins. */
    private var deck = listOf(Choices.KIT, Choices.SHARD, "Mountain")
    private val kit by lazy { EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = deck))) }
    private val toolkit get() = kit.toolkit
    private var version by mutableIntStateOf(0)

    @After fun forget() = AutomatumBoards.reset()

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

    /** Alice's Main 1 with a Mountain and an Ember Shard on the battlefield; their ids. */
    private fun twoSources(): Pair<String, String> {
        kit.opening()
        val mountain = kit.playLand()
        val shard = kit.atw(kit.hand(Choices.SHARD).first())
        assertTrue(toolkit.cast.start(shard), "the Shard is cast: ${toolkit.error}")
        kit.playOn("the Shard on the battlefield") { shard in kit.field(Choices.SHARD).map(kit::atw) && kit.aliceMain() }
        return mountain to shard
    }

    private fun tapped(card: String) = kit.alice.play().board.cards.single { it.id == card }.tapped

    private fun raise(card: String) {
        kit.tap(kit.item(card, PlayZone.HAND))
        assertEquals(card, toolkit.hand.raised, "raised")
        assertNotNull(toolkit.hand.chips, "its chips")
    }

    // ZON-080 (owner 2026-11-07): an offspring creature's chips are its two complete ways, "Cast" and "Cast with offspring {R}";
    // a tap on the second casts it at once with Autotap: the Mountain taps and the Kit enters with its token copy
    @Test fun aChipTapCastsThatWayAtOnce() {
        kit.opening()
        val mountain = kit.playLand()
        val card = kit.atw(kit.hand(Choices.KIT).first())
        raise(card)
        val chips = toolkit.hand.chips!!
        assertEquals(listOf("Cast" to null, "Cast with offspring" to "{R}"), chips.chips.map { it.name to it.cost })
        assertFalse(chips.more)
        assertTrue(toolkit.hand.chip(1), "the chip casts")
        assertNull(toolkit.hand.raised, "lowered")
        assertFalse(toolkit.cast.open, "no panel: nothing left to ask")
        kit.playOn("the Kit and its token copy on the battlefield", rounds = 30) { kit.field(Choices.KIT).size >= 2 && kit.aliceMain() }
        assertTrue(tapped(mountain), "Autotap tapped the Mountain")
    }

    // ZON-079, ZON-080 (owner 2026-11-07): a tap on the raised Kit (two ways) opens the cast panel with its way and its extra
    // (offspring) instead of picking a chip, and the chips flash; nothing is sent
    @Test fun aTapOnTheRaisedCardOpensThePanelAndFlashesTheChips() {
        kit.opening()
        kit.playLand()
        val card = kit.atw(kit.hand(Choices.KIT).first())
        raise(card)
        val tip = kit.alice.device.session.tip
        val flashes = toolkit.hand.flash
        kit.tap(kit.item(card, PlayZone.HAND))
        assertEquals(flashes + 1, toolkit.hand.flash, "the chips flash")
        assertTrue(toolkit.cast.open, "the cast panel")
        val hand = assertNotNull(toolkit.cast.hand, "its ways and extras")
        assertEquals(listOf("Offspring"), hand.extras.map { it.name })
        assertTrue(toolkit.cast.choosing)
        assertEquals(tip, kit.alice.device.session.tip, "nothing sent")
    }

    // ZON-079 (owner 2026-11-07): a card without any choice (the Shard: one way, nothing to ask) is cast by the tap on the
    // raised card, no panel; the chips don't flash
    @Test fun aRetapCastsACardWithoutAnyChoice() {
        kit.opening()
        val shard = kit.atw(kit.hand(Choices.SHARD).first())
        raise(shard)
        assertFalse(toolkit.hand.chips!!.choice)
        val flashes = toolkit.hand.flash
        kit.tap(kit.item(shard, PlayZone.HAND))
        assertFalse(toolkit.cast.open, "no panel")
        assertEquals(flashes, toolkit.hand.flash)
        kit.playOn("the Shard on the battlefield") { shard in kit.field(Choices.SHARD).map(kit::atw) && kit.aliceMain() }
    }

    // ZON-080 (owner 2026-11-07): holding a chip outlines the sources Autotap would tap for it, without casting: none for the
    // free "Cast", one source for "Cast with offspring"
    @Test fun holdingAChipPreviewsItsSources() {
        val (mountain, shard) = twoSources()
        val card = kit.atw(kit.hand(Choices.KIT).first())
        raise(card)
        val play = kit.alice.play()
        toolkit.hand.held = 0
        assertEquals(emptySet(), toolkit.hand.sources(play), "the free cast taps nothing")
        toolkit.hand.held = 1
        val preview = toolkit.hand.sources(play)
        assertEquals(1, preview.size)
        assertTrue(preview.single() in setOf(mountain, shard))
        assertFalse(toolkit.cast.open, "nothing cast")
        assertEquals(card, toolkit.hand.raised)
    }

    // ZON-080 (owner 2026-11-07): the Broodraider's eight complete ways show as one "Cast…" chip with its cheapest cost the
    // mana pays (dash {R}); its tap opens the panel with both ways and both extras
    @Test fun moreThanFourWaysAreOneCastChip() {
        deck = listOf(Choices.BROODRAIDER, Choices.SHARD, "Mountain")
        twoSources()
        val card = kit.atw(kit.hand(Choices.BROODRAIDER).first())
        render()
        raise(card)
        val chips = toolkit.hand.chips!!
        assertTrue(chips.more)
        assertEquals(8, chips.count)
        assertEquals("{R}", chips.cheapest)
        refresh()
        compose.onNodeWithContentDescription("Way Cast… {R}").assertExists()
        captureShot("$SCREENSHOTS/chips/cast-more-chip.png",
            "Alice tapped her Ember Broodraider, which has eight ways to cast it (cast or dash, each with or without kicker and offspring): one \"Cast…\" chip with the cheapest cost, {R}, rises above it to open the cast panel.")
        assertTrue(toolkit.hand.more())
        val hand = assertNotNull(toolkit.cast.hand)
        assertEquals(listOf("Cast", "Dash"), hand.ways.map { it.name })
        assertEquals(listOf("Kicker", "Offspring"), hand.extras.map { it.name })
        assertEquals(1, hand.way, "the first way the mana pays: Dash")
    }

    // STK-024 (owner 2026-11-08, partial override): in the Kit's panel with offspring on, Autotap's source pays and is outlined; a tap
    // on it takes it out and Autotap fills in with the other; taking that out too leaves nothing (Cast greyed, not paid), a tap on it
    // picks it again; Cast pays with the source picked, the one taken out isn't used again
    @Test fun thePanelsPaymentIsChosenOnTheTable() {
        val (mountain, shard) = twoSources()
        val card = kit.atw(kit.hand(Choices.KIT).first())
        raise(card)
        kit.tap(kit.item(card, PlayZone.HAND))
        assertTrue(toolkit.cast.open)
        assertNull(toolkit.cast.payment, "the free cast pays nothing")
        assertTrue(toolkit.cast.handExtra(0), "offspring on")
        assertEquals("{R}", toolkit.cast.payment?.cost.toString())
        assertTrue(toolkit.cast.paid)
        val suggested = toolkit.cast.paySources().single()
        val other = if (suggested == mountain) shard else mountain
        kit.tap(kit.item(suggested))
        assertTrue(toolkit.cast.custom)
        assertTrue(toolkit.cast.paid, "Autotap fills in")
        assertEquals(setOf(other), toolkit.cast.paySources())
        kit.tap(kit.item(other))
        assertFalse(toolkit.cast.paid, "nothing pays now")
        assertEquals(emptySet(), toolkit.cast.paySources())
        assertTrue(toolkit.cast.open, "the panel stays")
        kit.tap(kit.item(other))
        assertTrue(toolkit.cast.paid)
        assertEquals(setOf(other), toolkit.cast.paySources())
        assertTrue(toolkit.cast.cast())
        kit.playOn("the Kit and its copy on the battlefield", rounds = 30) { kit.field(Choices.KIT).size >= 2 && kit.aliceMain() }
        assertTrue(tapped(other), "the hand-picked source paid")
        assertFalse(tapped(suggested), "Autotap's choice stayed untapped")
    }

    // STK-024 (owner 2026-11-07): with Autotap off nothing is chosen: Cast waits until a source on the table pays
    @Test fun withAutotapOffEverySourceIsPicked() {
        val (mountain, _) = twoSources()
        toolkit.cast.autotap = false
        val card = kit.atw(kit.hand(Choices.KIT).first())
        raise(card)
        kit.tap(kit.item(card, PlayZone.HAND))
        assertTrue(toolkit.cast.handExtra(0))
        assertEquals(emptyList(), toolkit.cast.mana, "nothing pre-chosen")
        assertFalse(toolkit.cast.paid)
        kit.tap(kit.item(mountain))
        assertTrue(toolkit.cast.paid)
        assertEquals(setOf(mountain), toolkit.cast.paySources())
    }

    // ZON-080 (owner 2026-11-07): the screenshots: the Kit's chips, the Broodraider's "Cast…" chip, and the Kit's panel
    // paying with the hand-picked Shard (outlined) with Cast enabled, then greyed with the reason while nothing pays
    @Test fun screenshots() {
        val (_, shard) = twoSources()
        val card = kit.atw(kit.hand(Choices.KIT).first())
        render()
        raise(card)
        refresh()
        compose.onNodeWithContentDescription("Way Cast with offspring {R}").assertExists()
        captureShot("$SCREENSHOTS/chips/cast-chips-offspring.png",
            "Alice tapped her Ember Kit in hand with a Mountain and an Ember Shard out: it rises with two chips above it, \"Cast\" and \"Cast with offspring\" with its {R}, each casting the Kit that way in one tap.")
        kit.tap(kit.item(card, PlayZone.HAND))
        assertTrue(toolkit.cast.handExtra(0))
        refresh()
        // STK-024 (owner 2026-11-07, open issue for the owner): unpanned, the portrait cast panel covers her land row
        captureShot("$SCREENSHOTS/cast-panel/covers-lands.png",
            "Alice tapped her raised Ember Kit to open its cast panel and turned on its Offspring extra, the table not panned: the panel's \"Mana · {R}\" section asks her to tap sources on the table, but the panel covers the bottom of her battlefield, her Mountain and Ember Shard hidden under it.")
        // In portrait the panel covers the bottom of her battlefield: she pans the table up to see her sources above it
        toolkit.layoutState.value = toolkit.layoutState.value.copy(camera = TableCamera(y = 150f))
        val suggested = toolkit.cast.paySources().single()
        kit.tap(kit.item(suggested))
        val filled = toolkit.cast.paySources().single()
        kit.tap(kit.item(filled))
        refresh()
        compose.onNodeWithContentDescription("Not paid: Tap sources on the table to pay {R}.").assertExists()
        compose.onNode(hasText("Cast") and hasClickAction()).assertIsNotEnabled()
        kit.tap(kit.item(shard))
        refresh()
        assertEquals(setOf(shard), toolkit.cast.paySources())
        compose.onNodeWithContentDescription("Payment: Paid with ${Choices.SHARD}").assertExists()
        compose.onNode(hasText("Cast") and hasClickAction()).assertIsEnabled()
        captureShot("$SCREENSHOTS/cast-panel/payment-chosen.png",
            "Alice tapped her raised Ember Kit to open its cast panel, turned on its Offspring extra, panned the table up above the panel, took out both sources and tapped her Ember Shard on the table: the panel says \"Paid with Ember Shard\" under \"Mana · {R}\", the Shard is outlined in amber on the table and Cast is enabled.")
    }
}
