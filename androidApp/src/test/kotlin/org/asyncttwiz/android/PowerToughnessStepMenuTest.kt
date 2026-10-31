package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PowerToughnessStepMenuTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val steps = listOf("+1/+0", "+0/+1", "−1/−0", "+0/−1")

    private fun render(corner: String) {
        val card = f.source(f.a, "1/3")
        val state = f.initial.copy(board = f.initial.board.copy(cards = f.initial.board.cards.map {
            if (it.id == card.id) it.copy(corner = corner) else it }))
        val toolkit = TableToolkitController(f.a, { state }, { true }, { _, _ -> true }, playBatch = { _, _ -> true })
        toolkit.openCard(TableScope(card.id, listOf(card.id)))
        compose.setContent {
            MaterialTheme { TableSurface(f.game, state, f.a, false, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle) }
        }
        compose.waitForIdle()
        compose.ringPage(3)
    }

    // TBL-083
    @Test fun stepsStayShownAndEnabledOnACardWithPowerToughness() {
        render("1/3")
        for (step in steps) {
            compose.onNodeWithContentDescription("P/T $step").assertIsEnabled()
            compose.onNodeWithContentDescription("EOT P/T $step").assertIsEnabled()
        }
    }

    // SYS-040, TBL-070, TBL-083
    @Test fun stepsStayShownButDisabledOnACardWithoutPowerToughness() {
        render("")
        for (step in steps) compose.onNodeWithContentDescription("P/T $step").assertIsNotEnabled()
    }
}
