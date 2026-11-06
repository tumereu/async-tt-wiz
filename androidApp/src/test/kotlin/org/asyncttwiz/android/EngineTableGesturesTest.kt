package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the table does with taps, double-taps and drags on a game played on engine state (R4b's replacements of the old
 * model's table tests): Alice's table on the simulated server ([EngineTableKit]), every gesture made on her scene and
 * handled by her toolkit as the table screen wires it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineTableGesturesTest {
    private val kit = EngineTableKit()
    private val f = kit.f
    private val toolkit = kit.toolkit

    @After fun forget() = AutomatumBoards.reset()

    // TBL-017: the camera is personal: panning the table moves only Alice's own view and sends nothing to the game
    @Test fun theCameraIsPersonal() {
        kit.opening()
        val before = kit.alice.device.session.tip
        kit.alice.take(kit.scenario.sim.now)
        val start = kit.emptyPoint()
        val (gestures, emitted) = kit.gestures()
        gestures.down(start, 0)
        for (i in 1..6) gestures.move(TablePoint(start.x + 10f * i, start.y + 12f * i), 16L * i)
        gestures.up(TablePoint(start.x + 60f, start.y + 72f), 120)
        gestures.tick(2_000)
        assertTrue(emitted.any { it is TableIntent.Camera }, "a pan: $emitted")
        assertTrue(toolkit.layoutState.value.camera != TableCamera(), "Alice's own camera moved")
        assertTrue(kit.alice.take(kit.scenario.sim.now).isEmpty(), "nothing goes to the server")
        assertEquals(before, kit.alice.device.session.tip)
        assertEquals(TableCamera(), kit.bobToolkit.layoutState.value.camera, "Bob's camera is his own")
    }

    // TRN-034: portrait: each player's row holds their life square and their packed piles
    @Test fun eachPlayersRowHoldsTheirLifeAndPiles() {
        kit.opening()
        val items = kit.scene().items
        for (player in listOf(f.a, f.b)) {
            val life = items.single { it.kind == TableItemKind.LIFE && it.owner == player }
            val piles = items.filter { it.kind == TableItemKind.PILE && it.owner == player &&
                it.zone in setOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE) }
            assertTrue(piles.isNotEmpty())
            for (pile in piles) assertTrue(pile.bounds.y < life.bounds.bottom && life.bounds.y < pile.bounds.bottom,
                "$player's ${pile.zone} on the life square's row")
        }
    }

    // ZON-004, TBL-044: double-tapping a hand card plays it through Automatum (a land onto the battlefield,
    // untapped); a hand card is never turned
    @Test fun aHandDoubleTapPlaysThroughAutomatumAndNeverTaps() {
        kit.opening()
        val land = kit.atw(kit.hand("Mountain").first())
        val (gestures, emitted) = kit.gestures()
        kit.doubleTap(gestures, kit.item(land, PlayZone.HAND).bounds.center)
        assertTrue(emitted.none { it is TableIntent.Rotate }, "a hand card is never turned: $emitted")
        assertTrue(emitted.any { it is TableIntent.Move && it.handDoubleTap }, "$emitted")
        kit.settle()
        assertTrue(land in kit.field("Mountain").map(kit::atw), "played onto the battlefield: ${toolkit.error}")
        assertFalse(kit.alice.play().board.cards.single { it.id == land }.tapped)
        assertEquals(kit.scenario.sim.head, kit.alice.device.session.head, "through Automatum: the server took the land")
    }

    // ZON-013, ZON-078, TBL-118: a library whose top card Automatum offers nothing for shakes on a tap and opens no menu;
    // a double-tap draws nothing; a Graveyard's menu is only Search
    @Test fun aLibraryTapShakesAndADoubleTapDrawsNothing() {
        kit.opening()
        val library = kit.pile(PlayZone.LIBRARY)
        val hand = kit.hand("Mountain").size + kit.hand("Storm Spark").size
        kit.tap(library)
        assertEquals(library.id, toolkit.shaking?.id, "the library shakes")
        assertNull(toolkit.panel, "no menu")
        assertNull(toolkit.error, "no message")
        val (gestures, emitted) = kit.gestures()
        kit.doubleTap(gestures, library.bounds.center)
        kit.settle()
        assertTrue(emitted.none { it is TableIntent.Move || it is TableIntent.Submit }, "nothing drawn: $emitted")
        assertEquals(hand, kit.hand("Mountain").size + kit.hand("Storm Spark").size)
        val graveyard = kit.pile(PlayZone.GRAVEYARD)
        kit.tap(graveyard)
        assertEquals("pile/${graveyard.id}", toolkit.panel, "the Graveyard's menu: only Search")
    }

    // ZON-077, ZON-004: a hand card dragged onto the Battlefield is played as with a double-tap; a drag that starts on a
    // Battlefield card (toward the Graveyard) pans the table: nothing is lifted, said or sent
    @Test fun aDragIsWhatItMeansToAutomatum() {
        kit.opening()
        val land = kit.atw(kit.hand("Mountain").first())
        val field = kit.scene().fields.last().bounds.center
        kit.gestures().let { (gestures, emitted) ->
            kit.drag(gestures, kit.item(land, PlayZone.HAND).bounds.center, field)
            assertTrue(emitted.any { it is TableIntent.Move && it.drag }, "$emitted")
        }
        kit.settle()
        assertTrue(land in kit.field("Mountain").map(kit::atw), "played: ${toolkit.error}")
        val tip = kit.alice.device.session.tip
        kit.gestures().let { (gestures, emitted) ->
            kit.drag(gestures, kit.item(land).bounds.center, kit.pile(PlayZone.GRAVEYARD).bounds.center)
            assertTrue(emitted.none { it is TableIntent.Move } && emitted.any { it is TableIntent.Camera }, "$emitted")
        }
        assertNull(toolkit.shaking, "nothing shakes")
        assertNull(toolkit.error, "no message")
        kit.settle()
        assertEquals(tip, kit.alice.device.session.tip, "nothing was sent")
        assertTrue(land in kit.field("Mountain").map(kit::atw))
    }

    // ZON-079, TBL-118: a tap raises a hand card Automatum lets Alice cast, with the Mountain Autotap would tap
    // outlined; a tap elsewhere lowers it; the raised card's second tap casts it; a card she can't cast now only shakes
    @Test fun aTapRaisesAHandCardAndItsSecondTapCastsIt() {
        kit.opening()
        val spark = kit.atw(kit.hand("Storm Spark").first())
        // No land yet: Storm Spark can't be cast; Inspect says why
        kit.tap(kit.item(spark, PlayZone.HAND))
        assertNull(toolkit.hand.raised, "not raised")
        assertEquals(spark, toolkit.shaking?.id, "it shakes")
        assertTrue(toolkit.cardPlays(kit.alice.play())!!.of(spark).none { it.legal }, "Inspect lists why")
        val mountain = kit.playLand()
        kit.tap(kit.item(spark, PlayZone.HAND))
        assertEquals(spark, toolkit.hand.raised, "raised")
        assertEquals(setOf(mountain), toolkit.hand.options?.sources, "the Mountain Autotap would tap is outlined")
        kit.tap(kit.item(mountain))
        assertNull(toolkit.hand.raised, "a tap elsewhere lowers it")
        toolkit.dismiss(); toolkit.closeAbilities()
        kit.tap(kit.item(spark, PlayZone.HAND))
        kit.tap(kit.item(spark, PlayZone.HAND))
        assertTrue(toolkit.cast.open, "the second tap casts it: its target is asked")
        assertNull(toolkit.hand.raised)
    }

    // TBL-122, TBL-120: one tap on an untapped Mountain adds {R} to Alice's pool at once (no menu, she keeps priority);
    // a tap on a tapped Mountain opens its menu
    @Test fun oneTapActivatesAManaAbility() {
        kit.opening()
        val mountain = kit.playLand()
        kit.tap(kit.item(mountain))
        assertNull(toolkit.panel); assertNull(toolkit.capture, "no menu")
        kit.settle()
        assertEquals(1, kit.state().player(kit.me).manaPool.size, "{R} in Alice's pool")
        assertEquals(kit.me, kit.state().priority.holder, "she keeps priority")
        assertEquals(1, kit.alice.play().board.players.single { it.accountId == f.a }.manaPool.size, "the pool row shows it")
        kit.tap(kit.item(mountain))
        assertNotNull(toolkit.capture, "the tapped Mountain opens its menu")
    }

    // TBL-117: the cards Alice can do something with now glow: Storm Spark with a Mountain untapped; a land whose only use
    // is its mana ability doesn't, Bob's cards never do; nothing glows during the opening or on the timeline
    @Test fun playableCardsGlow() {
        assertEquals(emptySet(), toolkit.playable(kit.alice.play()), "nothing glows during the opening")
        kit.opening()
        val mountain = kit.playLand()
        val sparks = kit.hand("Storm Spark").map(kit::atw).toSet()
        val glowing = toolkit.playable(kit.alice.play())
        assertTrue(sparks.isNotEmpty() && glowing.containsAll(sparks), "$glowing")
        assertFalse(mountain in glowing, "a land with only its mana ability")
        assertTrue(kit.bobToolkit.playable(kit.bob.play()).none { id -> kit.bob.play().board.cards.find { it.id == id }?.owner == f.a },
            "Bob's table never glows Alice's cards")
        kit.routes.enterReview(kit.alice.timeline.events(kit.alice, emptyMap()).first().id)
        assertEquals(emptySet(), toolkit.playable(kit.alice.play()), "nothing glows on the timeline")
    }

    // CMB-001, CMB-028: the combat area appears when an attacker is staged for Automatum's declaration and Automatum closes
    // it when combat is over; nobody opens or closes it by hand
    @Test fun theCombatAreaOpensForStagingAndAutomatumClosesIt() {
        val cub = kit.toReadyCub()
        kit.toAttacks()
        assertNull(kit.alice.play().board.combat)
        kit.tap(kit.item(cub))
        kit.settle()
        assertNotNull(kit.alice.play().board.combat, "staging opened the area")
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "Attack (1)")
        kit.playOn("combat over") {
            if (kit.alice.device.status == GameDevice.Status.YourMove && kit.state().priority.holder == kit.me &&
                kit.state().turn.step != TurnStep.PostcombatMain) toolkit.automatumStep(TableActionIntent.Next(kit.scope))
            kit.state().turn.step == TurnStep.PostcombatMain
        }
        assertNull(kit.alice.play().board.combat, "Automatum closed it")
    }

    // CMB-032: a tap on a combat copy selects it; a tap on the selected copy opens its Combat menu; a tap on the empty
    // table clears the selection; the selection lasts only for the combat step it was made in
    @Test fun aCombatCopyIsSelectedByATap() {
        val cub = kit.toReadyCub()
        kit.toAttacks()
        kit.tap(kit.item(cub))
        kit.settle()
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(kit.scope)), "Attack (1)")
        kit.playOn("Bob declaring blockers", bobActs = false) { kit.state().turn.step == TurnStep.DeclareBlockers }
        fun copy() = kit.scene().items.first { it.viewport == "combat" && it.sourceId == cub }
        kit.tap(copy())
        assertEquals(cub, toolkit.combat.selected, "selected")
        assertNull(toolkit.capture)
        kit.emptyTap()
        assertNull(toolkit.combat.selected, "a tap on the empty table clears it")
        kit.tap(copy())
        kit.tap(copy())
        assertNotNull(toolkit.capture, "the selected copy's tap opens its Combat menu")
        toolkit.dismiss()
        kit.tap(copy())
        assertEquals(cub, toolkit.combat.selected)
        kit.playOn("the next step") {
            if (kit.alice.device.status == GameDevice.Status.YourMove && kit.state().priority.holder == kit.me &&
                kit.state().turn.step == TurnStep.DeclareBlockers) toolkit.automatumStep(TableActionIntent.Next(kit.scope))
            kit.state().turn.step != TurnStep.DeclareBlockers
        }
        assertNull(toolkit.combat.selected, "it lasts only for its combat step")
        assertTrue(Engine.options(kit.state(), kit.me).none { it is Input.DeclareAttackers })
    }
}
