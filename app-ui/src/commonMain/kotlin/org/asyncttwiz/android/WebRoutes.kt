package org.asyncttwiz.android

// The web client's screens as addresses (docs/features/web.md WEB-001..WEB-004), the browser history kept in step with
// them, and when an open tab left on an older build reloads itself (WEB-006). Plain logic: the web shell (app-ui's wasmJs
// side) reads and writes the browser's address and history with it; nothing here runs on Android.

/** A screen of the web client with its own address. [path] is the address; [parse] reads one back. */
sealed interface WebRoute {
    val path: String

    /** The screen the on-screen Back of this one returns to when the browser has no earlier entry; null for the menu. */
    val parent: WebRoute?

    data object Menu : WebRoute {
        override val path = "/"
        override val parent: WebRoute? = null
    }
    data object Games : WebRoute {
        override val path = "/games"
        override val parent = Menu
    }
    /** A game's lobby or table. */
    data class Game(val id: String) : WebRoute {
        override val path get() = "/games/$id"
        override val parent get() = Games
    }
    data object Pods : WebRoute {
        override val path = "/pods"
        override val parent = Menu
    }
    /** A pod at [tab] (null before it has loaded: its usual tab). */
    data class Pod(val id: String, val tab: PodTab? = null) : WebRoute {
        override val path get() = "/pods/$id" + (tab?.let { "/" + it.name.lowercase() } ?: "")
        override val parent get() = Pods
    }
    /** Manage decks: the saved decks and cubes. */
    data object Decks : WebRoute {
        override val path = "/decks"
        override val parent = Menu
    }
    /** A new deck's import and editor. */
    data object DeckImport : WebRoute {
        override val path = "/decks/import"
        override val parent = Decks
    }
    data class Deck(val id: String) : WebRoute {
        override val path get() = "/decks/$id"
        override val parent get() = Decks
    }
    data class DeckEdit(val id: String) : WebRoute {
        override val path get() = "/decks/$id/edit"
        override val parent get() = Deck(id)
    }
    /** A new cube's editor. */
    data object CubeNew : WebRoute {
        override val path = "/cubes/new"
        override val parent = Decks
    }
    data class Cube(val id: String) : WebRoute {
        override val path get() = "/cubes/$id"
        override val parent get() = Decks
    }
    data object Contacts : WebRoute {
        override val path = "/contacts"
        override val parent = Menu
    }
    data object Settings : WebRoute {
        override val path = "/settings"
        override val parent = Menu
    }
    data object Stats : WebRoute {
        override val path = "/stats"
        override val parent = Menu
    }

    /** The screens this one is reached through, nearest first (a deck's editor: the deck, then Manage decks, then the menu). */
    val ancestors: List<WebRoute> get() = generateSequence(parent) { it.parent }.toList()

    /** Whether [other] is this screen in another state (a pod's tab), so moving between them replaces the history entry. */
    fun sameScreen(other: WebRoute): Boolean = this is Pod && other is Pod && id == other.id

    companion object {
        private val ID = Regex("[A-Za-z0-9_-]{1,64}")

        /** The screen at [path] (an address's path, without query or fragment), null when no screen has it. */
        fun parse(path: String): WebRoute? {
            val parts = path.trim().removeSuffix("/").split('/').drop(1)
            fun id(index: Int) = parts.getOrNull(index)?.takeIf { ID.matches(it) }
            return when (parts.firstOrNull() ?: "") {
                "" -> Menu.takeIf { parts.size <= 1 }
                "games" -> when (parts.size) { 1 -> Games; 2 -> id(1)?.let(::Game); else -> null }
                "pods" -> when (parts.size) {
                    1 -> Pods
                    2 -> id(1)?.let { Pod(it) }
                    3 -> id(1)?.let { pod -> PodTab.entries.firstOrNull { it.name.lowercase() == parts[2] }?.let { Pod(pod, it) } }
                    else -> null
                }
                "decks" -> when {
                    parts.size == 1 -> Decks
                    parts.size == 2 && parts[1] == "import" -> DeckImport
                    parts.size == 2 -> id(1)?.let(::Deck)
                    parts.size == 3 && parts[2] == "edit" -> id(1)?.let(::DeckEdit)
                    else -> null
                }
                "cubes" -> when {
                    parts.size == 2 && parts[1] == "new" -> CubeNew
                    parts.size == 2 -> id(1)?.let(::Cube)
                    else -> null
                }
                "contacts" -> Contacts.takeIf { parts.size == 1 }
                "settings" -> Settings.takeIf { parts.size == 1 }
                "stats" -> Stats.takeIf { parts.size == 1 }
                else -> null
            }
        }
    }
}

