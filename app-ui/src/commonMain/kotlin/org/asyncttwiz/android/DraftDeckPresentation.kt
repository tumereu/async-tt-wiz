package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Draft decks: kept automatically from submitted pod decks, read-only until saved as an ordinary deck. */
const val DRAFT_DECKS_EXPLANATION =
    "Saved automatically when you submit a pod deck. Only the newest ${DraftDecks.LIMIT} are kept: the oldest is dropped. Save as deck to keep one."

fun draftDecksHeading(count: Int) = "DRAFT DECKS ($count / ${DraftDecks.LIMIT})"

fun deckKind(deck: DeckSummary?) = if (deck?.draft != null) "draft deck" else "saved deck"

fun deckRowDetail(deck: DeckSummary): String {
    val counts = "${deck.mainboard} main / ${deck.sideboard} side"
    val pod = deck.draft?.podName?.takeUnless { deck.name.contains(it) } ?: return counts
    return "$counts · $pod"
}

fun draftDeckNote(origin: DraftDeckOrigin) =
    "Drafted in ${origin.podName} and kept automatically. Save as deck to edit it and keep it for good."

/** LOB-250: the deck line of a Random game before the deal. */
const val DEALT_AT_RANDOM = "Dealt at random when the game starts"

/** LOB-256: " · from <lender>" after a borrowed deck's counts in [game]'s lobby; null for a player's own deck. */
fun borrowedFrom(deck: PreparedDeck, game: GameSummary?): String? = deck.lender?.let { lender ->
    val name = listOfNotNull(game?.inviter, game?.recipient).firstOrNull { it.accountId == lender }?.username ?: return@let null
    " · from $name"
}

/** LOB-252: the line an invitation's rows add when its creator lends [accountId] decks; null otherwise. */
fun lendsDecksLine(game: GameSummary, accountId: String): String? =
    ActivityTexts.lendsDecks(game.inviter.username).takeIf { game.deckSettings.lending && game.recipient.accountId == accountId &&
        game.invitationState == InvitationState.PENDING && game.podId == null }

/** Game deck picker: ordinary decks first, then draft decks under their own heading (null heading: ordinary). */
fun deckPickerSections(decks: List<DeckSummary>): List<Pair<String?, List<DeckSummary>>> {
    val (drafts, ordinary) = decks.partition { it.draft != null }
    return listOfNotNull(ordinary.takeIf { it.isNotEmpty() }?.let { null to it },
        drafts.takeIf { it.isNotEmpty() }?.let { "Draft decks" to it })
}
