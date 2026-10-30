package org.asyncttwiz.android

import android.content.Context

/** Personal layout data; action identifiers are independent of their short visual labels. */
internal data class CardRingPage(val name: String, val actions: List<String>)
internal data class CardRingSlot(val id: String, val cell: Int, val span: Int)

/** One fixed layout per context. A layout applies fully: every cell is always shown (disabled when it does not apply). */
internal enum class CardMenuLayout { CARD, COMBAT, STACK_EFFECT, HAND, PANEL }

internal object CardRingPages {
    const val columns = 5
    const val capacity = 25
    /** Owner proposal under playtest: cards in panels get their own one-page layout; false falls back to [CardMenuLayout.CARD]. */
    const val panelLayout = true
    val tempPiles = listOf("Temp 1", "Temp 2", "Temp 3", "Temp 4")
    private val libraryMoves = listOf("Library top", "Library bottom", "Shuffle into library")

    /** Battlefield, Stack, token and face-down cards. */
    val card = listOf(
        CardRingPage("Everyday", listOf("inspect", "note", "corner", "Stack", "Declare without tapping", "flip", "switchFace",
            "Graveyard", "Exile", "Clone", "Ping / Unping", "Hand") + libraryMoves + listOf("eotCorner", "Battlefield")),
        CardRingPage("Counters", org.asyncttwiz.protocol.CounterColor.entries.chunked(2).flatMapIndexed { row, colors ->
            colors.map { "counter/${it.name}" } + listOf("note", "eotNote", "resetCounters")[row]
        }),
        CardRingPage("More", listOf("owner", "otherSide", "Link", "Unlink",
            "corner/LEFT/1", "corner/LEFT/-1", "corner/RIGHT/1", "corner/RIGHT/-1", "resetCorner",
            "eotCorner/LEFT/1", "eotCorner/LEFT/-1", "eotCorner/RIGHT/1", "eotCorner/RIGHT/-1",
            "keywords", "eotKeywords") + tempPiles + listOf("Deal to target", "Deal to target or creatures",
            // Round 34: rarely needed, so last on More rather than on Everyday.
            "damage")))
    /** Attacker/blocker copies in the combat area: combat-only, nothing that modifies the real card. */
    val combat = listOf(CardRingPage("Combat", listOf("inspect", "note", "Ping / Unping", "Attack →", "Remove from combat",
        "Graveyard", "Exile", "Hand", "Deal to target", "Deal to target or creatures", "Link", "Links")))
    /** Ability markers on the Stack. */
    val stackEffect = listOf(CardRingPage("Effect", listOf("inspect", "note", "Ping / Unping", "Remove Stack effect", "Link", "Links")))
    val hand = listOf(CardRingPage("Hand", listOf("inspect", "Play", "Face down", "Reveal", "Battlefield", "Discard", "Exile") +
        libraryMoves + tempPiles + listOf("Discard random", "Reveal random", "Exile random", "Random to library bottom")))
    /** Search, look, reveal, graveyard, exile and Temp contents. */
    val panel = listOf(CardRingPage("Card", listOf("inspect", "otherSide", "Ping / Unping", "Hand", "Battlefield", "Move to Stack",
        "Graveyard", "Exile") + libraryMoves + tempPiles))

    /** Only the state twins replace a cell; nothing is ever added, hidden or left blank. */
    fun pages(layout: CardMenuLayout, token: Boolean = false, attacker: Boolean = false): List<CardRingPage> = when (layout) {
        CardMenuLayout.CARD -> card.mapIndexed { index, page -> if (index != 0) page else page.copy(actions = page.actions.map { id ->
            when {
                id == "Declare without tapping" && attacker -> "Attack →"
                id == "Hand" && token -> "Remove token"
                else -> id
            }
        }) }
        CardMenuLayout.COMBAT -> combat
        CardMenuLayout.STACK_EFFECT -> stackEffect
        CardMenuLayout.HAND -> hand
        CardMenuLayout.PANEL -> if (panelLayout) panel else pages(CardMenuLayout.CARD, token, attacker)
    }

    /** Projections get their combat/effect layouts; a real card's zone picks the rest. */
    fun context(board: org.asyncttwiz.protocol.BoardState, viewId: String): CardMenuLayout? {
        if (board.stackEffects.any { it.id == viewId }) return CardMenuLayout.STACK_EFFECT
        if (org.asyncttwiz.protocol.CombatStackRules.occurrences(board.combat).any { it.id == viewId }) return CardMenuLayout.COMBAT
        val card = board.cards.singleOrNull { it.id == viewId } ?: return null
        return when (board.containers.singleOrNull { it.id == card.containerId }?.zone) {
            org.asyncttwiz.protocol.PlayZone.HAND -> CardMenuLayout.HAND
            org.asyncttwiz.protocol.PlayZone.BATTLEFIELD, org.asyncttwiz.protocol.PlayZone.STACK, null -> CardMenuLayout.CARD
            else -> if (panelLayout) CardMenuLayout.PANEL else CardMenuLayout.CARD
        }
    }

