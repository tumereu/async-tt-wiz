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

/** CMB-029 (owner playtest 2026-11-06): the damage preview's one badge per creature, at the sizes a card's zoom gives it,
 * drawn at a phone's density so the pictures show how readable the numbers are. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xxhdpi")
class DamageBadgeTest {
    @get:Rule val compose = createComposeRule()

    // CMB-029: a red splash with the number when the creature survives, a black skull with the number inside when it
    // dies, the same at every size; the badge keeps its size for two or three digits, the number drawn smaller to fit
    // and centred in the splash or the skull's cranium (owner gallery review 2026-11-07)
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
                        DamageBadge(120, dying = false, size = size)
                    }
                }
            }
        }
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 3 damage").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 2 damage and die").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would die").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 12 damage").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 10 damage and die").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithContentDescription("Would take 120 damage").fetchSemanticsNodes().size)
        // Every badge is as wide as it is tall, whatever its number
        val px = with(compose.density) { listOf(12, 18, 26).map { it.dp.roundToPx() } }
        for (description in listOf("Would take 3 damage", "Would take 12 damage", "Would take 120 damage", "Would die",
            "Would take 2 damage and die", "Would take 10 damage and die")) {
            val sizes = compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().map { it.size }
            assertEquals(px, sizes.map { it.height })
            assertEquals(sizes.map { it.height }, sizes.map { it.width })
        }
        compose.onRoot().captureShot("$SCREENSHOTS/combat-band/damage-badges.png",
            "The damage preview's badges at three sizes, smallest first: a red splash with 3 for a creature that survives, a black skull with 2 in its cranium for one that dies, a plain skull for one that dies without damage, then a splash with 12, a skull with 10 and a splash with 120, every badge the same size with the longer numbers drawn smaller to fit.")
    }

    // CMB-029 (owner gallery review 2026-11-07): two-digit damage on cards as large as the table's: the badge keeps its
    // size over the card's middle and its number fits inside, centred
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
            "Damage preview badges on four combat cards at their largest: a red splash with 12, a skull with 10 in its cranium, a splash with 7 and a skull with 20, every badge the same size with each number fitting inside, centred.")
    }
}
