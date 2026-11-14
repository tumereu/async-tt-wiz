package org.asyncttwiz.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.appui.AppPlatform
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLayoutTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() {
        for (name in listOf("card-appearance", "card-appearance-desktop", "layout"))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    // WEB-013: Automatic draws the desktop layout on a window of at least 1024 × 600 whose pointer hovers, else the phone's
    @Test fun automaticChoosesByWindowSizeAndPointer() {
        assertEquals(AppLayout.Desktop, AppLayouts.choose(LayoutChoice.Automatic, 1024, 600, hovers = true))
        assertEquals(AppLayout.Desktop, AppLayouts.choose(LayoutChoice.Automatic, 1440, 900, hovers = true))
        assertEquals(AppLayout.Phone, AppLayouts.choose(LayoutChoice.Automatic, 1023, 900, hovers = true))
        assertEquals(AppLayout.Phone, AppLayouts.choose(LayoutChoice.Automatic, 1440, 599, hovers = true))
        assertEquals(AppLayout.Phone, AppLayouts.choose(LayoutChoice.Automatic, 1440, 900, hovers = false), "a touchscreen")
    }

    // WEB-014: Phone and Desktop override the window and the pointer
    @Test fun theSettingOverridesTheAutomaticChoice() {
        assertEquals(AppLayout.Phone, AppLayouts.choose(LayoutChoice.Phone, 1920, 1080, hovers = true))
        assertEquals(AppLayout.Desktop, AppLayouts.choose(LayoutChoice.Desktop, 412, 892, hovers = false))
    }

    // WEB-014: kept in the browser's preferences, Automatic until changed
    @Test fun theLayoutChoiceIsKept() {
        val preferences = LayoutPreferences(AppPlatform.current)
        assertEquals(LayoutChoice.Automatic, preferences.choice)
        var changed = 0
        val stop = preferences.observe { changed++ }
        preferences.choice = LayoutChoice.Desktop
        assertEquals(LayoutChoice.Desktop, LayoutPreferences(AppPlatform.current).choice)
        assertTrue(changed > 0, "observers learn the change")
        stop()
        assertEquals(LayoutChoice.Automatic, LayoutChoice.of("something else"))
    }

    // WEB-022, TBL-034: card sizes are kept for each layout; the desktop layout's start at battlefield 2×, hand 2×, and
    // changing them leaves the phone layout's as they were
    @Test fun cardSizesAreKeptPerLayout() {
        val phone = CardAppearancePreferences(AppPlatform.current)
        val desktop = CardAppearancePreferences(AppPlatform.current, AppLayout.Desktop)
        assertEquals(1f, phone.read().cardSize)
        assertEquals(1f, phone.read().size(CardArea.HAND))
        assertEquals(2f, desktop.read().cardSize)
        assertEquals(2f, desktop.read().size(CardArea.HAND))
        assertEquals(1.5f, desktop.read().size(CardArea.PILES))
        desktop.setSize(2.5f, CardArea.HAND)
        assertEquals(2.5f, CardAppearancePreferences(AppPlatform.current, AppLayout.Desktop).read().size(CardArea.HAND))
        assertEquals(1f, phone.read().size(CardArea.HAND), "the phone layout's unchanged")
        phone.setSize(1.2f)
        assertEquals(2f, desktop.read().cardSize, "the desktop layout's unchanged")
    }
}
