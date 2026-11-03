package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Player-facing text for a refused Undo/Redo/Automatum flow revert original; null for any other action. */
internal fun historyControlFailure(action: GameAction?, reason: String?): String? {
    // I-D19: switching Automatum off still closes the flow when its revert is refused (I-D24).
    if (action == GameAction.RevertFlow) return if (reason == "noFlow") "Automatum had already finished its step."
        else "Automatum's unfinished step couldn't be taken back; it was closed as it is."
    // finalreview-sync #6: switching off (or clearing) during a flow is refused only as any play would be
    if (action is GameAction.ExitFlow) return "Can't " + (if (action.then == MtgAction.ClearRecords) "clear Automatum data" else
        "switch Automatum off") + ": " + refusalText(reason).replaceFirstChar { it.lowercase() }
    val verb = when (action) { GameAction.Undo -> "undo"; GameAction.Redo -> "redo"; else -> return null }
    val explanation = when (reason) {
        "tooFarBack" -> "your opponent has made too many moves since your last one."
        "noCandidate" -> "there is no action to $verb. Keep playing."
        "forkBoundary" -> "that would change an existing branch. Use History to choose a continuation."
        "suffixUnavailable", "workLimit" -> "that history is too large or branched to check safely. Use History to review it, or keep playing."
        "referenceBlocked" -> "a later event refers to that action. Use History to review it, or keep playing."
        "replayBlocked", "targetTransitionBlocked" -> "that would change another recorded transition. Use History to review it, or keep playing."
        "missingAnchor" -> "the original place for that action is no longer available. Keep playing or open History."
        "lifecycleEnded", "inapplicable" -> "the original play is no longer active. Return to the games list."
        "accessDenied" -> "this game is no longer available to this account. Return to the games list."
        "historyUnavailable", "missingReference" -> "the required history is unavailable. Reconnect to synchronize it."
        else -> "the request could not be used. Keep playing or open History."
    }
    return "Can't $verb: $explanation"
}

/** Player-facing text for any other refused original request, by its GameResult reason. */
internal fun refusalText(reason: String?): String = when (reason) {
    "sourceChanged" -> "This saved deck changed. Choose it again to use the current version."
    "sourceUnavailable" -> "This saved deck is no longer available. Choose another deck."
    "deckRequired" -> "Select your deck to get ready."
    "mainTooSmall" -> "Main must contain at least 40 cards including basics."
    "sideboardLimit" -> "Side exceeds this pod's sideboard limit. Move cards to Main."
    "podDeckFixed" -> "This game's deck is fixed to your submitted pod deck."
    "automatumUnsupported" -> "Automatum can't play every card of this deck yet. Choose another deck."
    "starterRequired" -> "Choose the starting player to get ready."
    "branched" -> "Your opponent went back to an earlier point — your last action wasn't applied."
    // SYS-053
    RuleRefusals.REAL_CARD_ON_STACK -> "A card can't go into the Stack itself. Put it on the Stack as an entry instead; the card stays where it is."
    RuleRefusals.OFF_BATTLEFIELD -> "That card isn't on the battlefield. Damage, notes and until-end-of-turn marks go only on battlefield cards."
    // SYS-059
    RuleRefusals.AUTOMATUM_MANUAL_STACK -> "Automatum can't take over a Stack with hand-made entries. Resolve or remove them first."
    RuleRefusals.AUTOMATUM_TEMP_PILES -> "Automatum can't take over Temp piles. Empty them first."
    // SYS-062
    RuleRefusals.FLOW_OPEN -> "Automatum is in the middle of something. Finish it or switch Automatum off before going back."
    else -> "This game has changed. Refresh and try again."
}