    /** True when [cardIds] include a source with an attacker copy in the current combat. */
    fun declaredAttacker(board: org.asyncttwiz.protocol.BoardState, cardIds: Collection<String>) =
        board.combat?.pairs.orEmpty().any { pair -> pair.attackers.any { it.sourceId in cardIds } }

    fun span(id: String) = if (id in listOf("corner", "corner/counters", "damage", "eotCorner") ||
        org.asyncttwiz.protocol.CounterColor.entries.any { id == "counter/${it.name}" }) 2 else 1

    /** Row-end padding belongs to the layout, never to an action or half of a widget. */
    fun layout(actions: List<String>): List<CardRingSlot> {
        var cell = 0
        return actions.map { id ->
            val span = span(id)
            if (cell % columns + span > columns) cell += columns - cell % columns
            CardRingSlot(id, cell, span).also { cell += span }
        }
    }

    fun fits(actions: List<String>) = actions.isEmpty() || layout(actions).last().let { it.cell + it.span <= capacity }
    fun adjacent(selected: Int, delta: Int, count: Int) = (selected + delta).mod(count)
}

internal class CardRingPageStore(context: Context, account: String, game: String) {
    private val preferences = context.applicationContext.getSharedPreferences("card-ring-pages", Context.MODE_PRIVATE)
    private val key = "${account.length}:$account:$game"
    // The battlefield layout keeps the original key, so remembered pages survive the layout change.
    private fun key(layout: CardMenuLayout) = if (layout == CardMenuLayout.CARD) key else "$key/${layout.name}"
    fun read(layout: CardMenuLayout = CardMenuLayout.CARD) = preferences.getInt(key(layout), 0).coerceAtLeast(0)
    fun write(page: Int, layout: CardMenuLayout = CardMenuLayout.CARD) { preferences.edit().putInt(key(layout), page.coerceAtLeast(0)).apply() }
}

/** A press that began during the guard stays inert, even if released after the deadline. */
internal class CardRingInputGuard(val openedAt: Long, val duration: Long = 280L) {
    fun accepts(downAt: Long) = downAt - openedAt >= duration
}

internal enum class CardRingSide { ABOVE, BELOW, LEFT, RIGHT }
internal data class CardRingPlacement(val square: TableBounds, val side: CardRingSide)

/** Pure dp geometry, independent of page contents and font scale. */
internal object CardRingGeometry {
    const val cell = 48f
    const val gridSide = CardRingPages.columns * cell
    const val squareSide = gridSide + 24f
    private const val margin = 12f
    private const val gap = 8f
    /** Beside the card, [focusY] (default: the middle) of the popup is lined up with the card's center. */
    fun place(width: Float, height: Float, card: TableBounds, own: Boolean,
        popupWidth: Float = squareSide, popupHeight: Float = squareSide, focusY: Float = popupHeight / 2): CardRingPlacement? {
        val screen = TableBounds(margin, margin, (width - 2 * margin).coerceAtLeast(0f), (height - 2 * margin).coerceAtLeast(0f))
        val spaces = listOf(
            screen.copy(height = (card.y - gap - screen.y).coerceIn(0f, screen.height)),
            screen.copy(y = (card.bottom + gap).coerceIn(screen.y, screen.bottom), height = (screen.bottom - card.bottom - gap).coerceIn(0f, screen.height)),
            screen.copy(width = (card.x - gap - screen.x).coerceIn(0f, screen.width)),
            screen.copy(x = (card.right + gap).coerceIn(screen.x, screen.right), width = (screen.right - card.right - gap).coerceIn(0f, screen.width)))
        val preferred = if (own) 0 else 1
        val side = spaces.indices.filter { spaces[it].width >= popupWidth && spaces[it].height >= popupHeight }
            .maxWithOrNull(compareBy<Int> { spaces[it].width * spaces[it].height }.thenBy { it == preferred }) ?: return null
        val region = spaces[side]
        val x = (when (side) {
            2 -> region.right - popupWidth
            3 -> region.x
            else -> card.center.x - popupWidth / 2
        }).coerceIn(region.x, region.right - popupWidth)
        val y = (when (side) {
            0 -> region.bottom - popupHeight
            1 -> region.y
            else -> card.center.y - focusY
        }).coerceIn(region.y, region.bottom - popupHeight)
        return CardRingPlacement(TableBounds(x, y, popupWidth, popupHeight), CardRingSide.entries[side])
    }
}
