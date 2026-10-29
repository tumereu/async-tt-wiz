package org.asyncttwiz.android

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardRingPersistenceTest {
    @Test fun selectedPageSurvivesNewStoresAndIsIsolatedByAccountAndGameNotPlayOrCard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("card-ring-pages", Context.MODE_PRIVATE).edit().clear().commit()
        val first = CardRingPageStore(context, "alice", "game")
        assertEquals(0, first.read())
        first.write(3)
        assertEquals(3, CardRingPageStore(context, "alice", "game").read())
        assertEquals(0, CardRingPageStore(context, "bob", "game").read())
        assertEquals(0, CardRingPageStore(context, "alice", "other-game").read())
        CardRingPageStore(context, "alice", "game").write(1)
        assertEquals(1, first.read())
        first.write(20)
        assertEquals(20, first.read())
        first.write(-1)
        assertEquals(0, first.read())
    }

    @Test fun eachLayoutRemembersItsOwnPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("card-ring-pages", Context.MODE_PRIVATE).edit().clear().commit()
        val store = CardRingPageStore(context, "alice", "game")
        store.write(2)
        assertEquals(0, store.read(CardMenuLayout.HAND))
        store.write(5, CardMenuLayout.HAND)
        assertEquals(2, store.read())
        assertEquals(2, store.read(CardMenuLayout.CARD))
        assertEquals(5, store.read(CardMenuLayout.HAND))
        assertEquals(0, store.read(CardMenuLayout.PANEL))
    }
}
