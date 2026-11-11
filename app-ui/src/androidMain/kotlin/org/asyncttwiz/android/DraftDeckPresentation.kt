package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Draft decks: kept automatically from submitted pod decks, read-only until saved as an ordinary deck. */
internal const val DRAFT_DECKS_EXPLANATION =
    "Saved automatically when you submit a pod deck. Only the newest ${DraftDecks.LIMIT} are kept: the oldest is dropped. Save as deck to keep one."

internal fun draftDecksHeading(count: Int) = "DRAFT DECKS ($count / ${DraftDecks.LIMIT})"

internal fun deckKind(deck: DeckSummary?) = if (deck?.draft != null) "draft deck" else "saved deck"

internal fun deckRowDetail(deck: DeckSummary): String {
    val counts = "${deck.mainboard} main / ${deck.sideboard} side"
    val pod = deck.draft?.podName?.takeUnless { deck.name.contains(it) } ?: return counts
    return "$counts · $pod"
}

internal fun draftDeckNote(origin: DraftDeckOrigin) =
    "Drafted in ${origin.podName} and kept automatically. Save as deck to edit it and keep it for good."

/** Game deck picker: ordinary decks first, then draft decks under their own heading (null heading: ordinary). */
internal fun deckPickerSections(decks: List<DeckSummary>): List<Pair<String?, List<DeckSummary>>> {
    val (drafts, ordinary) = decks.partition { it.draft != null }
    return listOfNotNull(ordinary.takeIf { it.isNotEmpty() }?.let { null to it },
        drafts.takeIf { it.isNotEmpty() }?.let { "Draft decks" to it })
}
