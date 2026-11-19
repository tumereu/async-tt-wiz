package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** LOB-260: why a Ready of a Random game is refused while too few lent decks can be dealt. */
const val NO_DECKS_TO_DEAL = "There aren't enough lent decks Automatum can play to deal one to each player."

/** Player-facing text for a refused original request, by its GameResult reason. */
fun refusalText(reason: String?): String = when (reason) {
    "sourceChanged" -> "This saved deck changed. Choose it again to use the current version."
    "sourceUnavailable" -> "This saved deck is no longer available. Choose another deck."
    "deckRequired" -> "Select your deck to get ready."
    "mainTooSmall" -> "Main must contain at least 40 cards including basics."
    "sideboardLimit" -> "Side exceeds this pod's sideboard limit. Move cards to Main."
    "podDeckFixed" -> "This game's deck is fixed to your submitted pod deck."
    "automatumUnsupported" -> "Automatum can't play every card of this deck yet. Choose another deck."
    "starterRequired" -> "Choose the starting player to get ready."
    // TRN-065, LOB-046
    "resultNotSeen" -> "Both players tap OK on the last game's result first."
    // LOB-135
    "notHost" -> "Only the player who created this game can change its settings."
    // LOB-249, LOB-250, LOB-254, LOB-256, LOB-260
    "notLent" -> "Only the lent decks can be used in this game."
    "sameDeckTaken" -> "Both players can't use the same deck in this game. Choose another deck."
    "decksDealt" -> "In this game the decks are dealt at random when it starts."
    "borrowedDeckShown" -> "A borrowed deck always shows to both players."
    "noDecksToDeal" -> NO_DECKS_TO_DEAL
    "branched" ->"Your opponent went back to an earlier point — your last action wasn't applied."
    // SYS-053
    RuleRefusals.REAL_CARD_ON_STACK -> "A card can't go into the Stack itself."
    RuleRefusals.OFF_BATTLEFIELD -> "That card isn't on the battlefield."
    // SYS-062
    RuleRefusals.FLOW_OPEN -> "Automatum is in the middle of something. Finish it before going back."
    else -> "This game has changed. Refresh and try again."
}
