package org.asyncttwiz.android

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenFavoritesCacheTest {
    @Test fun offlineFavoritesSurviveReopeningAndRemainAccountScoped() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = AccountTokenFavoritesSource(context, "first", { null }, "https://cache-test.invalid")
        assertNull(first.cached().keys)
        val favorites = TokenFavorites(listOf(TokenIdentity.key(TokenCatalog.blank.definition)))
        assertFailsWith<AccountFailure> { first.set(favorites) }
        val reopened = AccountTokenFavoritesSource(context, "first", { null }, "https://cache-test.invalid")
        assertEquals(favorites, reopened.cached())
        assertFailsWith<AccountFailure> { reopened.refresh() }
        assertEquals(favorites, reopened.cached())
        assertNull(AccountTokenFavoritesSource(context, "second", { null }, "https://cache-test.invalid").cached().keys)
    }
}
