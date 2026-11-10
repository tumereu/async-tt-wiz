package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
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

    // LOB-080
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

    // LOB-080
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

    private fun capture(name: String, view: String) {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also {
            models.put("catalog", it)
            it.seed(CatalogUiState(installed = CatalogMetadata("2026-10-26", "1", records = 124_555), ready = true))
        }
        val source = FakeSettings()
        compose.setContent { ScreenTheme { SettingsContent(catalog, "shots", {}, fixtures.auth, source,
            // Fixed so the screenshots don't change with every commit.
            versionText = appVersionText("0.0.0", 0, "debug", "0000000")) } }
        compose.waitForIdle()
        val directory = File("$SCREENSHOTS/settings").also { it.mkdirs() }
        captureShot(File(directory, "$name-top.png").path,
            "Settings screen $view for a signed-in player with the card catalog installed, at the top of the page.")
        for ((tag, part) in listOf("cards-settings" to "card size settings", "playback-settings" to "timeline playback settings",
                "automatum-settings" to "Automatum settings", "catalog-controls" to "card catalog controls", "logout" to "Log out button")) {
            compose.onNodeWithTag(tag).performScrollTo()
            compose.waitForIdle()
            captureShot(File(directory, "$name-$tag.png").path,
                "Settings screen $view for a signed-in player, scrolled to bring the $part into view.")
        }
    }

    // LOB-113 (I-D56 §7, §13; owner 2026-11-09): new games start in Quick with Assume resolves When they have no mana and Autotap on; without an
    // account (signed out) the device keeps them per account
    @Test fun automatumPlayDefaults() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { ScreenTheme { AutomatumPauseSettings("pauses") } }
        compose.onNodeWithTag("response-mode-Quick").assertIsSelected()
        compose.onNodeWithTag("assume-resolves-no-mana").assertIsSelected()
        compose.onNodeWithTag("autotap").assertIsOn()
        compose.onNodeWithTag("response-mode-Control").performClick()
        compose.onNodeWithTag("assume-resolves-always").performClick()
        compose.onNodeWithTag("autotap").performClick()
        compose.waitForIdle()
        assertEquals(org.asyncttwiz.protocol.game.PlaySettings(org.asyncttwiz.protocol.game.ResponseMode.FullControl,
            assume = org.asyncttwiz.protocol.game.AssumeResolves.Always, autotap = false), PausePreferences(context, "pauses").engineDefaults)
        assertEquals(org.asyncttwiz.protocol.game.PlaySettings.NEW_GAMES, PausePreferences(context, "someone else").engineDefaults)
        compose.onNodeWithTag("response-mode-Control").assertIsSelected()
        compose.onNodeWithTag("autotap").assertIsOff()
        // "When they have no mana" is the third choice, Never the first; one choice at a time
        compose.onNodeWithTag("assume-resolves-no-mana").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("assume-resolves-always").assertIsNotSelected()
        assertEquals(org.asyncttwiz.protocol.game.AssumeResolves.WhenNoMana, PausePreferences(context, "pauses").engineDefaults.assume)
        compose.onNodeWithTag("assume-resolves-never").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("assume-resolves-no-mana").assertIsNotSelected()
        assertEquals(org.asyncttwiz.protocol.game.AssumeResolves.Never, PausePreferences(context, "pauses").engineDefaults.assume)
    }

    // LOB-079, LOB-113
    @Test @Config(qualifiers = "w412dp-h2600dp-port-xhdpi")
    fun fullPagePortraitScreenshot() = capture("settings-portrait-full", "on a very tall portrait screen")

    // LOB-079
    @Test @Config(qualifiers = "w892dp-h412dp-land-xhdpi")
    fun landscapeScreenshots() = capture("settings-landscape", "in landscape")
}
