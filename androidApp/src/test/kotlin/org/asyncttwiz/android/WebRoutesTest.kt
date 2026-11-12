package org.asyncttwiz.android

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** The web client's addresses, history and stale-tab reload (app-ui's WebRoutes.kt; the web shell applies them). */
class WebRoutesTest {
    private val game = "3f2a9c1e-0b5d-4a8e-9c7f-1d2e3f4a5b6c"

    // WEB-001
    @Test fun everyScreenHasAnAddressThatReadsBackAsItself() {
        val routes = listOf(WebRoute.Menu, WebRoute.Games, WebRoute.Game(game), WebRoute.Pods, WebRoute.Pod("p1"),
            WebRoute.Pod("p1", PodTab.BUILD), WebRoute.Decks, WebRoute.DeckImport, WebRoute.Deck("d1"), WebRoute.DeckEdit("d1"),
            WebRoute.CubeNew, WebRoute.Cube("c1"), WebRoute.Contacts, WebRoute.Settings, WebRoute.Stats)
        for (route in routes) assertEquals(route, WebRoute.parse(route.path), route.path)
        assertEquals("/games/$game", WebRoute.Game(game).path)
        assertEquals("/pods/p1/draft", WebRoute.Pod("p1", PodTab.DRAFT).path)
        assertEquals("/decks/d1/edit", WebRoute.DeckEdit("d1").path)
        assertEquals(WebRoute.Games, WebRoute.parse("/games/"))
    }

    // WEB-001
    @Test fun addressesOfNoScreenReadAsNone() {
        for (path in listOf("/unknown", "/games/a/b", "/pods/p1/table", "/decks/d1/view", "/cubes", "/cubes/c1/edit",
                "/settings/more", "/games/${"x".repeat(65)}", "/games/a.b", "/games/%2e%2e"))
            assertNull(WebRoute.parse(path), path)
    }

    // WEB-003: the on-screen Back's way up when the browser has no earlier entry
    @Test fun eachScreenReturnsToTheOneItIsPartOf() {
        assertNull(WebRoute.Menu.parent)
        assertEquals(WebRoute.Menu, WebRoute.Games.parent)
        assertEquals(WebRoute.Games, WebRoute.Game(game).parent)
        assertEquals(WebRoute.Deck("d1"), WebRoute.DeckEdit("d1").parent)
        assertEquals(listOf(WebRoute.Deck("d1"), WebRoute.Decks, WebRoute.Menu), WebRoute.DeckEdit("d1").ancestors)
        assertEquals(WebRoute.Decks, WebRoute.Cube("c1").parent)
    }

    // WEB-002
    @Test fun newScreensAddEntriesAndReturningGoesBackInsteadOfLooping() {
        val history = WebHistory(WebRoute.Menu)
        assertEquals(HistoryStep.Push(WebRoute.Games, 1), history.shown(WebRoute.Games))
        assertEquals(HistoryStep.Push(WebRoute.Game(game), 2), history.shown(WebRoute.Game(game)))
        assertEquals(HistoryStep.None, history.shown(WebRoute.Game(game)))
        // The on-screen Back (or the lobby's own Back) returned to Games: the browser goes back, so its Back doesn't loop
        assertEquals(HistoryStep.Back, history.shown(WebRoute.Games))
        assertEquals(HistoryStep.Back, history.shown(WebRoute.Menu))
        // A new screen from there drops the entries after it
        assertEquals(HistoryStep.Push(WebRoute.Settings, 1), history.shown(WebRoute.Settings))
        assertEquals(HistoryStep.Push(WebRoute.Games, 2), history.shown(WebRoute.Games))
    }

    // WEB-002: a pod's tabs are one screen
    @Test fun aPodsTabsReplaceItsEntry() {
        val history = WebHistory(WebRoute.Pods)
        assertEquals(HistoryStep.Push(WebRoute.Pod("p1"), 1), history.shown(WebRoute.Pod("p1")))
        assertEquals(HistoryStep.Replace(WebRoute.Pod("p1", PodTab.DRAFT), 1), history.shown(WebRoute.Pod("p1", PodTab.DRAFT)))
        assertEquals(HistoryStep.Replace(WebRoute.Pod("p1", PodTab.STATS), 1), history.shown(WebRoute.Pod("p1", PodTab.STATS)))
        assertEquals(HistoryStep.Back, history.shown(WebRoute.Pods))
    }

    // WEB-002: a refreshed deck editor's address opens through its deck without leaving entries Back would stop at
    @Test fun theScreensOnTheWayToAnOpenedAddressReplaceItsEntry() {
        val history = WebHistory(null)
        history.open(WebRoute.DeckEdit("d1"))
        assertEquals(HistoryStep.Replace(WebRoute.Menu, 0), history.shown(WebRoute.Menu))
        assertEquals(HistoryStep.Replace(WebRoute.Decks, 0), history.shown(WebRoute.Decks))
        assertEquals(HistoryStep.Replace(WebRoute.Deck("d1"), 0), history.shown(WebRoute.Deck("d1")))
        assertEquals(HistoryStep.Replace(WebRoute.DeckEdit("d1"), 0), history.shown(WebRoute.DeckEdit("d1")))
        assertNull(history.opening)
        assertEquals(HistoryStep.Push(WebRoute.Settings, 1), history.shown(WebRoute.Settings))
    }

    // WEB-002: the browser's Back and Forward
    @Test fun browserBackOpensTheEntrysScreenAndAnotherScreenEndsTheWait() {
        val history = WebHistory(WebRoute.Menu)
        history.shown(WebRoute.Games)
        history.shown(WebRoute.Game(game))
        history.popped(WebRoute.Games, 1)
        assertEquals(WebRoute.Games, history.current)
        assertEquals(HistoryStep.None, history.shown(WebRoute.Games))
        history.popped(WebRoute.Game(game), 2)
        // A deck shown instead of the game being opened (the player moved on): the wait ends, the deck gets its entry
        assertEquals(HistoryStep.Push(WebRoute.Deck("d1"), 3), history.shown(WebRoute.Deck("d1")))
    }

    // WEB-006
    @Test fun aStaleTabReloadsOnlyAtASafeMoment() {
        assertTrue(StaleReload.now(true, WebRoute.Menu, opened = false, layerOpen = false, typing = false))
        assertTrue(StaleReload.now(true, WebRoute.Games, opened = false, layerOpen = false, typing = false))
        assertTrue(StaleReload.now(true, null, opened = false, layerOpen = false, typing = false))
        assertTrue(StaleReload.now(true, WebRoute.Game(game), opened = true, layerOpen = false, typing = false))
        assertFalse(StaleReload.now(true, WebRoute.Game(game), opened = false, layerOpen = false, typing = false))
        assertFalse(StaleReload.now(false, WebRoute.Menu, opened = false, layerOpen = false, typing = false))
        assertFalse(StaleReload.now(true, WebRoute.Menu, opened = false, layerOpen = true, typing = false))
        assertFalse(StaleReload.now(true, WebRoute.Games, opened = false, layerOpen = false, typing = true))
        assertFalse(StaleReload.now(true, null, opened = false, layerOpen = false, typing = true))
        for (route in listOf(WebRoute.Decks, WebRoute.DeckEdit("d1"), WebRoute.Settings, WebRoute.Pod("p1"), WebRoute.Contacts))
            assertFalse(StaleReload.now(true, route, opened = true, layerOpen = false, typing = false), route.path)
    }
}
