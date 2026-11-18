package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TBL-117, SYS-171 (owner, 2026-11-17: "maybe mana abilities could get a different color glow that's also more faint? if mana and
 * non mana then show non mana glow?"): Alice's permanents whose only use now is a mana ability glow fainter in their own colour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ManaGlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var kit: EngineTableKit
    private val toolkit get() = kit.toolkit

    @After fun forget() = AutomatumBoards.reset()

    /** Alice's table after her opening keep, her deck Mountains but for [wanted], dealt so that she holds them (the seats of the
     * opening hand don't depend on what the cards are). */
    private fun dealt(vararg wanted: String) {
        val probe = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf("Mountain"))))
        probe.opening()
        val held = probe.state().player(probe.me).hand.map(probe::atw).toSet()
        val seats = (0 until 30).filter { TableStatesFixture.id("${probe.f.a}/card/$it") in held }
        val deck = MutableList(30) { "Mountain" }
        wanted.forEachIndexed { i, name -> deck[seats[i]] = name }
        AutomatumBoards.reset()
        kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = deck)))
        kit.opening()
    }

    /** Casts the free permanent [name] from the hand, on the battlefield before going on. */
    private fun plays(name: String): String {
        val card = kit.atw(kit.hand(name).first())
        assertTrue(toolkit.cast.start(card), "$name is cast: ${toolkit.error}")
        kit.playOn("$name on the battlefield") { card in kit.field(name).map(kit::atw) && kit.aliceMain() }
        return card
    }

    /** Alice's second Main 1: two Mountains, an Ember Elf ("{T}: Add {R}.", haste), an Ember Shard ("{T}: Add {R}.") and an Ember
     * Lantern ("{T}: Add {R}." and "{2}: You gain 1 life."), all untapped, an Ember Rebuke ({3}{R}) in her hand. */
    private fun board(): Board {
        dealt(Choices.REBUKE, Choices.ELF, Choices.SHARD, Choices.LANTERN)
        val first = kit.playLand()
        val elf = plays(Choices.ELF); val shard = plays(Choices.SHARD); val lantern = plays(Choices.LANTERN)
        assertTrue(toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice's second Main 1") { kit.aliceMain() && kit.state().turn.number >= 3 }
        val second = kit.playLand()
        return Board(setOf(first, second), elf, shard, lantern, kit.atw(kit.hand(Choices.REBUKE).first()))
    }
    private data class Board(val mountains: Set<String>, val elf: String, val shard: String, val lantern: String, val rebuke: String)

    // TBL-117, SYS-171: the Mountains, the Elf and the Shard, whose only use now is mana, are the mana glow's; the Lantern (its
    // life ability usable too) and the castable Rebuke glow cyan only; Bob's table gives none of Alice's cards either glow
    @Test fun permanentsWhoseOnlyUseIsManaGetTheManaGlow() {
        val b = board()
        val play = kit.alice.play()
        assertEquals(b.mountains + b.elf + b.shard, toolkit.manaOnlyOrNull(play), "the mana glow")
        val cyan = toolkit.playable(play)
        assertTrue(b.lantern in cyan && b.rebuke in cyan, "the cyan glow: $cyan")
        assertTrue(cyan.none { it in b.mountains || it == b.elf || it == b.shard }, "mana alone never glows cyan: $cyan")
        val bobPlay = kit.bob.play()
        val alices = bobPlay.board.cards.filter { it.owner == kit.f.a }.map { it.id }.toSet()
        assertTrue(kit.bobToolkit.manaOnlyOrNull(bobPlay).orEmpty().none { it in alices }, "Bob's table never glows Alice's cards")
    }

    // TBL-117, STK-164: while her cast is open the payment's outlines show, not the mana glow
    @Test fun noManaGlowWhileACastIsOpen() {
        val b = board()
        assertTrue(toolkit.cast.start(b.rebuke), "the Rebuke's cast begins: ${toolkit.error}")
        assertTrue(toolkit.cast.open, "the cast is open")
        assertEquals(emptySet(), toolkit.manaOnlyOrNull(kit.alice.play()))
    }

    // TBL-117, SYS-171: the representative picture for the owner's gallery review
    @Test fun theManaGlowBesideTheCyanGlow() {
        board()
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(kit.f.game, kit.alice.play(), kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/battlefield/mana-glow.png",
            "Alice's second main phase after playing her second Mountain: her two untapped Mountains, her Ember Elf and her Ember Shard, whose only use now is making mana, glow faintly in slate blue, while her Ember Lantern (it can also gain life now) and the Ember Rebuke in her hand, which she can cast, glow in the usual cyan.")
    }
}
