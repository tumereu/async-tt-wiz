package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Affected
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Value
import arcanum.engine.state.CardId
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.json.Json
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Automatum I1 (integration spec §3, §5): the switch, its hold panel, computed P/T on the table and Inspect's
 * breakdown. The fixture's cards play with their printed values (no device definitions, I-D21). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val sentinel = f.source(f.a, "1/3")
    private val guide = f.source(f.b, "2/3")
    private val source = f.initial.board.cards.first { it.containerId == f.id("a/GRAVEYARD") }
    /** The card in Alice's graveyard made the effects: an invented "Wild Surge". */
    private val surge = CardDefinition("Wild Surge", listOf(DefinitionFace("surge", "Wild Surge", typeLine = "Instant",
        rulesText = "Target creature gets +2/+2 until end of turn."))).let { SubmittedDefinition(DeckJson.digest(it), it) }

    @After fun forget() = AutomatumBoards.reset()

    private fun effect(id: String, mechanic: Mechanic, on: PlayCard, timestamp: Long, duration: Duration) = EngineRecord(
        "effect:$id", EngineRecordKind.Effect, timestamp, Json.encodeToString(EffectObject.serializer(), EffectObject(
            EffectId(id), listOf(mechanic), Affected.Objects(Objects.Self), locked = listOf(ObjectId.Card(CardId(on.id), 0)),
            source = ObjectId.Card(CardId(source.id), 0), controller = PlayerId(f.a), timestamp = timestamp, createdTurn = 4,
            duration = duration)))

    /** Alice's Meadow Sentinel (1/3) with a +1/+1 counter and +2/+2 until end of turn; Bob's Lantern Guide (2/3)
     * with −1/−1 and flying, both from a card in Alice's graveyard. */
    private fun board(on: Boolean): PlayState = f.initial.copy(definitions = f.initial.definitions + surge,
        board = f.initial.board.copy(automatum = on,
        cards = f.initial.board.cards.map { when (it.id) {
            source.id -> it.copy(definition = surge.digest, faceId = "surge")
            sentinel.id -> it.copy(timestamp = 1, typedCounters = listOf(TypedCounter("+1/+1", 1, 2)))
            guide.id -> it.copy(timestamp = 3)
            else -> it
        } },
        engineRecords = if (!on) emptyList() else listOf(
            effect("pump", Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)), sentinel, 4, Duration.EndOfTurn),
            effect("shrink", Mechanic.ModifyPowerToughness(Value.Fixed(-1), Value.Fixed(-1)), guide, 5, Duration.Indefinitely),
            effect("wings", Mechanic.GainAbilities(listOf(Keyword.Flying)), guide, 6, Duration.Indefinitely))))

    private fun render(play: PlayState): Pair<TableToolkitController, MutableList<PlayAction>> {
        // Worked out before the first frame, as the worker would have by then
        AutomatumBoards.compute(play, f.a)
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                    }
                }
            }
        }
        compose.waitForIdle()
        return toolkit to sent
    }

    // TRN-054, SYS-055: off, the switch is a dim outlined rune; a tap asks first (I-D45), then turns Automatum on for
    // the whole game
    @Test fun switchOffTurnsAutomatumOn() {
        val (_, sent) = render(board(false))
        val switch = compose.onNodeWithContentDescription("Automatum off. Turn Automatum on")
        switch.captureShot("$SCREENSHOTS/automatum/switch-off.png",
            "The Automatum switch in the top bar while Automatum is off: a dim outlined rune.")
        switch.performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), sent, "nothing is sent before the confirmation")
        compose.onNodeWithText("Turn Automatum on?").assertExists()
        captureShot("$SCREENSHOTS/automatum/switch-ask-on.png",
            "Tapping the Automatum switch while it is off asks first: \"Turn Automatum on?\" with what it means, Cancel and Turn on.")
        compose.onNodeWithText("Turn on").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetAutomatum(true))), sent)
    }

    // TRN-054 (I-D45): Cancel in the switch's confirmation does nothing
    @Test fun switchConfirmationCancelSendsNothing() {
        val (toolkit, sent) = render(board(true))
        compose.onNodeWithContentDescription("Automatum on. Turn Automatum off").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Turn Automatum off?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(null, toolkit.automatumAsk)
        assertEquals(emptyList(), sent)
    }

    // TRN-054, SYS-059 (I-D45): where turning on is refused there is no confirmation, only the refusal
    @Test fun refusedSwitchOnSkipsTheConfirmation() {
        val play = board(false).let { it.copy(board = it.board.copy(containers = it.board.containers.map { c ->
            if (c.zone == PlayZone.STACK) c.copy(cards = listOf(sentinel.id)) else c })) }
        assertTrue(Automatum.turnOnRefusal(play.board) != null)
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        toolkit.askAutomatum(true)
        assertEquals(null, toolkit.automatumAsk)
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetAutomatum(true))), sent)
    }

    // TRN-054 (I-D45): turning off during an unfinished Automatum step says that step is taken back first
    @Test fun switchOffConfirmationMentionsTheOpenStep() {
        assertEquals("Turn Automatum off?", automatumAskText(false, flowOpen = false, castOpen = false).title)
        assertTrue(automatumAskText(false, flowOpen = true, castOpen = true).text
            .endsWith("Automatum's unfinished step is taken back first. Your unfinished cast is dropped."))
    }

    // TRN-054, TBL-021, TBL-112, TBL-113: on, the switch is a violet-to-cyan sigil, and creatures show the engine's P/T
    @Test fun switchOnShowsComputedPowerToughness() {
        val play = board(true)
        val (_, sent) = render(play)
        compose.onNodeWithContentDescription("Automatum on. Turn Automatum off").captureShot("$SCREENSHOTS/automatum/switch-on.png",
            "The Automatum switch in the top bar while Automatum is on: the rune on a violet-to-cyan disc with its orbiting spark.")
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/computed-table.png",
            "With Automatum on, Alice's Meadow Sentinel shows a green ⌛4/6 (printed 1/3, a +1/+1 counter, +2/+2 until end of turn) and Bob's Lantern Guide a red 1/2 with a violet Flying chip.")
        val computed = AutomatumBoards.cached(play, f.a)!!
        assertEquals("4/6", computed.cards.getValue(sentinel.id).corner)
        assertEquals("1/2", computed.cards.getValue(guide.id).corner)
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false, computed = computed)
        val sentinelItem = scene.items.single { it.cardId == sentinel.id && it.zone == PlayZone.BATTLEFIELD }
        assertEquals("4/6", sentinelItem.corner)
        assertTrue(sentinelItem.cornerHourglass)
        assertEquals(RaisedPowerToughnessColor, sentinelItem.cornerColor)
        val guideItem = scene.items.single { it.cardId == guide.id && it.zone == PlayZone.BATTLEFIELD }
        assertEquals(LoweredPowerToughnessColor, guideItem.cornerColor)
        assertEquals(listOf("Flying"), guideItem.automatumMarks)
        compose.onNodeWithContentDescription("Automatum on. Turn Automatum off").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Turn off").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetAutomatum(false))), sent)
    }

    // TRN-055, SYS-063: holding the switch opens its panel: status, the own skip setting, clearing
    // the data and the engine's records
    @Test fun holdOpensThePanel() {
        val (toolkit, sent) = render(board(true))
        compose.onNodeWithContentDescription("Automatum on. Turn Automatum off").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("automatum", toolkit.panel)
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Automatum on · 1 ability is manual").assertExists()
        captureShot("$SCREENSHOTS/automatum/hold-panel.png",
            "Holding the Automatum switch opens its panel: the status line (the invented Wild Surge plays by hand), the own Skip when I have no choice switch, Clear Automatum data and the engine records.")
        compose.onNodeWithContentDescription("Skip when I have no choice").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(false))), sent)
        compose.onNodeWithText("Clear Automatum data").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.waitForIdle()
        assertEquals(PlayAction.Mtg(MtgAction.ClearRecords), sent.last())
    }

    // TRN-055: the Automatum panel is a dialog like Table actions: its ✕ closes it, and so do a tap outside and Back
    @Test fun holdPanelClosesOnOutsideTapAndClose() {
        val (toolkit, _) = render(board(true))
        val switch = compose.onNodeWithContentDescription("Automatum on. Turn Automatum off")
        switch.performTouchInput { longClick() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        assertEquals("automatum", toolkit.panel)
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(20f, height * .55f)) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(null, toolkit.panel, "a tap outside closes the panel")
        switch.performTouchInput { longClick() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        assertEquals("automatum", toolkit.panel)
        compose.onNodeWithContentDescription("Close").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(null, toolkit.panel, "its ✕ closes it")
    }

    // TBL-110 (I1): Inspect explains a computed P/T: printed, then every effect and counter in order
    @Test fun inspectExplainsComputedPowerToughness() {
        val (toolkit, _) = render(board(true))
        compose.runOnIdle { toolkit.inspect(sentinel.id) }
        compose.waitForIdle()
        compose.onNodeWithText("4/6: 1/3 printed, +1/+1 counters, +2/+2 Wild Surge until end of turn", substring = true).assertExists()
        captureShot("$SCREENSHOTS/automatum/inspect-breakdown.png",
            "Inspecting Alice's Meadow Sentinel with Automatum on lists where its 4/6 comes from: 1/3 printed, +1/+1 from counters, +2/+2 until end of turn.")
    }

    // TBL-114: while Automatum computes a card's P/T, its P/T controls are off and show the computed value
    @Test fun computedPowerToughnessIsNotEditable() {
        val play = board(true)
        val (toolkit, _) = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(sentinel.id, listOf(sentinel.id))) }
        compose.onNodeWithContentDescription("Inspect").assertIsDisplayed()
        // TBL-116: with Automatum on the P/T controls are in the usual menu behind "Manual…", greyed while Automatum
        // doesn't wait for something done by hand
        compose.onNodeWithContentDescription(CardRingPages.MANUAL).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Set corner").assertDoesNotExist()
        compose.onNodeWithContentDescription("Set EOT P/T").assertDoesNotExist()
    }
}
