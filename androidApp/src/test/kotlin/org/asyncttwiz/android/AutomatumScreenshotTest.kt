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
    private fun board(): PlayState = f.initial.copy(definitions = f.initial.definitions + surge,
        board = f.initial.board.copy(
        cards = f.initial.board.cards.map { when (it.id) {
            source.id -> it.copy(definition = surge.digest, faceId = "surge")
            sentinel.id -> it.copy(timestamp = 1, typedCounters = listOf(TypedCounter("+1/+1", 1, 2)))
            guide.id -> it.copy(timestamp = 3)
            else -> it
        } },
        engineRecords = listOf(
            effect("pump", Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)), sentinel, 4, Duration.EndOfTurn),
            effect("shrink", Mechanic.ModifyPowerToughness(Value.Fixed(-1), Value.Fixed(-1)), guide, 5, Duration.Indefinitely),
            effect("wings", Mechanic.GainAbilities(listOf(Keyword.Flying)), guide, 6, Duration.Indefinitely))))

    private fun render(play: PlayState): Pair<TableToolkitController, MutableList<PlayAction>> {
        // Worked out before the first frame, as the worker would have by then
        AutomatumBoards.compute(play, f.a)
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { TableBoard.of(play) }, { true }, { action, _ -> sent += action; true })
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, TableBoard.of(play), f.a, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, controlsConnected = true))
                    }
                }
            }
        }
        compose.waitForIdle()
        return toolkit to sent
    }

    // TBL-021, TBL-112, TBL-113: creatures show the engine's P/T
    @Test fun creaturesShowComputedPowerToughness() {
        val play = board()
        render(play)
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/computed-table.png",
            "With Automatum on, Alice's Meadow Sentinel shows a green ⌛4/6 (printed 1/3, a +1/+1 counter, +2/+2 until end of turn) and Bob's Lantern Guide a red 1/2 with a violet Flying chip.")
        val computed = AutomatumBoards.cached(play, f.a)!!
        assertEquals("4/6", computed.cards.getValue(sentinel.id).corner)
        assertEquals("1/2", computed.cards.getValue(guide.id).corner)
        val scene = TablePresentation.scene(f.game, TableBoard.of(play), f.a, 412f, 892f, false, computed = computed)
        val sentinelItem = scene.items.single { it.cardId == sentinel.id && it.zone == PlayZone.BATTLEFIELD }
        assertEquals("4/6", sentinelItem.corner)
        assertTrue(sentinelItem.cornerHourglass)
        assertEquals(RaisedPowerToughnessColor, sentinelItem.cornerColor)
        val guideItem = scene.items.single { it.cardId == guide.id && it.zone == PlayZone.BATTLEFIELD }
        assertEquals(LoweredPowerToughnessColor, guideItem.cornerColor)
        assertEquals(listOf("Flying"), guideItem.automatumMarks)
    }

    // TBL-110 (I1): Inspect explains a computed P/T: printed, then every effect and counter in order
    @Test fun inspectExplainsComputedPowerToughness() {
        val (toolkit, _) = render(board())
        compose.runOnIdle { toolkit.inspect(sentinel.id) }
        compose.waitForIdle()
        compose.onNodeWithText("4/6: 1/3 printed, +1/+1 counters, +2/+2 Wild Surge until end of turn", substring = true).assertExists()
        captureShot("$SCREENSHOTS/automatum/inspect-breakdown.png",
            "Inspecting Alice's Meadow Sentinel with Automatum on lists where its 4/6 comes from: 1/3 printed, +1/+1 from counters, +2/+2 until end of turn.")
    }

}
