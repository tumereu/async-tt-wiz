package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.Avatar as AvatarIdentity
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AvatarTest {
    @get:Rule val compose = createComposeRule()
    private val viewModels = androidx.lifecycle.ViewModelStore()
    @After fun clearViewModels() { viewModels.clear() }

    @Test fun catalogDownloadHasVisibleProgressWithoutBlockingLoginOrRegistration() {
        val fixture = ScreenFixtures()
        fixture.auth.seed(AuthState(restoring = false))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also { viewModels.put("catalog", it) }
        val progress = org.asyncttwiz.storage.CatalogProgress(org.asyncttwiz.storage.CatalogPhase.DOWNLOADING, 10, 100)
        catalog.seed(CatalogUiState(busy = true, progress = progress, status = progress.label()))
        compose.setContent { AccountEntry(fixture.auth, fixture.reset, catalog) }
        compose.onNodeWithText(progress.label()).assertIsDisplayed()
        compose.onNodeWithText("Not now").assertDoesNotExist()
        compose.onNodeWithTag("email").performTextInput("player@example.test")
        assertEquals("player@example.test", fixture.auth.state.email)
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.onNodeWithTag("username").performTextInput("Wizard")
        assertEquals("Wizard", fixture.auth.state.username)
        compose.onNodeWithText(progress.label()).assertIsDisplayed()
    }

    @Test fun firstLoginShowsWaitingChooserAndSkipDoesNotNagOnNextLogin() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("avatar-chooser", 0).edit().clear().commit()
        val fixture = ScreenFixtures()
        fixture.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also { viewModels.put("catalog", it) }
        catalog.seed(CatalogUiState(busy = true, status = "Downloading card records…"))
        compose.setContent { AccountEntry(fixture.auth, fixture.reset, catalog) }
        compose.onNodeWithText("Choose your avatar").assertIsDisplayed()
        compose.onNodeWithText("Avatars need the card catalog. Downloading…").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Choose your avatar").assertDoesNotExist()
        compose.runOnIdle { fixture.auth.seed(AuthState(restoring = false)) }
        compose.runOnIdle { fixture.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex)) }
        compose.onNodeWithText("Choose your avatar").assertDoesNotExist()
        assertFalse(AvatarPromptMemory(context).shouldShow(ScreenFixtures.alex))
    }

    @Test @Config(qualifiers = "w412dp-h892dp-port-mdpi")
    fun compactProfileFitsWithoutScrollingAndCancelKeepsName() {
        val fixture = ScreenFixtures()
        fixture.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also { viewModels.put("catalog", it) }
        val progress = org.asyncttwiz.storage.CatalogProgress(org.asyncttwiz.storage.CatalogPhase.DOWNLOADING, 43, 100)
        catalog.seed(CatalogUiState(busy = true, progress = progress))
        compose.setContent { MaterialTheme { ProfileSettings(fixture.auth, catalog) } }
        compose.onNodeWithText("Change username").assertDoesNotExist()
        compose.onNodeWithText("Change avatar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit profile").performClick()
        compose.onNodeWithText("Avatars need the card catalog. Downloading… 43%").assertDoesNotExist()
        compose.onNodeWithText("Search by card name").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reroll avatars").assertIsNotEnabled()
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Username").assertIsDisplayed().performTextReplacement("Unsaved")
        compose.onNodeWithContentDescription("Save profile").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel profile").assertIsDisplayed().performClick()
        assertEquals(ScreenFixtures.alex.username, fixture.auth.state.account!!.username)
        compose.onNodeWithContentDescription("Save profile").assertDoesNotExist()
    }

    @Test fun waitingChooserBecomesAvailableInPlaceWhenCatalogIsInstalled() {
        val fixture = ScreenFixtures()
        fixture.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex, avatarChooser = true))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also { viewModels.put("catalog", it) }
        val progress = org.asyncttwiz.storage.CatalogProgress(org.asyncttwiz.storage.CatalogPhase.INDEXING, 50, 100)
        catalog.seed(CatalogUiState(busy = true, progress = progress))
        compose.setContent { MaterialTheme { AvatarChooser(fixture.auth, catalog) } }
        compose.onNodeWithText("Avatars need the card catalog. Indexing… 50%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reroll avatars").assertIsNotEnabled()
        compose.runOnIdle { catalog.seed(CatalogUiState(installed = org.asyncttwiz.storage.CatalogMetadata("2026-10-27", "test"))) }
        compose.onNodeWithText("Avatars need the card catalog. Indexing… 50%").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reroll avatars").assertIsEnabled()
        compose.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test fun shownOrSkippedIsRememberedPerAccountAcrossMemoryInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("avatar-chooser", 0).edit().clear().commit()
        val account = AccountIdentity("first", "Alice")
        val memory = AvatarPromptMemory(context)
        assertTrue(memory.shouldShow(account))
        memory.shown(account.accountId)
        assertFalse(AvatarPromptMemory(context).shouldShow(account))
        assertTrue(memory.shouldShow(account.copy(accountId = "second")))
        assertFalse(memory.shouldShow(account.copy(avatar = AvatarIdentity(java.util.UUID.randomUUID().toString()))))
    }

    @Test fun fallbackIsInitialWithStableAccountColor() {
        compose.setContent { MaterialTheme { Avatar(AccountIdentity("stable", "Alice")) } }
        compose.onNodeWithText("A").assertIsDisplayed()
        compose.onNodeWithContentDescription("Alice's avatar").assertExists()
        assertEquals(avatarColor("stable"), avatarColor("stable"))
        assertNotEquals(avatarColor("stable"), avatarColor("other"))
        assertEquals("?", avatarInitial(""))
    }

    @Test fun unavailableArtworkStillShowsInitial() {
        compose.setContent { MaterialTheme { Avatar(AccountIdentity("stable", "Bob", AvatarIdentity("unavailable"))) } }
        compose.onNodeWithText("B").assertIsDisplayed()
    }
}
