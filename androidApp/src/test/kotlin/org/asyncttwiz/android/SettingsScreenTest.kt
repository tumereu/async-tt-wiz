package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.asyncttwiz.appui.AppPlatform

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

    // LOB-146, TRN-075 (owner, 2026-11-11): the "Your move" sound is Plip by default; a choice saves to the account at once;
    // each sound's play button plays it (Off has none)
    @Test fun yourMoveSoundDefaultsToPlipSavesTheChoiceAndPlaysOnListen() {
        val source = FakeSettings()
        compose.setContent { ScreenTheme { SettingsContent(null, "move-sound", {}, null, source) } }
        compose.onNodeWithTag("move-sound").performScrollTo()
        compose.onNodeWithText("Your move sound").assertIsDisplayed()
        compose.onNodeWithTag("move-sound-plip").assertIsSelected()
        compose.onNodeWithContentDescription("Listen to Off").assertDoesNotExist()
        val directory = File("$SCREENSHOTS/settings").also { it.mkdirs() }
        compose.onNodeWithTag("move-sound").captureShot(File(directory, "move-sound.png").path,
            "Settings → Games → Your move sound for a new account: Off and four sounds of the app's own, Plip chosen (the default), each sound with a play button to listen to it.")
        compose.onNodeWithTag("move-sound-drop").performClick().assertIsSelected()
        compose.onNodeWithTag("move-sound-plip").assertIsNotSelected()
        compose.runOnIdle { assertEquals(listOf(org.asyncttwiz.protocol.MoveSound.Drop), source.writes.map { it.moveSound }) }
        compose.onNodeWithContentDescription("Listen to Chime").performClick()
        compose.runOnIdle { assertTrue("Your move sound: chime (Settings: listening)" in AppLog.text(), "the play button plays it") }
        compose.onNodeWithTag("move-sound-drop").assertIsSelected()
        // The app's own sounds, made in code: short and soft
        for (sound in org.asyncttwiz.protocol.MoveSound.all - org.asyncttwiz.protocol.MoveSound.Off) {
            val pcm = assertNotNull(MoveSounds.pcm(sound))
            assertTrue(pcm.size in 1..MoveSounds.SAMPLE_RATE / 2 && pcm.maxOf { kotlin.math.abs(it.toInt()) } < Short.MAX_VALUE / 2, sound.id)
        }
        assertNull(MoveSounds.pcm(org.asyncttwiz.protocol.MoveSound.Off))
    }

    // TRN-167, TRN-075 (owner, 2026-11-20): "Vibrate on your move" is its own switch under the Your move sound, on by default
    // and kept on the device; switched off, the Your move cue doesn't vibrate (the sound is a setting of its own)
    @Test fun vibrateOnYourMoveIsItsOwnSwitchOnByDefault() {
        MoveVibrationPreferences(AppPlatform.current).enabled = true
        val source = FakeSettings(AccountSettings(moveSound = org.asyncttwiz.protocol.MoveSound.Off))
        var cue: ((String) -> Unit)? = null
        compose.setContent { ScreenTheme {
            cue = org.asyncttwiz.appui.rememberMoveVibration()
            SettingsContent(null, "move-vibration", {}, null, source)
        } }
        compose.onNodeWithTag("move-vibration").performScrollTo().assertIsOn()
        compose.onNodeWithText("Vibrate on your move").assertIsDisplayed()
        // Below the Your move sound, with the sound Off: the two are separate
        assertTrue(compose.onNodeWithTag("move-vibration").getUnclippedBoundsInRoot().top >
            compose.onNodeWithTag("move-sound").getUnclippedBoundsInRoot().top)
        compose.runOnIdle { cue!!("the test's move") }
        assertTrue("Your move vibration: 70 ms (the test's move)" in AppLog.text(), "on: it vibrates with the sound Off")
        compose.onNodeWithTag("move-vibration").performClick().assertIsOff()
        assertFalse(MoveVibrationPreferences(AppPlatform.current).enabled, "kept on the device")
        compose.runOnIdle { cue!!("the next move") }
        assertTrue("Your move vibration: off in Settings (the next move)" in AppLog.text())
        assertFalse("Your move vibration: 70 ms (the next move)" in AppLog.text())
        compose.onNodeWithTag("move-vibration").performClick().assertIsOn()
        compose.runOnIdle { assertTrue(MoveVibrationPreferences(AppPlatform.current).enabled) }
    }

    // LOB-276, LOB-278, LOB-079 (owner, 2026-11-26): Settings → Notifications, after Games: a switch per kind with what it covers,
    // each on by default and saved to the account at once; on Android "Sound and vibration" under them opens Android's
    // notification settings for the app
    @Test fun notificationsHaveASwitchPerKindKeptOnTheAccountAndOpenAndroidsSettings() {
        val source = FakeSettings(server = AccountSettings(notifications = NotificationSettings(draft = false)))
        compose.setContent { ScreenTheme { SettingsContent(null, "notifications", {}, null, source) } }
        compose.waitUntil { compose.onAllNodes(hasTestTag("notification-kind-draft") and isOff()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onNodeWithText("Notifications").getUnclippedBoundsInRoot().top >
            compose.onNodeWithText("Games").getUnclippedBoundsInRoot().top, "after Games")
        for (kind in NotificationKind.all) {
            compose.onNodeWithTag("notification-kind-${kind.id}").performScrollTo()
            if (kind == NotificationKind.Draft) compose.onNodeWithTag("notification-kind-${kind.id}").assertIsOff()
            else compose.onNodeWithTag("notification-kind-${kind.id}").assertIsOn()
            compose.onNodeWithText(kind.label).assertIsDisplayed(); compose.onNodeWithText(kind.explanation).assertIsDisplayed()
        }
        assertEquals(listOf("Your move", "Reminders", "Chat", "Invitations", "Draft", "Game over & warnings"),
            NotificationKind.all.map { it.label })
        compose.onNodeWithTag("notification-sound").performScrollTo()
        val directory = File("$SCREENSHOTS/settings").also { it.mkdirs() }
        captureShot(File(directory, "notifications.png").path,
            "Settings → Notifications on Android, the account's Draft switched off on another device: a switch per kind with " +
                "what it covers (choice: these lines), then Sound and vibration.")
        compose.onNodeWithTag("notification-kind-chat").performClick().assertIsOff()
        compose.runOnIdle { assertEquals(NotificationSettings(chat = false, draft = false), source.writes.last().notifications) }
        compose.onNodeWithTag("notification-kind-chat").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(NotificationSettings(draft = false), source.writes.last().notifications) }
        // LOB-278: Android's notification settings for the app
        compose.onNodeWithTag("notification-sound").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("Sound and vibration").assertIsDisplayed()
        compose.onNodeWithTag("notification-sound").performClick()
        val opened = org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened?.action)
        assertEquals(ApplicationProvider.getApplicationContext<android.content.Context>().packageName,
            opened?.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
    }

    // LOB-263: a test build's About & account has "Show bot picks" under Send error reports, off by default and kept on the device
    @Test fun showBotPicksIsATestBuildSwitchOffByDefault() {
        assertTrue(draftBotSettingOffered, "the unit tests are a debug build")
        compose.setContent { ScreenTheme { SettingsContent(null, "draft-bot") {} } }
        compose.onNodeWithTag("draft-bot-setting").performScrollTo().assertIsOff()
        compose.onNodeWithText("Show bot picks").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("draft-bot-setting").getUnclippedBoundsInRoot().top >
            compose.onNodeWithTag("reports-setting").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("draft-bot-setting").performClick().assertIsOn()
        compose.runOnIdle { assertTrue(DraftBotPreferences(AppPlatform.current).enabled, "kept on the device") }
        compose.onNodeWithTag("draft-bot-setting").performClick().assertIsOff()
        compose.runOnIdle { assertFalse(DraftBotPreferences(AppPlatform.current).enabled) }
    }

    // LOB-264, DRF-293 (owner, 2026-11-21): Games → "Autoconfirm draft picks", off by default and kept on the device, decides
    // whether a new draft's Auto toggle starts on; a draft whose toggle was set keeps its own
    @Test fun autoconfirmDraftPicksDecidesHowNewDraftsStart() {
        val preferences = PodAutoconfirmPreferences(AppPlatform.current)
        preferences.set("touched", false)
        compose.setContent { ScreenTheme { SettingsContent(null, "autoconfirm", {}, null, FakeSettings()) } }
        compose.onNodeWithTag("pod-autoconfirm-setting").performScrollTo().assertIsOff()
        compose.onNodeWithText("Autoconfirm draft picks").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("pod-autoconfirm-setting").getUnclippedBoundsInRoot().top >
            compose.onNodeWithTag("move-sound").getUnclippedBoundsInRoot().top, "in Games, under the Your move sound")
        compose.runOnIdle { assertFalse(preferences.of("new draft"), "drafts start with Auto off") }
        compose.onNodeWithTag("pod-autoconfirm-setting").performClick().assertIsOn()
        compose.runOnIdle {
            assertTrue(preferences.startOn, "kept on the device")
            assertTrue(preferences.of("another new draft"), "now new drafts start with Auto on")
            assertFalse(preferences.of("touched"), "a draft whose toggle was set keeps it")
        }
        compose.onNodeWithTag("pod-autoconfirm-setting").performClick().assertIsOff()
        compose.runOnIdle { assertFalse(preferences.startOn) }
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
        val catalog = CatalogViewModel(AppServices.current.catalogController()).also {
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

    // LOB-113, LOB-228 (I-D56 §7, §13; owner 2026-11-09): new games start in Quick with Assume resolves When they have no mana and Autotap on; without an
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
            assume = org.asyncttwiz.protocol.game.AssumeResolves.Always, autotap = false), PausePreferences(AppPlatform.current, "pauses").engineDefaults)
        assertEquals(org.asyncttwiz.protocol.game.PlaySettings.NEW_GAMES, PausePreferences(AppPlatform.current, "someone else").engineDefaults)
        compose.onNodeWithTag("response-mode-Control").assertIsSelected()
        compose.onNodeWithTag("autotap").assertIsOff()
        // "When they have no mana" is the third choice, Never the first; one choice at a time
        compose.onNodeWithTag("assume-resolves-no-mana").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("assume-resolves-always").assertIsNotSelected()
        assertEquals(org.asyncttwiz.protocol.game.AssumeResolves.WhenNoMana, PausePreferences(AppPlatform.current, "pauses").engineDefaults.assume)
        compose.onNodeWithTag("assume-resolves-never").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("assume-resolves-no-mana").assertIsNotSelected()
        assertEquals(org.asyncttwiz.protocol.game.AssumeResolves.Never, PausePreferences(AppPlatform.current, "pauses").engineDefaults.assume)
    }

    // LOB-079, LOB-113
    @Test @Config(qualifiers = "w412dp-h2600dp-port-xhdpi")
    fun fullPagePortraitScreenshot() = capture("settings-portrait-full", "on a very tall portrait screen")

    // LOB-079
    @Test @Config(qualifiers = "w892dp-h412dp-land-xhdpi")
    fun landscapeScreenshots() = capture("settings-landscape", "in landscape")
}
