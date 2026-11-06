package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.test.assertTrue

/** CMB-029 (owner playtest 2026-11-06): the damage preview's one badge per creature, at the sizes a card's zoom gives it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class DamageBadgeTest {
    @get:Rule val compose = createComposeRule()

    // CMB-029: a red splash with the number when the creature survives, a black skull with the number inside when it
    // dies, the same at every size; two digits widen the badge (owner gallery review 2026-11-06)
    @Test fun aSplashWhenItSurvivesASkullWhenItDies() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.background(Color(0xffd7e4dc)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (size in listOf(12f, 18f, 26f)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        DamageBadge(3, dying = false, size = size)
                        DamageBadge(2, dying = true, size = size)
                        DamageBadge(null, dying = true, size = size)
                        DamageBadge(12, dying = false, size = size)
                        DamageBadge(10, dying = true, size = size)
                    }
                }
            }
        }
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 3 damage").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 2 damage and die").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would die").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 12 damage").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 10 damage and die").fetchSemanticsNodes().size)
        // A badge is round for one digit and wider for two
        assertEquals(26f, damageBadgeWidth("3", 26f))
        assertTrue(damageBadgeWidth("12", 26f) > 26f * 1.3f)
        compose.onRoot().captureShot("$SCREENSHOTS/combat-band/damage-badges.png",
            "The damage preview's badges at three sizes, smallest first: a red splash with 3 for a creature that survives, a black skull with 2 inside for one that dies, a plain skull for one that dies without damage, then a wider splash with 12 and a wider skull with 10 inside, each number centred.")
    }

    // CMB-029 (owner gallery review 2026-11-06): two-digit damage on cards as large as the table's: the badge widens over
    // the card's middle and its number stays centred
    @Test fun twoDigitDamageOnCards() {
        compose.setContent {
            MaterialTheme {
                Row(Modifier.background(Color(0xfff5f1df)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for ((dealt, dying) in listOf(12 to false, 10 to true, 7 to false, 20 to true)) Box(Modifier.size(44.dp, 62.dp)
                        .background(Color.White, RoundedCornerShape(3.dp)).border(1.5.dp, Color(0xff2e7d5b), RoundedCornerShape(3.dp))) {
                        DamageBadge(dealt, dying, 26f, Modifier.align(Alignment.Center).wrapContentSize(unbounded = true))
                    }
                }
            }
        }
        assertEquals(1, compose.onAllNodesWithContentDescription("Would take 12 damage").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithContentDescription("Would take 20 damage and die").fetchSemanticsNodes().size)
        compose.onRoot().captureShot("$SCREENSHOTS/combat-band/damage-badges-two-digits.png",
            "Damage preview badges on four combat cards at their largest: a wide red splash with 12, a wide skull with 10 inside, a round splash with 7 and a wide skull with 20, each number centred in its badge.")
    }
}
