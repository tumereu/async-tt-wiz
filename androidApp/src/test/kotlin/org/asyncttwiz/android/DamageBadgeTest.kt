package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** CMB-029 (owner playtest 2026-11-06): the damage preview's one badge per creature, at the sizes a card's zoom gives it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class DamageBadgeTest {
    @get:Rule val compose = createComposeRule()

    // CMB-029: a red splash with the number when the creature survives, a black skull with the number inside when it
    // dies, the same at every size
    @Test fun aSplashWhenItSurvivesASkullWhenItDies() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.background(Color(0xffd7e4dc)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (size in listOf(12f, 18f, 26f)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        DamageBadge(3, dying = false, size = size)
                        DamageBadge(2, dying = true, size = size)
                        DamageBadge(null, dying = true, size = size)
                    }
                }
            }
        }
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 3 damage").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 2 damage and die").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would die").fetchSemanticsNodes().size)
        compose.onRoot().captureShot("$SCREENSHOTS/combat-band/damage-badges.png",
            "The damage preview's badges at three sizes, smallest first: a red splash with 3 for a creature that survives, a black skull with 2 inside for one that dies, and a plain skull for one that dies without damage.")
    }
}
