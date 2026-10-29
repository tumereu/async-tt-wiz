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
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CombatDamageMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun experimentalDamageEntriesAreReachableInTheOrdinaryProjectionMenu() {
        val f = TableStatesFixture
        val state = f.combat()
        val views = listOf(f.id("attacker1"), f.id("attacker2"))
        val requests = mutableListOf<List<PlayAction>>()
        val toolkit = TableToolkitController(f.a, { state }, { true }, { action, _ -> requests += listOf(action); true },
            playBatch = { actions, _ -> requests += actions; true })
        toolkit.openCard(TableScope(views.first(), views))
        compose.setContent {
            MaterialTheme { TableSurface(f.game, state, f.a, false, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle) }
        }
        compose.onNodeWithContentDescription("Deal to target").assertIsDisplayed()
        compose.onNodeWithContentDescription("Deal to target or creatures").assertIsDisplayed()
        compose.ringClick("Deal to target or creatures")
        assertEquals(listOf(CombatDamage.assign(state, views).actions), requests)
        assertNull(toolkit.capture)
    }
}