/** What the browser's history does when the app's screen changes. */
sealed interface HistoryStep {
    /** Nothing: the address already shows this screen. */
    data object None : HistoryStep
    /** A new entry after the current one (the ones after it are dropped). */
    data class Push(val route: WebRoute, val index: Int) : HistoryStep
    /** The current entry now has this address. */
    data class Replace(val route: WebRoute, val index: Int) : HistoryStep
    /** Go back one entry: the app returned to the screen it came from (on-screen Back), so browser Back doesn't loop. */
    data object Back : HistoryStep
}

/**
 * This tab's browser history as the app knows it (WEB-002): the entries it made, by their index in history.state, and the
 * one shown. The app's screen changes (taps, on-screen Back) go through [shown]; the browser's Back and Forward through
 * [popped]. While a screen opened by its address is still loading (a refreshed deck's editor, say) the screens on the way
 * to it replace the entry instead of adding ones Back would stop at ([opening]).
 */
class WebHistory(initial: WebRoute?, private var index: Int = 0) {
    private val entries = mutableMapOf<Int, WebRoute>().apply { if (initial != null) put(index, initial) }
    /** The screen being opened from an address, until it shows or another screen is chosen. */
    var opening: WebRoute? = null
        private set

    /** The current entry's screen, null before the app has one (signed out). */
    val current: WebRoute? get() = entries[index]

    /** The app shows [route] now: what the history does about it. */
    fun shown(route: WebRoute): HistoryStep {
        val target = opening
        if (target != null) {
            if (route == target) opening = null
            if (route == target || route in target.ancestors) return replace(route)
            opening = null
        }
        val now = entries[index]
        return when {
            now == null -> replace(route)
            route == now -> HistoryStep.None
            route.sameScreen(now) -> replace(route)
            entries[index - 1] == route -> { index--; HistoryStep.Back }
            else -> {
                entries.keys.filter { it > index }.forEach(entries::remove)
                index++
                entries[index] = route
                HistoryStep.Push(route, index)
            }
        }
    }

    /** The browser moved to the entry at [entryIndex] (its history.state; null for one this app didn't make), whose address
     * shows [route]. The app is to open [route]; until it shows, the screens on the way replace the entry. */
    fun popped(route: WebRoute, entryIndex: Int?) {
        index = entryIndex ?: index
        entries[index] = route
        opening = route
    }

    /** The app opens [route] from its address (first load or a refresh). */
    fun open(route: WebRoute) {
        entries[index] = route
        opening = route
    }

    private fun replace(route: WebRoute): HistoryStep {
        if (entries[index] == route) return HistoryStep.None
        entries[index] = route
        return HistoryStep.Replace(route, index)
    }
}

/**
 * When a tab running an older web build reloads itself (WEB-006, docs/specs/web-client.md §2.2): at once on the menu or the
 * Games list, or as a game is opened; never while a dialog, panel or other layer is open or text is being typed. Elsewhere
 * it waits until the player reaches one of those.
 */
object StaleReload {
    /** [route]: the screen shown (null: signed out); [opened]: it was just opened (a game reloads only as it opens). */
    fun now(stale: Boolean, route: WebRoute?, opened: Boolean, layerOpen: Boolean, typing: Boolean): Boolean =
        stale && !layerOpen && !typing &&
            (route == null || route == WebRoute.Menu || route == WebRoute.Games || (opened && route is WebRoute.Game))
}
