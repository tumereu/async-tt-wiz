package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-27 ("cannot see what is being targeted when casting a response"): Alice has three identical Ash Pups
 * and Bob casts Storm Spark at the first. On her table that Pup lies apart from the other two (TBL-053), and while she casts
 * Cinder Charm in response and picks its target, Bob's arrow to it stays drawn (STK-126), on a game played through the
 * simulated server.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TargetsApartTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private fun render(kit: EngineTableKit) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            val play = kit.alice.play()
                            TableSurface(kit.f.game, play, kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() },
                                    controlsConnected = true, waitingLine = kit.routes.waitingLine(play)))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice casts three Ash Pups in her first turn and ends it; Bob casts Storm Spark at the first; she holds priority over it.
     * The Pups by arrival. */
    private fun toBobsSparkAtAPup(kit: EngineTableKit): List<String> {
        kit.opening()
        val pups = kit.hand(Choices.PUP).take(3).map(kit::atw)
        assertEquals(3, pups.size, "three Pups in Alice's opening hand")
        for (pup in pups) {
            assertTrue(kit.toolkit.cast.start(pup), "the Pup is cast: ${kit.toolkit.error}")
            kit.playOn("the Pup resolving") { pup in kit.field(Choices.PUP).map(kit::atw) && kit.aliceMain() }
        }
        // Before: identical, they lie in one stack
        assertEquals(listOf(pups.toSet()), BattlefieldLayout.groups(kit.alice.play(), kit.f.a).filter { it.members.any { m -> m in pups } }
            .map { it.members.toSet() })
        kit.bobCastsSpark { s -> TargetRef.Object(s.battlefield.first { kit.atw(it) == pups.first() }) }
        kit.scenario.confirmStops[kit.f.a] = false
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice holding priority over Bob's Spark", rounds = 40) {
            val s = kit.state()
            s.stack.isNotEmpty() && s.priority.holder == kit.me && kit.routes.arrowShown() && s.stackItems[s.stack.last()]?.kind == StackItemKind.Spell
        }
        return pups
    }

    /** TBL-053: the Pup Bob's Spark targets lies on its own right after the stack of the other two, in its row. */
    private fun assertTargetLiesApart(kit: EngineTableKit, pups: List<String>) {
        val groups = BattlefieldLayout.groups(kit.alice.play(), kit.f.a)
        val rest = assertNotNull(groups.singleOrNull { it.members.toSet() == pups.drop(1).toSet() }, "the other two in one stack: $groups")
        val apart = assertNotNull(groups.singleOrNull { it.members == listOf(pups.first()) }, "the targeted Pup alone: $groups")
        assertEquals(rest.key, apart.besideOf, "right after their stack")
        val target = kit.item(pups.first()).bounds
        val stack = kit.item(pups[1]).bounds
        assertEquals(stack.y, target.y, "in its row")
        assertTrue(target.x > stack.x, "right of the stack: $target, $stack")
    }

    /** STK-126: Bob's arrow to the Pup is drawn. */
    private fun assertBobsArrowDrawn() {
        compose.onNodeWithTag("stack-target-arrows", useUnmergedTree = true)
            .assert(hasContentDescription("Targets of the top Stack entry: ${Choices.PUP}"))
    }

    /** Alice starts casting Cinder Charm and chooses its damage mode: its targets are picked on the table. */
    private fun castCharm(kit: EngineTableKit, shot: String?) {
        val charm = kit.atw(kit.hand(Choices.CHARM).first())
        assertTrue(kit.toolkit.cast.start(charm), "the Charm's cast begins: ${kit.toolkit.error}")
        compose.waitForIdle()
        assertBobsArrowDrawn()
        shot?.let { captureShot(it, "Alice began casting Cinder Charm in response to Bob's Storm Spark: while the cast panel asks for its mode, Bob's red arrow to her targeted Ash Pup stays drawn, the Pup lying apart from the other two (choice: the arrow simply drawn, not growing again).") }
        compose.onNodeWithText("Cinder Charm deals 1 damage to any target.").performClick()
        compose.waitForIdle()
        assertTrue(kit.toolkit.cast.targeting, "targets are picked on the table")
    }

    // TBL-053, TBL-178, TBL-129, STK-058, STK-125, STK-126, STK-047 (owner playtest 2026-11-27): Bob's Spark targets the first of
    // Alice's three Pups: on her table it lies apart; she responds with Cinder Charm and, while she chooses its mode and picks its
    // target, Bob's arrow to the Pup stays drawn in his red, her own violet glow on top of it on the Pup
    @Test fun theTargetLiesApartAndItsArrowStaysWhileAliceResponds() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.PUP, Choices.PUP, Choices.CHARM),
            bobCards = listOf("Storm Spark", "Mountain"))))
        val pups = toBobsSparkAtAPup(kit)
        assertTargetLiesApart(kit, pups)
        render(kit)
        assertBobsArrowDrawn()
        captureShot("$SCREENSHOTS/stack/targets-apart.png",
            "Bob cast Storm Spark at the first of Alice's three identical Ash Pups and she holds priority: that Pup lies on its own right after the stack of the other two (×2), Bob's red arrow running to it from the Stack (choice: the other player's entries' targets lie apart too).")
        castCharm(kit, "$SCREENSHOTS/stack/targets-apart-cast-panel.png")
        assertBobsArrowDrawn()
        captureShot("$SCREENSHOTS/stack/targets-apart-picking.png",
            "Alice chose Cinder Charm's damage mode and picks its target on the table: her legal targets glow violet while Bob's red arrow to the targeted Pup and its red highlight stay drawn, the Pup's violet glow on top of the red.")
    }

    // TBL-053, STK-126 (owner playtest 2026-11-27): the same in landscape
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun theTargetLiesApartAndItsArrowStaysInLandscape() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.PUP, Choices.PUP, Choices.CHARM),
            bobCards = listOf("Storm Spark", "Mountain"))))
        assertTargetLiesApart(kit, toBobsSparkAtAPup(kit))
        render(kit)
        assertBobsArrowDrawn()
        captureShot("$SCREENSHOTS/stack/targets-apart-landscape.png",
            "Landscape: Bob cast Storm Spark at the first of Alice's three identical Ash Pups: that Pup lies on its own right after the stack of the other two, Bob's red arrow running to it.")
        castCharm(kit, null)
        // The target bar stands over the battlefields' low end: Alice pans the table up to see her Pups above it
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(640f, 222f), androidx.compose.ui.geometry.Offset(640f, 120f), 400) }
        compose.waitForIdle()
        assertBobsArrowDrawn()
        captureShot("$SCREENSHOTS/stack/targets-apart-picking-landscape.png",
            "Landscape: Alice picks Cinder Charm's target in response and panned the table up above the target bar: her legal targets glow violet while Bob's red arrow to the targeted Pup stays drawn, the Pup's violet glow on top.")
    }
}
