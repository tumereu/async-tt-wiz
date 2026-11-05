package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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

    // TBL-124: a long ability's chip wraps its whole effect onto more rows (none of it cut), its cost in symbols beside it
    @OptIn(ExperimentalLayoutApi::class)
    @Test fun aLongAbilityChipWraps() {
        compose.setContent {
            MaterialTheme {
                FlowRow(Modifier.testTag("chips").width(412.dp).background(Color(0xffe6ece9)).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AbilityChip("{T}: Add {G}.", {}, {})
                    AbilityChip(long, {}, {})
                    AbilityChip("{1}{G/P}, {S}: Untap target land.", {}, {})
                }
            }
        }
        val effect = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle"
        compose.onNodeWithText(effect).assertExists()
        assertTrue(lines(effect) >= 3, "the long effect wraps onto more rows: ${lines(effect)}")
        compose.onNodeWithText("{2}{G}, {T}, Sacrifice this artifact").assertExists()
        compose.onNodeWithTag("chips").captureShot("$SCREENSHOTS/chips/ability-chips-wrapped.png",
            "A forest card's three ability chips: \"Add\" with the tap and G symbols, a long search ability wrapped onto several rows with its cost ({2}{G}, {T}, sacrifice) drawn with symbols beside it, and an untap ability with Phyrexian and snow symbols.")
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

    // ZON-080 (batch B 2026-11-06): abilities used from the hand are chips after the casts, as plot and foretell are: Cycling
    // that can be paid now, Channel greyed
    @OptIn(ExperimentalLayoutApi::class)
    @Test fun handAbilitiesAreChips() {
        val card = ObjectId.Card(CardId("c"), 0)
        val cast = Input.Cast(PlayerId("a"), card)
        fun ability(index: Int) = Input.Activate(PlayerId("a"), card, arcanum.engine.view.AbilityOrigin.Printed(0, index))
        compose.setContent {
            MaterialTheme {
                FlowRow(Modifier.testTag("chips").width(412.dp).background(Color(0xffe6ece9)).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WayChip(CastWayChip(cast, "Cast", "{3}{R}", null, false), true, {}, {})
                    WayChip(CastWayChip(ability(1), "Cycling", "{1}", "Cycling {1}", true), false, {}, {})
                    WayChip(CastWayChip(ability(2), "Channel", "{2}{R}", "Channel — {2}{R}, Discard this card: Draw two cards.", false), false, {}, {})
                }
            }
        }
        compose.onNodeWithText("Cycling").assertExists()
        compose.onNodeWithTag("chips").captureShot("$SCREENSHOTS/chips/hand-ability-chips.png",
            "A raised hand card's chips: Cast {3}{R} chosen but greyed (not enough mana), then Cycling {1}, which can be paid now, and Channel {2}{R}, greyed.")
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
            "Three card menu rows whose ability texts draw their mana and tap symbols as symbols: tap for G, an X damage ability with a red-green hybrid, and a greyed one with a blue Phyrexian and the untap symbol.")
    }
}
