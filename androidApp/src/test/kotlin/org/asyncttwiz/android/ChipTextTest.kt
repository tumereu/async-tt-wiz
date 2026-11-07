package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.CastWayChip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening: a battlefield card's ability chips (TBL-124) and a hand card's way chips (ZON-080)
 * wrap long texts onto more rows instead of cutting them, and mana and tap symbols are drawn as symbols wherever text
 * shows them (the chips, a card menu's rows).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ChipTextTest {
    @get:Rule val compose = createComposeRule()

    /** The number of lines the text node [text] is laid out on; it fails when any of the text is cut off. */
    private fun lines(text: String): Int {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!(results)
        val layout = results.single()
        assertTrue(!layout.hasVisualOverflow, "\"$text\" is cut off")
        return layout.lineCount
    }

    private val long = "{2}{G}, {T}, Sacrifice this artifact: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle."

    // TBL-124: an ability's chip reads as on the card, cost first in symbols ({T} a turning arrow), then the effect; a long
    // effect wraps onto more rows (none of it cut); the chips take the fewest rows (owner gallery review 2026-11-06): in
    // card order they would take three, so the third chip moves up beside the first
    @Test fun aLongAbilityChipWraps() {
        val untap = "{1}{G/P}, {S}: Untap target land."
        val pay = "{T}, Pay 1 life: Add {G}."
        val add = "{T}: Add {G}."
        compose.setContent {
            MaterialTheme {
                ChipRowsLayout(4.dp, Modifier.testTag("chips").width(412.dp).background(Color(0xffe6ece9)).padding(8.dp)) {
                    AbilityChip(untap, {}, {})
                    AbilityChip(long, {}, {})
                    AbilityChip(pay, {}, {})
                    AbilityChip(add, {}, {})
                }
            }
        }
        val effect = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle"
        compose.onNodeWithText(effect).assertExists()
        assertTrue(lines(effect) >= 3, "the long effect wraps onto more rows: ${lines(effect)}")
        val cost = compose.onNodeWithText("{2}{G}, {T}, Sacrifice this artifact:").assertExists().getUnclippedBoundsInRoot()
        assertTrue(cost.left < compose.onNodeWithText(effect).getUnclippedBoundsInRoot().left, "the cost comes first")
        compose.onNodeWithText("{T}:").assertExists()
        val chips = listOf(untap, long, pay, add).map { compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot() }
        fun sameRow(a: Int, b: Int) = chips[a].top < chips[b].bottom && chips[b].top < chips[a].bottom
        assertTrue(sameRow(0, 2) && sameRow(1, 3) && !sameRow(0, 1), "two rows: the first and third chips, then the second and fourth: $chips")
        assertTrue(chips[0].right < chips[2].left && chips[1].right < chips[3].left, "card order within each row")
        compose.onNodeWithTag("chips").captureShot("$SCREENSHOTS/chips/ability-chips-wrapped.png",
            "A land's four ability chips in the fewest rows, each cost first as on the card: an untap ability with Phyrexian and snow symbols (the untap symbol purple) and, moved up beside it, \"{T}, Pay 1 life: Add\" G; then a long search ability whose cost ({2}{G}, tap arrow, sacrifice) comes before its effect wrapped onto several rows, and the tap arrow then \"Add\" G.")
    }

    // ZON-080: a long way name wraps too
    @OptIn(ExperimentalLayoutApi::class)
    @Test fun aLongWayChipWraps() {
        val cast = Input.Cast(PlayerId("a"), ObjectId.Card(CardId("c"), 0))
        val name = "Seasoned Pyromancer's Second Face · With permission"
        compose.setContent {
            MaterialTheme {
                FlowRow(Modifier.width(412.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WayChip(CastWayChip(cast, "Cast", "{R}", null, true), true, {}, {})
                    WayChip(CastWayChip(cast, name, "{3}{R}{R}", null, false), false, {}, {})
                }
            }
        }
        compose.onNodeWithText(name).assertExists()
        assertTrue(lines(name) >= 2, "the long way's name wraps: ${lines(name)}")
        assertTrue(lines("Cast") == 1)
    }

    // ZON-080 (batch B 2026-11-06; owner 2026-11-07: no chosen chip, a tap uses it): abilities used from the hand are chips
    // after the casts, as plot and foretell are: Cycling that can be paid now, Channel greyed
    @OptIn(ExperimentalLayoutApi::class)
    @Test fun handAbilitiesAreChips() {
        val card = ObjectId.Card(CardId("c"), 0)
        val cast = Input.Cast(PlayerId("a"), card)
        fun ability(index: Int) = Input.Activate(PlayerId("a"), card, arcanum.engine.view.AbilityOrigin.Printed(0, index))
        compose.setContent {
            MaterialTheme {
                FlowRow(Modifier.testTag("chips").width(412.dp).background(Color(0xffe6ece9)).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WayChip(CastWayChip(cast, "Cast", "{3}{R}", null, false), false, {}, {})
                    WayChip(CastWayChip(ability(1), "Cycling", "{1}", "Cycling {1}", true), false, {}, {})
                    WayChip(CastWayChip(ability(2), "Channel", "{2}{R}", "Channel — {2}{R}, Discard this card: Draw two cards.", false), false, {}, {})
                    WayChip(CastWayChip(ability(3), "Activate", "{1}", "{1}, Discard this card: You gain 2 life.", true), false, {}, {})
                }
            }
        }
        compose.onNodeWithText("Cycling").assertExists()
        // ZON-080: a plain "cost: effect" ability reads as on the card, cost first
        compose.onNodeWithText("{1}, Discard this card:").assertExists()
        compose.onNodeWithText("You gain 2 life").assertExists()
        compose.onNodeWithTag("chips").captureShot("$SCREENSHOTS/chips/hand-ability-chips.png",
            "A raised hand card's chips, each one way to cast or use it: Cast {3}{R} greyed (not enough mana), then Cycling {1}, which can be paid now, Channel {2}{R}, greyed, and a plain ability reading as on the card: \"{1}, Discard this card:\" then \"You gain 2 life\".")
    }

    // TBL-126, TBL-123: a card menu's rows draw the abilities' symbols; a greyed row too
    @Test fun menuRowsDrawSymbols() {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Column(Modifier.testTag("rows").background(Color.White).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EngineActionRow(EngineRow(MenuControl("{T}: Add {G}.") {}, Icons.Outlined.Bolt), AutomatumViolet)
                        EngineActionRow(EngineRow(MenuControl("{X}{R/G}, {T}: This artifact deals X damage to any target.") {}, Icons.Outlined.Bolt),
                            AutomatumViolet)
                        EngineActionRow(EngineRow(MenuControl("{2}{U/P}, {Q}: Draw a card.", enabled = false) {}, Icons.Outlined.Bolt),
                            AutomatumViolet)
                    }
                }
            }
        }
        compose.onNodeWithText("{T}: Add {G}.").assertExists()
        compose.onNodeWithTag("rows").captureShot("$SCREENSHOTS/chips/menu-rows-symbols.png",
            "Three card menu rows whose ability texts draw their mana and tap symbols as symbols: the tap arrow for G, an X damage ability with a red-green hybrid and the tap arrow, and a greyed one with a blue Phyrexian and the untap symbol, the arrow mirrored.")
    }

    // TBL-124: {T} is drawn as a turning arrow, as printed, and {Q} as the same arrow mirrored on a purple badge (owner
    // gallery review 2026-11-06); the letters in the badges are optically centred
    @Test fun tapAndUntapSymbolsAreArrows() {
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column(Modifier.testTag("symbols").background(Color.White).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (line in listOf(listOf("T", "Q", "G", "2"), listOf("W", "U", "B", "R", "C", "X"), listOf("10", "W/U", "G/P", "S", "E")))
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (token in line) androidx.compose.foundation.layout.Box(Modifier.size(48.dp)) { CardSymbol(token) }
                        }
                }
            }
        }
        compose.onNodeWithTag("symbols").captureShot("$SCREENSHOTS/chips/tap-symbols.png",
            "The tap symbol drawn large as a clockwise turning arrow, the untap symbol as the same arrow mirrored on a purple badge, then the G and 2 badges; below, the W, U, B, R, C and X badges, then 10, a white-blue hybrid, green Phyrexian, snow and energy, each letter centred in its badge.")
    }
}
