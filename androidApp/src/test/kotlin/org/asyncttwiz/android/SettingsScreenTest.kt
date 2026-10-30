package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.CompletableDeferred
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogMetadata
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    @After fun clear() = models.clear()

    private class FakeSettings(var cachedValue: AccountSettings = AccountSettings(), var server: AccountSettings = AccountSettings()) : AccountSettingsSource {
        val writes = mutableListOf<AccountSettings>()
        var fail = false
        var refreshGate: CompletableDeferred<Unit>? = null
        override suspend fun cached() = cachedValue
        override suspend fun refresh(): AccountSettings { refreshGate?.await(); return server }
        override suspend fun set(settings: AccountSettings) {
            writes += settings; cachedValue = settings
            if (fail) throw AccountFailure("network")
            server = settings
        }
    }

    private fun selected(option: DeckVisibility) = compose.onNodeWithTag("deck-visibility-${option.name}")

    @Test fun deckVisibilityShowsCachedThenServerValueAndSavesTheChoice() {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSettings(AccountSettings(DeckVisibility.HIDDEN), AccountSettings(DeckVisibility.VISIBLE)).apply { refreshGate = gate }
        compose.setContent { ScreenTheme { SettingsContent(null, "deck-visibility", {}, null, source) } }
        compose.onNodeWithText("Games").assertIsDisplayed()
        compose.onNodeWithText("Deck visibility").assertIsDisplayed()
        selected(DeckVisibility.HIDDEN).assertIsSelected()
        gate.complete(Unit)
        compose.waitUntil { compose.onAllNodes(hasTestTag("deck-visibility-VISIBLE") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        selected(DeckVisibility.HIDDEN).assertIsNotSelected()
        selected(DeckVisibility.AFTER_FIRST_GAME).assertHasClickAction().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(AccountSettings(DeckVisibility.AFTER_FIRST_GAME)), source.writes) }
        selected(DeckVisibility.VISIBLE).assertIsNotSelected()
        compose.onNodeWithText("Hidden until you've finished a game with that deck against that opponent").assertIsDisplayed()
    }

    @Test fun deckVisibilityDefaultsToShowAfterFirstGameAndAFailedSaveKeepsTheChoiceWithAMessage() {
        val source = FakeSettings().apply { fail = true }
        compose.setContent { ScreenTheme { SettingsContent(null, "deck-visibility-fail", {}, null, source) } }
        selected(DeckVisibility.AFTER_FIRST_GAME).assertIsSelected()
        selected(DeckVisibility.HIDDEN).performClick().assertIsSelected()
        compose.onNodeWithText("Couldn't save deck visibility", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(AccountSettings(DeckVisibility.HIDDEN), source.cachedValue) }
    }

    @Test fun withoutAnAccountTheGamesSectionIsHiddenAndOtherSectionsKeepTheirOrder() {
        compose.setContent { ScreenTheme { SettingsContent(null, "no-account") {} } }
        compose.onNodeWithText("Games").assertDoesNotExist()
        val order = listOf("Table", "Timeline playback", "About & account").map {
            compose.onNodeWithText(it).getUnclippedBoundsInRoot().top
        }
        assertEquals(order.sorted(), order)
    }

    private fun capture(name: String) {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also {
            models.put("catalog", it)
            it.seed(CatalogUiState(installed = CatalogMetadata("2026-10-26", "1", records = 124_555), loaded = true))
        }
        val source = FakeSettings()
        compose.setContent { ScreenTheme { SettingsContent(catalog, "shots", {}, fixtures.auth, source) } }
        compose.waitForIdle()
        val directory = File("build/outputs/roborazzi/settings").also { it.mkdirs() }
        captureScreenRoboImage(File(directory, "$name-top.png").path)
        for (tag in listOf("cards-settings", "playback-settings", "catalog-controls", "logout")) {
            compose.onNodeWithTag(tag).performScrollTo()
            compose.waitForIdle()
            captureScreenRoboImage(File(directory, "$name-$tag.png").path)
        }
    }

    @Test @Config(qualifiers = "w412dp-h2600dp-port-xhdpi")
    fun fullPagePortraitScreenshot() = capture("settings-portrait-full")

    @Test @Config(qualifiers = "w892dp-h412dp-land-xhdpi")
    fun landscapeScreenshots() = capture("settings-landscape")
}
