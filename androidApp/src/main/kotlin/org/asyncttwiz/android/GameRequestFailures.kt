package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Player-facing text for a refused original request, by its GameResult reason. */
internal fun refusalText(reason: String?): String = when (reason) {
    "sourceChanged" -> "This saved deck changed. Choose it again to use the current version."
    "sourceUnavailable" -> "This saved deck is no longer available. Choose another deck."
    "deckRequired" -> "Select your deck to get ready."
    "mainTooSmall" -> "Main must contain at least 40 cards including basics."
    "sideboardLimit" -> "Side exceeds this pod's sideboard limit. Move cards to Main."
    "podDeckFixed" -> "This game's deck is fixed to your submitted pod deck."
    "automatumUnsupported" -> "Automatum can't play every card of this deck yet. Choose another deck."
    "starterRequired" -> "Choose the starting player to get ready."
    // LOB-135
    "notHost" -> "Only the player who created this game can change its settings."
    "branched" ->"Your opponent went back to an earlier point — your last action wasn't applied."
    // SYS-053
    RuleRefusals.REAL_CARD_ON_STACK -> "A card can't go into the Stack itself."
    RuleRefusals.OFF_BATTLEFIELD -> "That card isn't on the battlefield."
    // SYS-062
    RuleRefusals.FLOW_OPEN -> "Automatum is in the middle of something. Finish it before going back."
    // SYS-069
    RuleRefusals.AUTOMATUM_ONLY -> "The game changes only by the rules."
    else -> "This game has changed. Refresh and try again."
}
