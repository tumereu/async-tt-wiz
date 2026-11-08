package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Declaring attackers (owner, 2026-11-04) through Alice's table on the simulated server with Bob's game beside it
 * ([EngineTableKit]): her Cinder Hound has an ability ("{R}: +1/+0"), Bob has an Ash Oracle (a planeswalker) to attack.
 * Control stops at the beginning of combat, where one tap pumps the Hound; while attackers are chosen a tap on the
 * Hound only stages or unstages it, nothing can be activated, and its target is picked in the lanes alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineDeclaringAttackersTest {
    private val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(declaring = true)))
    private val f = kit.f
    private val toolkit = kit.toolkit

    @After fun forget() = AutomatumBoards.reset()

    private fun obj(card: String): ObjectId = kit.state().battlefield.single { (it as? ObjectId.Card)?.card?.value == card }
    private fun power(card: String): Int? = Engine.view(kit.state())[obj(card)]?.power
    private fun attackDraft() = kit.alice.device.model.table.attackDraft.orEmpty()

    /** A tap on what Alice sees of [card]: its combat copy when staged (the combat area covers the cards under it,
     * CMB-007), else its real card's visible part. */
    private fun tapSeen(card: String) {
        val items = kit.scene().items.filter { it.kind == TableItemKind.CARD && it.sourceId == card && it.visible.isNotEmpty() &&
            (it.viewport == "combat" || !it.projection) }
        val item = items.firstOrNull { it.viewport == "combat" } ?: items.first { it.zone == org.asyncttwiz.protocol.PlayZone.BATTLEFIELD }
        val point = item.visible.first().center
        val (gestures, _) = kit.gestures()
        gestures.down(point, 0); gestures.up(point, 40); gestures.tick(1_000)
    }

    /** Alice passes with the step arrow wherever she holds priority in Bob's turn (Bob's spell, his end step). */
    private fun alicePassesInBobsTurn() {
        val s = kit.state()
        if (kit.alice.device.status == GameDevice.Status.YourMove && s.turn.active == kit.other && s.priority.holder == kit.me)
            assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "Alice passes in Bob's turn")
    }

    /** From the start to Alice's next Main 1 with her Hound ready and Bob's Ash Oracle on the battlefield. */
    private fun toReadyHound(): Pair<String, String> {
        kit.opening()
        kit.playLand()
        val hound = kit.atw(kit.hand("Cinder Hound").first())
        assertTrue(toolkit.cast.start(hound), "the Hound is cast: ${toolkit.error}")
        kit.playOn("the Hound resolving") { hound in kit.field("Cinder Hound").map(kit::atw) && kit.aliceMain() }
        // Bob's Main 1: a Mountain, then his Ash Oracle
        kit.bobMain = { s ->
            val land = kit.scenario.cards(s, kit.other, s.player(kit.other).hand, "Mountain").firstOrNull()
            val oracle = kit.scenario.cards(s, kit.other, s.player(kit.other).hand, "Ash Oracle").firstOrNull()
            when {
                (s.turn.landsPlayed[kit.other] ?: 0) == 0 && land != null -> { kit.bob.send(listOf(Input.PlayLand(kit.other, land))); true }
                oracle != null && s.battlefield.none { s.obj(it)?.definition?.faces?.first()?.name == "Ash Oracle" } &&
                    kit.scenario.cards(s, kit.other, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false } -> {
                    kit.bob.send(kit.scenario.cast(s, kit.other, Input.Cast(kit.other, oracle))); true
                }
                else -> false
            }
        }
        assertTrue(toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Alice's next Main 1 with a ready Hound and Bob's Oracle", rounds = 40) {
            alicePassesInBobsTurn()
            kit.aliceMain() && kit.field("Ash Oracle", kit.other).isNotEmpty() && Engine.view(kit.state())[obj(hound)]?.summoningSick == false
        }
        kit.bobMain = null
        return hound to kit.atw(kit.field("Ash Oracle", kit.other).single())
    }

    // CMB-028, CMB-047, STK-036, TBL-122, CMB-015, CMB-012: Control stops at the beginning of combat (the arrow reads Next)
    // and one tap there activates the Hound's ability; while attackers are chosen nothing can be activated and a tap on
    // the Hound only stages or unstages it (at the lane made active by its header); dragging its copy between lanes
    // retargets it; the arrow attacks the Oracle
    @Test fun fullControlActsBeforeAttacksAndTapsOnlyStageAtTheLanesTarget() {
        val (hound, oracle) = toReadyHound()
        // HIS-096: with Bob away, Alice plays on
        kit.alice.settings(kit.alice.shown!!.settings.copy(mode = ResponseMode.FullControl, assume = org.asyncttwiz.protocol.game.AssumeResolves.Always))
        kit.scenario.online[f.b] = false
        assertEquals(AutomatumStep.ToCombat, AutomatumStep.of(kit.alice.play()))
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "To combat")
        kit.settle(bobActs = false)
        assertEquals(TurnStep.BeginningOfCombat, kit.state().turn.step, "Control stops at the beginning of combat")
        assertEquals(kit.me, kit.state().priority.holder)
        assertEquals(GameDevice.Status.YourMove, kit.alice.device.status)
        assertEquals(AutomatumStep.Next, AutomatumStep.of(kit.alice.play()))

        // Before attacks: one tap activates the Hound's pump (TBL-122), Autotap paying; it resolves and Alice is back there
        assertEquals(2, power(hound))
        kit.tap(kit.item(hound))
        assertNull(toolkit.error)
        kit.settle(bobActs = false)
        assertEquals(1, kit.state().stack.size, "the pump is on the Stack")
        // Control: Alice keeps priority after activating (STK-036); the arrow lets it resolve
        kit.playOn("the pump resolving", bobActs = false) {
            if (kit.alice.device.status == GameDevice.Status.YourMove && kit.state().priority.holder == kit.me && kit.state().stack.isNotEmpty())
                assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "Resolve")
            kit.state().stack.isEmpty() && power(hound) == 3 && kit.state().priority.holder == kit.me
        }
        assertEquals(TurnStep.BeginningOfCombat, kit.state().turn.step, "still before attacks")
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "Next")
        kit.playOn("Alice declaring attackers", bobActs = false) { Engine.options(kit.state(), kit.me).any { it is Input.DeclareAttackers } }

        // While attackers are chosen nothing can be activated: no ability chips, every menu row greyed
        val plays = toolkit.cardPlays(kit.alice.play())!!
        assertTrue(plays.byCard.values.flatten().none { it.legal }, "nothing to activate or cast")
        assertEquals(emptyList(), toolkit.activatable(kit.alice.play(), hound))
        // A tap on the Hound stages it (at the defending player, the active lane), never pumping it
        tapSeen(hound)
        kit.settle(bobActs = false)
        assertNull(toolkit.error)
        assertEquals(listOf(TargetRef.Player(kit.other)), attackDraft().map { it.target }, "staged at Bob")
        assertTrue(kit.state().stack.isEmpty() && power(hound) == 3, "no ability activated")
        assertNull(toolkit.abilityCard, "no ability chips")
        // The Oracle has its lane; its header's tap makes it the active lane; the Hound's copy is selected by a tap and
        // unstaged by the next (CMB-003, CMB-032, owner 2026-11-06), the tap after that stages it there
        val lane = kit.scene().combatLabels.single { it.cardId == oracle }
        toolkit.combat.chooseTarget(kit.alice.play(), lane.cardId)
        assertEquals(oracle, toolkit.combat.activeTarget(kit.alice.play()))
        tapSeen(hound)
        kit.settle(bobActs = false)
        assertEquals(hound, toolkit.combat.selected, "selected")
        assertEquals(1, attackDraft().size, "still staged")
        tapSeen(hound)
        kit.settle(bobActs = false)
        assertEquals(emptyList(), attackDraft(), "unstaged")
        tapSeen(hound)
        kit.settle(bobActs = false)
        assertEquals(listOf<TargetRef>(TargetRef.Object(obj(oracle))), attackDraft().map { it.target }, "staged at the Oracle")
        assertTrue(kit.state().stack.isEmpty() && power(hound) == 3, "still no ability activated")

        // CMB-015: dragging the staged Hound's copy into another lane retargets it, both ways
        for (target in listOf(null, oracle)) {
            val scene = kit.scene()
            val copy = scene.items.first { it.viewport == "combat" && it.sourceId == hound }
            val drop = scene.combatDrops.first { it.destination.role == CombatRole.ATTACKER && it.destination.attackTarget == target }
            val (gestures, _) = kit.gestures()
            kit.drag(gestures, copy.visible.first().center, drop.landing)
            kit.settle(bobActs = false)
            assertNull(toolkit.error, "retargeted by the drag")
            val expected = target?.let { TargetRef.Object(obj(it)) } ?: TargetRef.Player(kit.other)
            assertEquals(listOf(expected), attackDraft().map { it.target })
        }

        // The arrow attacks the Oracle with the pumped Hound: 3 damage, its 3 loyalty gone
        assertEquals(AutomatumStep.Attack(1), AutomatumStep.of(kit.alice.play()))
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "Attack (1)")
        assertNull(toolkit.error)
        kit.scenario.online[f.b] = true
        val life = kit.state().player(kit.other).life
        kit.playOn("the Hound's damage to the Oracle", rounds = 40) {
            if (kit.alice.device.status == GameDevice.Status.YourMove && kit.state().priority.holder == kit.me &&
                kit.state().turn.step != TurnStep.PostcombatMain) toolkit.automatumStep(TableActionIntent.Next(kit.scope))
            kit.field("Ash Oracle", kit.other).isEmpty()
        }
        assertEquals(life, kit.state().player(kit.other).life, "the Oracle took the damage, not Bob")
    }

    // STK-036, CMB-028: in Normal there is no stop at the beginning of combat: To combat goes on to declaring attackers
    @Test fun normalGoesStraightToDeclaringAttackers() {
        toReadyHound()
        kit.alice.settings(kit.alice.shown!!.settings.copy(mode = ResponseMode.Normal))
        kit.scenario.online[f.b] = false
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "To combat")
        kit.settle(bobActs = false)
        assertEquals(TurnStep.DeclareAttackers, kit.state().turn.step)
        assertTrue(Engine.options(kit.state(), kit.me).any { it is Input.DeclareAttackers }, "Alice declares attackers")
    }

    // CMB-034, CMB-031: a change naming the lanes Automatum shows is taken ("Action could not be queued" before): Bob
    // blocks on his table by taps (his Cub picked, then Alice's attacking Cub), takes the block back by tapping the
    // attacker again and blocks again; the arrow declares it
    @Test fun theDefenderBlocksByTaps() {
        val k = EngineTableKit()
        val bobScope = TableActionScope(k.f.game.id, k.bob.play().playId)
        k.bobMain = { s ->
            val land = k.scenario.cards(s, k.other, s.player(k.other).hand, "Mountain").firstOrNull()
            val cub = k.scenario.cards(s, k.other, s.player(k.other).hand, "Ember Cub").firstOrNull()
            when {
                (s.turn.landsPlayed[k.other] ?: 0) == 0 && land != null -> { k.bob.send(listOf(Input.PlayLand(k.other, land))); true }
                cub != null && k.scenario.cards(s, k.other, s.battlefield, "Ember Cub").isEmpty() &&
                    k.scenario.cards(s, k.other, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false } -> {
                    k.bob.send(k.scenario.cast(s, k.other, Input.Cast(k.other, cub))); true
                }
                else -> false
            }
        }
        val attacker = k.toReadyCub()
        k.bobMain = null
        val blocker = k.atw(k.field("Ember Cub", k.other).single())
        k.toAttacks()
        k.tap(k.item(attacker))
        k.settle()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (1)")
        k.playOn("Bob declaring blockers", bobActs = false) {
            Engine.options(k.bob.device.model.engine, k.other).any { it is Input.DeclareBlockers }
        }
        fun tapOnBobsTable(card: String, copy: Boolean) {
            val item = k.scene(k.f.b).items.first { it.kind == TableItemKind.CARD && it.sourceId == card && it.visible.isNotEmpty() &&
                (if (copy) it.viewport == "combat" else it.viewport != "combat" && !it.projection) }
            val point = item.visible.first().center
            val (gestures, _) = k.gestures(k.bobToolkit, k.f.b)
            gestures.down(point, 0); gestures.up(point, 40); gestures.tick(1_000)
            k.settle(bobActs = false)
            assertNull(k.bobToolkit.error)
        }
        fun blocks() = k.bob.device.model.table.blockDraft.orEmpty().map { k.atw(it.blocker) to k.atw(it.attacker) }
        tapOnBobsTable(blocker, copy = false)
        assertEquals(blocker, k.bobToolkit.combat.selected, "Bob's Cub is the blocker to place")
        tapOnBobsTable(attacker, copy = true)
        assertEquals(listOf(blocker to attacker), blocks(), "blocked")
        tapOnBobsTable(blocker, copy = true)
        tapOnBobsTable(attacker, copy = true)
        assertEquals(emptyList(), blocks(), "the block taken back")
        assertEquals(blocker, k.bobToolkit.combat.selected, "still the blocker to place")
        tapOnBobsTable(attacker, copy = true)
        assertEquals(listOf(blocker to attacker), blocks(), "blocked again")
        assertEquals(AutomatumStep.Block(1), AutomatumStep.of(k.bob.play()))
        assertTrue(k.bobToolkit.automatumStep(TableActionIntent.Next(bobScope)), "Block (1)")
        k.settle(bobActs = false)
        assertEquals(listOf(blocker), k.state().combat?.blockers.orEmpty().map { k.atw(it.id) }, "declared")
    }
}
