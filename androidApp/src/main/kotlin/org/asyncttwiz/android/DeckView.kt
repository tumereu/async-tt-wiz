package org.asyncttwiz.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.SavedDeckSection

/**
 * ZON-093 (owner, 2026-11-07): your deck as you play it, from your own Library's menu: [deck] is every card of yours in
 * the game (no tokens), [stillInLibrary] the deck without the cards you have seen leave your library (in your hand, on
 * the battlefield, in a graveyard or face up in exile: what you can see now outside your library). Both are card data
 * digests with counts; the view sorts them by type and name, so the library's order never shows.
 */
internal object DeckView {
    private val zones = setOf(PlayZone.LIBRARY, PlayZone.HAND, PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE)

    /** [viewer]'s deck as played: their own cards (no tokens) in any zone, by digest. */
    fun deck(play: TableBoard, viewer: String): List<Pair<String, Long>> = counts(own(play, viewer).map { it.first.definition })

    /** [viewer]'s deck without the cards they have seen leave their library, by digest. */
    fun stillInLibrary(play: TableBoard, viewer: String): List<Pair<String, Long>> {
        val cards = own(play, viewer)
        val left = counts(cards.filter { (card, zone) -> zone != PlayZone.LIBRARY && seen(play, card, viewer) }.map { it.first.definition }).toMap()
        return counts(cards.map { it.first.definition }).map { (digest, n) -> digest to n - (left[digest] ?: 0L) }.filter { it.second > 0 }
    }

    private fun own(play: TableBoard, viewer: String): List<Pair<org.asyncttwiz.protocol.PlayCard, PlayZone>> {
        val zoneOf = play.board.containers.associate { it.id to it.zone }
        return play.board.cards.distinctBy { it.id }.mapNotNull { card ->
            val zone = zoneOf[card.containerId]?.takeIf { it in zones } ?: return@mapNotNull null
            (card to zone).takeIf { card.owner == viewer && !card.token }
        }
    }

    /** Whether [viewer] sees [card] now: face up for them, or a face-down card they may look at (their own morph). */
    private fun seen(play: TableBoard, card: org.asyncttwiz.protocol.PlayCard, viewer: String): Boolean =
        CardVisibility.isVisibleFaceUp(play.board, card, viewer) ||
            play.engine?.visible(ObjectId.Card(CardId(card.id), card.zoneChanges)) == true

    private fun counts(digests: List<String>): List<Pair<String, Long>> =
        digests.groupingBy { it }.eachCount().map { (digest, n) -> digest to n.toLong() }
}

/**
 * ZON-093: the View deck panel: your deck in the deck views' type groups (SYS-051, LOB-056: "Creatures · 12", cards by
 * name, ×N badges), "All" or "Still in library"; holding a card inspects it. Looking only: nothing in the game changes.
 */
@Composable
internal fun DeckViewPanel(play: TableBoard, viewer: String, dismiss: () -> Unit) {
    var remaining by rememberSaveable(play.gameId) { mutableStateOf(false) }
    val definitions = remember(play.definitions) { play.definitions.associate { it.digest to it.definition } }
    val all = remember(IdentityKey(play.board), viewer) { DeckView.deck(play, viewer) }
    val left = remember(IdentityKey(play.board), viewer) { DeckView.stillInLibrary(play, viewer) }
    val shown = if (remaining) left else all
    val groups = remember(shown, definitions) { preparationGroups(shown, definitions) }
    var held by remember { mutableStateOf<Pair<String, PreparationTile>?>(null) }
    val previewScroll = rememberScrollState()
    val previewScreen = holdPreviewScreenHeight()
    // ZON-093 (owner, 2026-11-08): the cards are the Panels card size, as every card panel's (STK-044)
    val (cardWidth, _) = choiceCardSize()
    InlinePopupPanel(dismiss, title = "Deck", width = 384.dp, height = 560.dp, key = "deck-view", sheet = true, closeIcon = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!remaining, { remaining = false }, { Text("All · ${all.sumOf { it.second }}") }, Modifier.ringInputTarget().testTag("deck-view-all"))
            FilterChip(remaining, { remaining = true }, { Text("Still in library · ${left.sumOf { it.second }}") },
                Modifier.ringInputTarget().testTag("deck-view-library"))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp).ringInputTarget(draggable = true).testTag("deck-view"), contentPadding = PaddingValues(bottom = 8.dp)) {
            visualDeckSection(SavedDeckSection.MAINBOARD, shown.sumOf { it.second }, groups, 1, header = false, cellWidth = cardWidth) { tile ->
                val key = "deck/${tile.digest}"
                VisualDeckCard(tile, key, "Deck", onHold = { open ->
                    if (open) {
                        previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                        held = key to tile
                    } else if (held?.first == key) held = null
                }, onHoldScroll = { previewScroll.holdPreviewScroll(it, previewScreen) })
            }
        }
        // Attached to this panel's window, as the Stack list's Inspect is
        held?.second?.let { tile ->
            val card = remember(tile.digest, tile.definition) {
                tile.definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(tile.digest, it) }
            }
            PodCardArtPreview(card, tile.name, previewScroll)
        }
    }
}
