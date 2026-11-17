package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.Avatar as AvatarIdentity
import org.asyncttwiz.storage.CatalogAvatar
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** LOB-149, LOB-160, LOB-161 (owner, 2026-11-14): the avatar choices name their cards, sit in a framed area that shows it scrolls, and use the
 * desktop's room. Invented card names; the art is the initial's placeholder. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AvatarChoicesTest {
    @get:Rule val compose = createComposeRule()
    private val names = listOf("Ashen Warden", "Briarthorn Matriarch", "Cindervale Oracle", "Duskmere Tyrant", "Emberlight Seer",
        "Frostgale Sovereign", "Gloomwick Archivist", "Hollowpeak Drakelord", "Ironbark Elder", "Jadefire Wanderer",
        "Kestrel of the Long Night", "Lumenspire Regent", "Mirefang Broodmother", "Nightbloom Siren")
    private fun choices(count: Int) = names.take(count).mapIndexed { i, name ->
        CatalogAvatar(name, AvatarIdentity("00000000-0000-4000-8000-0000000000${(10 + i)}")) }

    @Before fun noArt() { PlaceholderArtwork.install(RuntimeEnvironment.getApplication(), available = false) }

    // LOB-149: what the choices are, said above them
    @Test fun theLineAboveSaysWhatTheChoicesAre() {
        assertEquals("Random legendary creatures and planeswalkers", avatarChoicesTitle("", 6))
        assertEquals("Cards matching “dra”: 12", avatarChoicesTitle(" dra ", 12))
    }

    // LOB-149, LOB-160, LOB-013: search results on the phone's Edit profile: each named under its art, the framed area shaded at the
    // edge that hides more, the shade moving to the top once scrolled to the end; a tap chooses
    @Test @Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi") fun phoneSearchResultsAreNamedAndFramed() {
        var chosen: AvatarIdentity? = null
        val results = choices(14)
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit profile", style = MaterialTheme.typography.headlineSmall)
                AvatarChoices(results, results[1].avatar, "r", loading = false, error = false, compact = true, choose = { chosen = it },
                    modifier = Modifier.height(296.dp))
            }
        } } } }
        compose.onNodeWithText("Cards matching “r”: 14").assertIsDisplayed()
        compose.onNodeWithText("Briarthorn Matriarch").assertIsDisplayed()
        compose.onNodeWithTag("avatar-choices-more-below").assertExists()
        compose.onNodeWithTag("avatar-choices-more-above").assertDoesNotExist()
        captureShot("$SCREENSHOTS/settings/avatar-search-phone.png",
            "Edit profile on a phone after searching \"r\": 14 matching cards in a framed area, each named under its art (Briarthorn Matriarch chosen), the bottom edge shaded as more lie below.")
        compose.onNodeWithText("Ashen Warden").performClick()
        assertEquals(results[0].avatar, chosen)
        compose.onNodeWithTag("avatar-choices").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag("avatar-choices-more-above").assertExists()
    }

    // LOB-149, LOB-161: on the desktop the choices are larger and as many to a row as fit
    @Test @Config(sdk = [35], qualifiers = "w1440dp-h900dp-land-mdpi") fun desktopChoicesUseTheRoom() {
        val results = choices(14)
        compose.setContent { CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) { ScreenTheme { NoRipples {
            Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(Modifier.widthIn(max = 960.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Edit profile", style = MaterialTheme.typography.headlineSmall)
                    AvatarChoices(results, null, "r", loading = false, error = false, compact = false, choose = {},
                        modifier = Modifier.height(560.dp))
                }
            } }
        } } } }
        // Seven to a row: the 14 results take two rows, nothing hidden
        compose.onNodeWithText("Nightbloom Siren").assertIsDisplayed()
        compose.onNodeWithTag("avatar-choices-more-below").assertDoesNotExist()
        val first = compose.onNodeWithText("Ashen Warden").fetchSemanticsNode().boundsInRoot
        val seventh = compose.onNodeWithText("Gloomwick Archivist").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, seventh.top, "a row of seven")
        captureShot("$SCREENSHOTS/desktop/avatar-search.png",
            "Edit profile in the desktop layout after searching \"r\": the 14 matching cards in two rows of seven larger choices, each named under its art.")
    }
}
