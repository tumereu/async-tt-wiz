package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.protocol.CombatRole
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
 * Staging attackers from a stack (owner playtest 2026-11-07): while attackers are declared Alice's stack of three identical
 * Ember Cubs doesn't regroup: each tap on it stages its topmost unstaged Cub, which moves to the stack's right end with the
 * staged look, the others staying where they were.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class StagedPileTest {
    @get:Rule val compose = createComposeRule()
    private val k = EngineTableKit()

    @After fun forget() = AutomatumBoards.reset()

    /** Bumped to draw the table again on the board it has now (the content is set once). */
    private var version by mutableIntStateOf(0)
    private var rendered = false

    private fun render() {
        if (rendered) { version++; compose.waitForIdle(); return }
        rendered = true
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides k.routes) {
                        Box(Modifier.fillMaxSize()) {
                            version
                            val play = k.alice.play()
                            TableSurface(k.f.game, play, k.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = k.toolkit::handle, toolkit = k.toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { k.routes.undo() }, controlsConnected = true,
                                    waitingLine = k.routes.waitingLine(play)))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice declaring attackers with three ready Ember Cubs (a Cub in her first turn, two in her second). */
    private fun aliceDeclaringWithThreeCubs() {
        k.toReadyCub()
        k.playLand()
        k.castCub()
        k.castCub()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.End(k.scope)), "End turn")
        k.playOn("Alice's Main 1 with three ready Cubs") { k.aliceMain() && k.scenario.creatures(k.state(), k.me, ready = true).size == 3 }
        k.toAttacks()
        k.toolkit.combat.show(k.alice.play(), AutomatumBoards.combat(k.alice.play()))
    }

    /** A finger's tap on [item] of Alice's table, dispatched as her table's surface dispatches it: a battlefield card's tap
     * is its group's ([TableToolkitController.tap]). */
    private fun tap(item: TableItem) {
        lateinit var owner: TableGestureCoordinator
        owner = TableGestureCoordinator({ intent ->
            if (intent is TableIntent.Actions) owner.actionItem?.let { k.toolkit.tap(owner.capturedActions ?: intent, it, k.scene()) }
        })
        owner.update(k.scene(), true)
        owner.down(item.bounds.center, 0); owner.up(item.bounds.center, 40); owner.tick(1_000)
        k.settle()
    }

    /** Alice's group of Ember Cubs. */
    private fun cubs(): BattlefieldGroup = k.field("Ember Cub").map(k::atw).toSet().let { cubs ->
        k.scene().battlefieldGroups.single { g -> g.members.any { it in cubs } } }

    // CMB-047, CMB-071, CMB-070, CMB-028, TBL-130, TBL-163, TBL-053, TBL-178, TBL-230 (owner playtests 2026-11-07, 2026-11-20): a tap on the stack
    // stages its topmost unstaged Cub; the stack keeps its members (it doesn't regroup), the staged Cubs lie together in one
    // pile at its right end, each staged one on top of those before, with the staged look; the unstaged ones stay exactly
    // where they were, the ×N counting those left; declared, the attacking Cubs still lie in one pile right of the one at home
    @Test fun tapsOnAStackStageItsMembersOneByOne() {
        aliceDeclaringWithThreeCubs()
        val stack = cubs()
        assertEquals(3, stack.members.size, "one stack of three")
        val (bottom, middle, top) = stack.members
        // Where a card lies on Alice's battlefield (from its left end and its bottom edge: the opening band moves it whole)
        fun place(id: String) = k.scene().let { scene -> scene.fields.last().let { f ->
            k.item(id).bounds.let { b -> (b.x - f.originX) to (f.gridBounds.bottom - b.y) } } }
        val before = stack.members.associateWith { k.item(it).bounds }
        val placed = stack.members.associateWith(::place)
        assertEquals(3, k.item(top).copies)
        tap(k.item(bottom))
        assertEquals(listOf(top), k.alice.play().board.combat?.pairs.orEmpty().flatMap { it.attackers }.map { it.sourceId },
            "the topmost Cub staged")
        assertEquals(stack.key, cubs().key, "the stack doesn't regroup")
        assertEquals(mapOf(top to CombatRole.ATTACKER), cubs().staged)
        assertEquals(placed.getValue(bottom), place(bottom), "the unstaged Cubs stay where they were")
        assertEquals(placed.getValue(middle), place(middle))
        assertTrue(k.item(top).bounds.x > before.getValue(top).right, "the staged Cub at the stack's right end")
        assertEquals(CombatRole.ATTACKER, k.item(top).staged)
        assertEquals(2, k.item(middle).copies, "×2: the Cubs left")
        tap(k.item(top))
        assertEquals(listOf(top, middle), k.alice.play().board.combat?.pairs.orEmpty().flatMap { it.attackers }.map { it.sourceId },
            "a tap on the stack (its staged Cub too) stages the next unstaged one")
        assertEquals(placed.getValue(bottom), place(bottom), "the last Cub stays where it was")
        assertTrue(k.item(middle).bounds.x > k.item(top).bounds.x && k.item(middle).bounds.x < k.item(top).bounds.right,
            "staged together in one pile, the one staged later on top: ${k.item(top).bounds} ${k.item(middle).bounds}")
        assertEquals(2, k.item(middle).copies, "×2 on the staged pile")
        assertEquals(CombatRole.ATTACKER, k.item(middle).staged)
        assertEquals(null, k.item(bottom).staged)
        render()
        captureShot("$SCREENSHOTS/battlefield/staged-pile.png",
            "Alice declares attackers and taps her stack of three identical Ember Cubs twice: each tap staged the topmost Cub " +
                "not yet staged, so two Cubs attack Bob (their copies in the band's lane) and lie together in one pile ×2 at the " +
                "stack's right end with a red-orange wash and edge, while the third Cub stays where the stack was; the main button " +
                "reads Attack (2).")
        // Declared: the two attacking Cubs lie in one pile of their own right of the Cub at home
        val home = place(bottom)
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (2)")
        k.playOn("the two Cubs attacking") { k.state().combat?.attackers?.size == 2 && k.alice.play().board.priority?.holder != null }
        val attacking = k.scene().battlefieldGroups.single { g -> top in g.members }
        assertEquals(setOf(top, middle), attacking.members.toSet(), "the attackers in one pile")
        assertEquals(BattlefieldGroup.Kind.Stack, attacking.kind)
        assertEquals(home, place(bottom), "the Cub at home stays where it was")
        assertTrue(attacking.members.all { k.item(it).bounds.x > k.item(bottom).bounds.right - 1f }, "the attackers' pile right of it")
        render()
        captureShot("$SCREENSHOTS/battlefield/attackers-pile.png",
            "Alice declared the two Ember Cubs she staged from her stack of three as attackers: tapped and attacking, they lie together " +
                "in one pile ×2 right of the Cub that stays at home, their copies in the combat band's lane.")
    }
}
