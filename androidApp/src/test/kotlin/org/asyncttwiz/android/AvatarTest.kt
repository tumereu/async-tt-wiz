package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.Avatar as AvatarIdentity
import org.asyncttwiz.storage.CatalogAvatar
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
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
        compose.onNodeWithText("Downloading card records…").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Choose your avatar").assertDoesNotExist()
        compose.runOnIdle { fixture.auth.seed(AuthState(restoring = false)) }
        compose.runOnIdle { fixture.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex)) }
        compose.onNodeWithText("Choose your avatar").assertDoesNotExist()
        assertFalse(AvatarPromptMemory(context).shouldShow(ScreenFixtures.alex))
    }

    @Test fun rerollOffersSixDistinctNewChoicesWhenAvailable() {
        val pool = (1..20).map { CatalogAvatar("Card $it", AvatarIdentity(java.util.UUID(0, it.toLong()).toString())) }
        val first = randomAvatarChoices(pool, emptyList(), Random(1))
        val second = randomAvatarChoices(pool, first, Random(2))
        assertEquals(6, first.size)
        assertEquals(6, second.size)
        assertTrue(first.none { it in second })
        assertEquals(3, randomAvatarChoices(pool.take(3), emptyList()).size)
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
