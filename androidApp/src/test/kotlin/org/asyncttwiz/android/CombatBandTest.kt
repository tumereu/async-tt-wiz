package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasStateDescription
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The combat band (owner playtest 2026-11-06): always between the battlefields, collapsed to the row of target chips while
 * attackers are declared with none staged, opened by staging the first attacker, its lanes under the chips; the creatures
 * that may attack glowing red-orange (more under a finger); the defender's blocks with their arrows; the attacker placing
 * blocks to assume on the lanes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CombatBandTest {
    @get:Rule val compose = createComposeRule()
    private val k = EngineTableKit()

    @After fun forget() = AutomatumBoards.reset()

    /** [viewer]'s table as [EngineTableScreen] draws it (the blocks placed to assume on the attacker's lanes). */
    private fun render(viewer: String = k.f.a, landscape: Boolean = false) {
        val game = if (viewer == k.f.a) k.alice else k.bob
        val routes = if (viewer == k.f.a) k.routes else k.bobRoutes
        val toolkit = if (viewer == k.f.a) k.toolkit else k.bobToolkit
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            val play = game.play().let { b -> routes.assuming()?.board(b) ?: b }
                            TableSurface(k.f.game, play, viewer, landscape, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() }, controlsConnected = true,
                                    waitingLine = routes.waitingLine(play)))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Bob's Main 1: a Mountain, then an Ember Cub, so he has a creature to block with. */
    private fun bobCastsACub() {
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
    }

    /** Alice declaring attackers with a ready Cub (Bob has a Cub too); the combat worked out. Returns her Cub. */
    private fun aliceDeclaring(): String {
        bobCastsACub()
        val attacker = k.toReadyCub()
        k.bobMain = null
        k.toAttacks()
        k.toolkit.combat.show(k.alice.play(), AutomatumBoards.combat(k.alice.play()))
        return attacker
    }

    /** Alice has attacked with three Ember Cubs (a Cub in her first turn, two in her second); Bob, with a Cub of his own and
     * an untapped creature to block with, is declaring blockers, the combat worked out on his table. */
    private fun bobDeclaringAgainstThree(): List<String> {
        val cubs = aliceStagedThree()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (3)")
        k.playOn("Bob declaring blockers", bobActs = false) {
            Engine.options(k.bob.device.model.engine, k.other).any { it is Input.DeclareBlockers }
        }
        k.bobToolkit.combat.show(k.bob.play(), AutomatumBoards.combat(k.bob.play()))
        return cubs
    }

    /** Alice declares attackers with three Ember Cubs (a Cub in her first turn, two in her second) staged against Bob, who
     * has a Cub of his own. */
    private fun aliceStagedThree(): List<String> {
        bobCastsACub()
        k.toReadyCub()
        k.playLand()
        k.castCub()
        k.castCub()
        k.bobMain = null
        assertTrue(k.toolkit.automatumStep(TableActionIntent.End(k.scope)), "End turn")
        k.playOn("Alice's Main 1 with three ready Cubs") { k.aliceMain() && k.scenario.creatures(k.state(), k.me, ready = true).size == 3 }
        k.toAttacks()
        k.toolkit.combat.show(k.alice.play(), AutomatumBoards.combat(k.alice.play()))
        val cubs = k.field("Ember Cub").map(k::atw)
        assertTrue(k.toolkit.stage(k.alice.play(), cubs, null), "all three staged")
        k.settle()
        return cubs
    }

    private fun slots() = compose.onAllNodesWithTag(COMBAT_SLOT_TAG, useUnmergedTree = true)

    // CMB-034 (owner, 2026-11-08): for the whole declare blockers step each attacker has a dashed slot, brighter once the
    // creature selected may block it; "Choose blockers" on the band until the first block; a tap on a slot blocks as a tap on
    // the attacker does and the slot goes
    @Test fun eachAttackerHasADashedBlockerSlot() {
        bobDeclaringAgainstThree()
        val blocker = k.atw(k.field("Ember Cub", k.other).single())
        render(k.f.b)
        slots().assertCountEquals(3)
        compose.onNodeWithText(CHOOSE_BLOCKERS).assertExists()
        compose.onAllNodes(hasTestTag(COMBAT_SLOT_TAG) and hasStateDescription("free"), useUnmergedTree = true).assertCountEquals(3)
        captureShot("$SCREENSHOTS/combat-band/blocker-slots.png",
            "Alice attacks with three Ember Cubs and Bob declares blockers: in the band, mirrored for him, each attacker has a dashed blocker slot, \"Choose blockers\" sits between the rows until the first block, and his creature that may block glows red-orange.")
        k.tap(k.item(blocker, viewer = k.f.b), k.bobToolkit, k.f.b)
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag(COMBAT_SLOT_TAG) and hasStateDescription("may go here"), useUnmergedTree = true).assertCountEquals(3)
        captureShot("$SCREENSHOTS/combat-band/blocker-slots-selected.png",
            "Bob tapped his Ember Cub to place it: it is framed violet, the three attackers it may block glow violet and their dashed slots are brighter.")
        val slot = k.scene(k.f.b).combatSlots.first { it.role == CombatRole.BLOCKER }
        compose.runOnIdle { assertTrue(k.bobToolkit.tapCombatSlot(k.scene(k.f.b), slot.bounds.center), "the tap on the slot") }
        k.settle(bobActs = false)
        compose.waitForIdle()
        assertEquals(1, k.bob.device.model.table.blockDraft.orEmpty().size, "the block staged")
        assertEquals(2, k.scene(k.f.b).combatSlots.count { it.role == CombatRole.BLOCKER }, "the filled slot gone")
    }

    // CMB-034, CMB-007 (owner, 2026-11-08): the slots with several attackers in landscape
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun theBlockerSlotsInLandscape() {
        bobDeclaringAgainstThree()
        render(k.f.b, landscape = true)
        slots().assertCountEquals(3)
        captureShot("$SCREENSHOTS/combat-band/blocker-slots-landscape.png",
            "In landscape Alice attacks with three Ember Cubs and Bob declares blockers: the band, zoomed with the battlefields, holds a dashed slot over each attacker and \"Choose blockers\" between the rows.")
    }

    // CMB-028 (owner, 2026-11-08): declaring attackers, the active lane has the same dashed slot at its end, in room the lane
    // keeps for it: it moves on as attackers join; the collapsed band has none
    @Test fun theActiveLaneHasADashedAttackerSlot() {
        val attacker = aliceDeclaring()
        assertTrue(k.scene().combatSlots.isEmpty(), "the collapsed band has none")
        k.tap(k.item(attacker))
        k.settle()
        render()
        slots().assertCountEquals(1)
        val scene = TablePresentation.scene(k.f.game, k.alice.play(), k.f.a, 412f, 892f, false, attackerSlots = true)
        val copy = scene.items.first { it.viewport == "combat" && it.sourceId == attacker }
        val slot = scene.combatSlots.single { it.role == CombatRole.ATTACKER }
        assertEquals(copy.bounds.y, slot.bounds.y, .001f, "in the attackers' row")
        assertTrue(slot.bounds.x >= copy.bounds.right, "after the attacker")
        assertEquals(null, scene.combatDestination(slot.bounds.center)!!.pairId, "a drop on it is a new attacker")
        captureShot("$SCREENSHOTS/combat-band/attacker-slot.png",
            "Alice staged her Ember Cub: the band opened and the lane of her active target, Bob, has a dashed slot for the next attacker beside it.")
    }

    // CMB-012, CMB-028 (owner playtest 2026-11-10): when the attack is declared and the slot goes the attackers stay put: the
    // slot takes no part in placing them, it only sits in the room the lane keeps after them
    @Test fun theAttackersStayPutWhenTheSlotGoes() {
        val cubs = aliceStagedThree()
        fun copies(slots: Boolean) = TablePresentation.scene(k.f.game, k.alice.play(), k.f.a, 412f, 892f, false, attackerSlots = slots)
            .let { scene -> scene to cubs.associateWith { cub -> scene.items.single { it.viewport == "combat" && it.sourceId == cub }.bounds } }
        val (staging, staged) = copies(true)
        val slot = staging.combatSlots.single { it.role == CombatRole.ATTACKER }
        assertTrue(staged.values.all { slot.bounds.x >= it.right }, "the slot after them")
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (3)")
        k.playOn("the attack declared", bobActs = false) { k.state().combat?.attackers?.size == 3 }
        val (declared, after) = copies(false)
        assertTrue(declared.combatSlots.none { it.role == CombatRole.ATTACKER }, "no attacker slot once declared")
        for (cub in cubs) {
            assertEquals(staged.getValue(cub).x, after.getValue(cub).x, .001f, "$cub stays put")
            assertEquals(staged.getValue(cub).y, after.getValue(cub).y, .001f, "$cub stays put")
        }
    }

    /** The phone shown by [show] (whose table, a board of theirs or their live one, and a count that redraws it). */
    private var phone by androidx.compose.runtime.mutableStateOf(Triple<String, TableBoard?, Int>("", null, 0))

    /** [viewer]'s table as [render] draws it, with [board] instead of their live board when given; one content for the test,
     * changed between pictures. */
    private fun show(viewer: String, board: TableBoard? = null) {
        val first = phone.first.isEmpty()
        phone = Triple(viewer, board, phone.third + 1)
        if (first) compose.setContent {
            val (who, fixed, n) = phone
            val game = if (who == k.f.a) k.alice else k.bob
            val routes = if (who == k.f.a) k.routes else k.bobRoutes
            val toolkit = if (who == k.f.a) k.toolkit else k.bobToolkit
            androidx.compose.runtime.key(who, n) {
                MaterialTheme {
                    NoRipples {
                        CompositionLocalProvider(LocalEngineRoutes provides routes) {
                            Box(Modifier.fillMaxSize()) {
                                val play = fixed ?: game.play().let { b -> routes.assuming()?.board(b) ?: b }
                                TableSurface(k.f.game, play, who, false, Modifier.fillMaxSize(), connected = true,
                                    onIntent = toolkit::handle, toolkit = toolkit,
                                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() }, controlsConnected = true,
                                        waitingLine = routes.waitingLine(play)))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The board [viewer]'s table showed last that [which] picks among the steps of its transitions (the board right after
     * an engine step: where a combat hold stops the steps, HIS-113). */
    private fun step(viewer: String, which: (TableBoard) -> Boolean): TableBoard? =
        k.scenario.shown[viewer].orEmpty().flatMap { it.steps }.lastOrNull(which)

    // CMB-050, HIS-113, CMB-034, CMB-040 (owner, 2026-11-10: gallery before the release that ships the damage on the band): the
    // whole combat from both phones, one picture per pause: the attack shown, waiting for the blocks, the blocks shown, the
    // damage's hold with what it did on the band, and after the combat
    @Test fun theWholeCombatFromBothPhones() {
        val cubs = aliceStagedThree()
        val blocker = k.atw(k.field("Ember Cub", k.other).single())
        val bobScope = TableActionScope(k.f.game.id, k.bob.play().playId)
        // Bob answers his own stops (his phone stops where Alice's assumes he doesn't respond)
        k.scenario.confirmStops[k.f.b] = false
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (3)")
        k.playOn("Bob's stop at Alice's attack", bobActs = false) { k.bob.device.status is GameDevice.Status.Stopped }
        // Her own attack folds what follows it into her action: the board at its event (as the steps show it while it holds)
        val declaration = k.alice.shown!!.line.last { e -> e.engine?.steps?.any { it.input is Input.DeclareAttackers } == true }
        val attack = assertNotNull(k.alice.reviewPlay(declaration.id), "the board at Alice's attack")
        show(k.f.a, attack)
        captureShot("$SCREENSHOTS/combat-sequence/1-attack-alice.png",
            "Alice's phone, 1 of 5, a forced hold (show plays and combat): she attacked Bob with three Ember Cubs and her view holds on the attack in the band for the show-plays time, while her bars already wait for Bob's blocks.")
        show(k.f.b)
        captureShot("$SCREENSHOTS/combat-sequence/1-attack-bob.png",
            "Bob's phone, 1 of 5, a forced hold (show plays and combat), then a stop: Alice's three attacking Cubs show in his band; after the hold his phone stops here, where Alice's phone assumed he doesn't respond, and Pass goes on.")
        show(k.f.a)
        captureShot("$SCREENSHOTS/combat-sequence/2-blocks-awaited-alice.png",
            "Alice's phone, 2 of 5, a wait for the other player: she waits for Bob's blocks; meanwhile her band is in assume mode, a dashed slot over each attacker, and the main button reads Assume no blockers.")
        (k.bob.device.status as GameDevice.Status.Stopped).let { k.bob.act(it.assumed) }
        k.playOn("Bob declaring blockers", bobActs = false) { Engine.options(k.bob.device.model.engine, k.other).any { it is Input.DeclareBlockers } }
        k.bobToolkit.combat.show(k.bob.play(), AutomatumBoards.combat(k.bob.play()))
        show(k.f.b)
        captureShot("$SCREENSHOTS/combat-sequence/2-blocks-awaited-bob.png",
            "Bob's phone, 2 of 5, neither a hold nor a wait (his own move): he declares blockers, a dashed slot over each attacker, \"Choose blockers\" on the band and No blocks on the main button.")
        val pair = k.bob.play().board.combat!!.pairs.first { p -> p.attackers.any { it.sourceId == cubs.first() } }.id
        assertTrue(k.bobToolkit.block(k.bob.play(), blocker, pair), "Bob's Cub blocks: ${k.bobToolkit.error}")
        k.settle(bobActs = false)
        assertTrue(k.bobToolkit.automatumStep(TableActionIntent.Next(bobScope)), "Block (1)")
        k.playOn("Alice's move after the blocks", bobActs = false) {
            k.alice.device.status == GameDevice.Status.YourMove && k.state().combat?.blockers?.isNotEmpty() == true
        }
        k.toolkit.combat.show(k.alice.play(), AutomatumBoards.combat(k.alice.play()))
        k.bobToolkit.combat.show(k.bob.play(), AutomatumBoards.combat(k.bob.play()))
        show(k.f.a)
        captureShot("$SCREENSHOTS/combat-sequence/3-blocks-alice.png",
            "Alice's phone, 3 of 5, no hold (blocks don't hold the view) and no wait (her own move): Bob's Cub blocks her first Cub, the damage preview shows Bob going from 20 to 16 and both blocked Cubs dying, and the main button reads Next.")
        show(k.f.b)
        captureShot("$SCREENSHOTS/combat-sequence/3-blocks-bob.png",
            "Bob's phone, 3 of 5, a wait for the other player: his block is declared and he waits for Alice to go on to the damage, the damage preview on the band.")
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Alice passes")
        k.settle(bobActs = false)
        // Bob may respond: Alice's phone assumes he doesn't (Assume no response), on to the damage
        assertTrue(k.alice.assume() !is GameDevice.Acted.Refused, "Bob's pass assumed")
        // Her own action's steps, the damage's hold board among them (HIS-113)
        val aliceDealt = k.alice.transition?.steps.orEmpty().lastOrNull { it.damageDealt }
        k.playOn("the damage dealt on Alice's phone", bobActs = false) { k.state().turn.step == TurnStep.CombatDamage }
        // CMB-050: Bob's net 4 on his chip, 2 behind each unblocked Cub's blockers' row, nothing for the blocked one
        k.scene().let { scene ->
            assertEquals(mapOf<String?, Int>(null to 4), scene.combatDealt?.targets)
            assertEquals(listOf(2, 2), scene.combatLaneDamage.map { it.amount })
        }
        show(k.f.a, assertNotNull(aliceDealt ?: step(k.f.a) { it.damageDealt }, "the damage's hold board on Alice's phone"))
        captureShot("$SCREENSHOTS/combat-sequence/4-damage-alice.png",
            "Alice's phone, 4 of 5, a forced hold (show plays and combat): the combat damage is dealt; Bob's chip carries a red splash with 4, each unblocked Cub's column a small splash with 2 behind the blockers' row, and the two Cubs that fought still lie in the band until the hold ends.")
        k.scenario.confirmStops[k.f.b] = true
        k.playOn("the damage dealt on Bob's phone", bobActs = false) { k.scenario.shown[k.f.b].orEmpty().any { t -> t.steps.any { it.damageDealt } } }
        show(k.f.b, step(k.f.b) { it.damageDealt })
        captureShot("$SCREENSHOTS/combat-sequence/4-damage-bob.png",
            "Bob's phone, 4 of 5, a forced hold (show plays and combat): the same damage from his side, mirrored: his chip by his battlefield carries the red splash with 4, a small splash with 2 behind each unblocked Cub's empty blocker place.")
        k.playOn("Alice's second main phase") {
            // The combat holds end by their time (HIS-113), as the table ends them
            for (game in listOf(k.alice, k.bob)) game.shown?.combatHold?.let { game.dismissCombatHold(it) }
            val main2 = k.alice.device.status == GameDevice.Status.YourMove && k.state().turn.step == TurnStep.PostcombatMain
            if (!main2 && k.alice.device.status == GameDevice.Status.YourMove) k.toolkit.automatumStep(TableActionIntent.Next(k.scope))
            main2
        }
        assertTrue(k.scene().combatDealt == null && k.scene().combatLaneDamage.isEmpty(), "gone with the combat")
        show(k.f.a)
        captureShot("$SCREENSHOTS/combat-sequence/5-after-alice.png",
            "Alice's phone, 5 of 5, no hold or wait (her own move): the combat is over and she is in her second main phase; the band is closed again and the splashes are gone, Bob at 16 life.")
        show(k.f.b)
        captureShot("$SCREENSHOTS/combat-sequence/5-after-bob.png",
            "Bob's phone, 5 of 5, a wait for the other player: after the combat he waits for Alice in her second main phase; the band is closed again and the splashes are gone.")
    }

    private fun bobName() = listOf(k.f.game.inviter, k.f.game.recipient).single { it.accountId == k.f.b }.username

    // CMB-001, CMB-013, CMB-028 (owner playtest 2026-11-06): while Alice declares attackers with none staged the band
    // stays collapsed at its chip row and shows a chip for Bob (crossed swords, his round avatar, his name), the active
    // target filled violet; the creatures that may attack glow red-orange; a tap on the chip keeps its target active and
    // stages nothing
    @Test fun theCollapsedBandShowsTheTargetChips() {
        val attacker = aliceDeclaring()
        render()
        val scene = k.scene()
        assertNull(scene.combatPanel, "collapsed")
        val band = assertNotNull(scene.combatBand)
        assertEquals(CombatStackPresentation.CHIP_ROW * scene.camera.zoom, band.height, .001f)
        val chip = scene.combatLabels.single()
        assertEquals(null, chip.cardId)
        assertEquals(bobName(), chip.label)
        assertTrue(band.y <= chip.chip.y && band.bottom >= chip.chip.bottom)
        assertTrue(attacker in k.toolkit.combat.attackable(k.alice.play(), k.f.a), "the Cub may attack")
        compose.onNodeWithTag("combat-target-chip").assert(hasContentDescription("Attack ${bobName()}"))
            .assert(hasStateDescription("active"))
        captureShot("$SCREENSHOTS/combat-band/chips.png",
            "Alice declares attackers with nothing staged: the band between the battlefields stays collapsed at its chip row and shows Bob's target chip (crossed swords, his avatar, his name) filled violet as the active target; her Ember Cub, which may attack, glows red-orange and the main button reads No attack.")
        compose.onNodeWithTag("combat-target-chip").performClick()
        compose.waitForIdle()
        assertEquals(null, k.toolkit.combat.activeTarget(k.alice.play()))
        assertEquals(null, k.alice.play().board.combat, "a chip stages nothing")
        assertNull(k.scene().combatPanel)
    }

    // CMB-013 (owner playtest 2026-11-06): holding a chip shows the target's full name over it and highlights the
    // player's life square while held
    @Test fun holdingAChipShowsTheFullNameAndHighlightsTheTarget() {
        aliceDeclaring()
        render()
        compose.onNodeWithTag("combat-target-chip").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Target: ${bobName()}").assertExists()
        captureShot("$SCREENSHOTS/combat-band/chip-held.png",
            "Alice holds Bob's target chip while declaring attackers: his full name shows over the chip and his life square is framed violet while she holds it.")
        compose.onNodeWithTag("combat-target-chip").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Target: ${bobName()}").assertDoesNotExist()
    }

    // TBL-117, CMB-028 (owner playtest 2026-11-06): while a finger is down on a glowing card its glow grows a little and
    // brightens
    @Test fun aFingerOnAGlowingCardGrowsItsGlow() {
        val attacker = aliceDeclaring()
        render()
        val card = k.item(attacker)
        val table = compose.onNodeWithContentDescription("Shared table")
        // The table runs a frame clock while a finger is down: the test's clock is moved by hand meanwhile
        compose.mainClock.autoAdvance = false
        table.performTouchInput { down(androidx.compose.ui.geometry.Offset(card.bounds.center.x, card.bounds.center.y)) }
        compose.mainClock.advanceTimeBy(150)
        captureShot("$SCREENSHOTS/combat-band/pressed.png",
            "Alice's finger is down on her Ember Cub while she declares attackers: its red-orange glow has grown a little and brightened, so it feels pressable.")
        table.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
    }

    // CMB-001, CMB-042 (owner playtest 2026-11-06): staging the first attacker opens the band by itself, its copy in the
    // lane under Bob's chip, exactly the battlefield cards' size; CMB-013 (owner gallery review 2026-11-06): Bob's life on
    // his chip
    @Test fun stagingTheFirstAttackerOpensTheBand() {
        val attacker = aliceDeclaring()
        assertNull(k.scene().combatPanel)
        k.tap(k.item(attacker))
        k.settle()
        val panel = assertNotNull(k.scene().combatPanel)
        val copy = k.scene().items.single { it.viewport == "combat" && it.sourceId == attacker }
        assertTrue(panel.contains(copy.bounds.center))
        val real = k.item(attacker)
        assertEquals(minOf(real.bounds.width, real.bounds.height), copy.bounds.width, .001f)
        assertEquals(maxOf(real.bounds.width, real.bounds.height), copy.bounds.height, .001f)
        val lane = k.scene().combatLabels.single()
        assertTrue(lane.chip.bottom < copy.bounds.y)
        render()
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Attack (1)"))
        compose.onNodeWithContentDescription("${bobName()}: ${lane.value}").assertExists()
        captureShot("$SCREENSHOTS/combat-band/staged.png",
            "Alice tapped her ready Ember Cub: the band opened by itself between the battlefields, pushing them apart, with the Cub's copy at battlefield size in the lane under Bob's chip, his life on the chip after his name, and the main button reading Attack (1).")
    }

    // CMB-001, CMB-007 (owner, 2026-11-05): the open band in landscape
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun theOpenBandInLandscape() {
        val attacker = aliceDeclaring()
        k.tap(k.item(attacker))
        k.settle()
        render(landscape = true)
        captureShot("$SCREENSHOTS/combat-band/staged-landscape.png",
            "In landscape Alice tapped her ready Ember Cub: the band between the battlefields (across the whole width, the Stack at its right end) holds its copy in the lane aimed at Bob, zoomed with the battlefields, and the main button reads Attack (1).")
    }

    // CMB-031, CMB-034 (owner, 2026-11-05): Bob arranges his blocks on the band's lanes: his Cub blocks Alice's attacker,
    // an arrow from blocker to attacker
    @Test fun theDefenderArrangesBlocksOnTheLanes() {
        val attacker = aliceDeclaring()
        val blocker = k.atw(k.field("Ember Cub", k.other).single())
        k.tap(k.item(attacker))
        k.settle()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (1)")
        k.playOn("Bob declaring blockers", bobActs = false) {
            Engine.options(k.bob.device.model.engine, k.other).any { it is Input.DeclareBlockers }
        }
        k.bobToolkit.combat.show(k.bob.play(), AutomatumBoards.combat(k.bob.play()))
        k.tap(k.item(blocker, viewer = k.f.b), k.bobToolkit, k.f.b)
        k.settle(bobActs = false)
        k.tap(k.scene(k.f.b).items.first { it.viewport == "combat" && it.sourceId == attacker }, k.bobToolkit, k.f.b)
        k.settle(bobActs = false)
        assertEquals(listOf(blocker to attacker), k.bob.device.model.table.blockDraft.orEmpty().map { k.atw(it.blocker) to k.atw(it.attacker) })
        render(k.f.b)
        captureShot("$SCREENSHOTS/combat-band/blocks.png",
            "Bob declares blockers: in the band between the battlefields, mirrored for him, his Ember Cub's copy blocks Alice's attacking Cub with an arrow from blocker to attacker, the damage preview on both, and the main button reads Block (1).")
    }

    // CMB-013, CMB-029 (owner gallery review 2026-11-06): while Bob declares blockers with none placed, his target chip shows
    // what the damage preview would make of his life ("20 → 18") in red, the chip widened about its centre
    @Test fun theChipShowsTheDamagePreview() {
        val attacker = aliceDeclaring()
        k.tap(k.item(attacker))
        k.settle()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (1)")
        k.playOn("Bob declaring blockers", bobActs = false) {
            Engine.options(k.bob.device.model.engine, k.other).any { it is Input.DeclareBlockers }
        }
        k.bobToolkit.combat.show(k.bob.play(), AutomatumBoards.combat(k.bob.play()))
        render(k.f.b)
        val lane = k.scene(k.f.b).combatLabels.single { it.cardId == null }
        val life = lane.value.toInt()
        compose.onNodeWithContentDescription("${bobName()} would go from $life to ${life - 2}").assertExists()
        captureShot("$SCREENSHOTS/combat-band/chip-preview.png",
            "Bob declares blockers with none placed yet: his target chip at the bottom of the band, mirrored for him, reads his name and $life → ${life - 2} in red, what Alice's unblocked Ember Cub would do to his life.")
    }

    // CMB-040, CMB-049 (owner, 2026-11-05): Bob is away when Alice attacks; while she waits for his blocks her table is at
    // once in assume mode on the lanes: she places his Cub as a blocker by taps, as he would, and the main button assumes
    // exactly that ("Assume no blockers" before, "Assume blockers" after); nothing is assumed before she presses it
    @Test fun theAttackerPlacesBlocksToAssumeOnTheLanes() {
        val attacker = aliceDeclaring()
        val blocker = k.atw(k.field("Ember Cub", k.other).single())
        k.scenario.online[k.f.b] = false
        k.alice.tick(k.scenario.sim.now + 120_000)
        k.tap(k.item(attacker))
        k.settle()
        assertTrue(k.toolkit.automatumStep(TableActionIntent.Next(k.scope)), "Attack (1)")
        k.settle()
        k.alice.tick(k.scenario.sim.now + 121_000)
        // HIS-096 (owner, 2026-11-06): Bob's pause after her attack is assumed at once; his blockers are a wait with Assume
        // on the main button
        val waiting = assertIs<GameDevice.Status.Waiting>(k.alice.device.status)
        assertEquals(GameDevice.Offer.AssumeBlockers, waiting.offer)
        k.alice.notes.clear()
        val placed = assertNotNull(k.routes.assuming(), "assume mode at once")
        render()
        compose.onNodeWithTag("engine-wait-action").assert(hasContentDescription("Assume no blockers"))
        k.toolkit.combat.show(placed.board(k.alice.play()), AutomatumBoards.combat(placed.board(k.alice.play())))
        // CMB-040 (owner, 2026-11-08): the slot and the words as the defender has them
        compose.waitForIdle()
        slots().assertCountEquals(1)
        compose.onNodeWithText(CHOOSE_BLOCKERS).assertExists()
        captureShot("$SCREENSHOTS/combat-band/assume-slots.png",
            "Alice attacks while Bob is away: waiting for his blocks her band has the defender's look: a dashed blocker slot over her Ember Cub, \"Choose blockers\" between the rows, and his creature that may block glowing red-orange.")
        k.tap(k.item(blocker))
        assertEquals(blocker, k.toolkit.combat.selected, "Bob's Cub is the blocker to place")
        k.tap(k.item(attacker))
        assertEquals(mapOf(blocker to placed.blocks.getValue(blocker)), placed.blocks)
        assertTrue(k.alice.device.session.pending.none { it.assumed?.input is Input.DeclareBlockers }, "nothing assumed yet")
        compose.waitForIdle()
        compose.onNodeWithTag("engine-wait-action").assert(hasContentDescription("Assume blockers"))
        compose.onNodeWithContentDescription("Clear").assertExists()
        // CMB-040 (owner, 2026-11-08): the same look: his creature that may block glows, and the words are gone with the first block
        compose.onNodeWithText(CHOOSE_BLOCKERS).assertDoesNotExist()
        assertTrue(blocker in k.toolkit.combat.blockable(placed.board(k.alice.play()), k.f.a, assuming = true))
        assertTrue(k.toolkit.combat.blockable(placed.board(k.alice.play()), k.f.a, assuming = false).isEmpty())
        captureShot("$SCREENSHOTS/combat-band/assume.png",
            "Bob is away when Alice attacks with her Ember Cub: waiting for his blocks, her table is in assume mode on the band's lanes; she tapped his Cub and then her attacker, so his Cub's copy blocks it with an arrow, Clear is the quick action and the main button reads Assume blockers.")
        val expected = listOf(Block(k.state().battlefield.single { (it as? ObjectId.Card)?.card?.value == blocker },
            k.state().battlefield.single { (it as? ObjectId.Card)?.card?.value == attacker }))
        compose.onNodeWithTag("engine-wait-action").performClick()
        compose.runOnIdle {
            val assumed = k.alice.device.session.pending.firstNotNullOf { it.assumed?.input as? Input.DeclareBlockers }
            assertEquals(expected, assumed.blocks)
        }
    }
}
