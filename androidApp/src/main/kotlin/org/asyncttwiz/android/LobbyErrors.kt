package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

internal fun lobbyLifecycleInterruption(failure: Throwable, interrupted: Boolean): Boolean = interrupted &&
    (failure is kotlinx.coroutines.CancellationException || transientServiceFailure(failure))

internal fun lobbyError(failure: Throwable): String = when ((failure as? LobbyFailure)?.code) {
    "invalid_search" -> "Use 1–64 username characters without control characters."
    "invalid_name" -> "Enter a game name."
    "contact_required" -> "Add this player to your contacts first."
    "authentication_required" -> "Please sign in again. Your unfinished request is kept."
    "try_later" -> "Too many requests. Wait a moment and retry."
    "request_conflict" -> "This request could not be reused. Close the dialog and try again."
    "not_configured" -> "The game service is not configured in this build."
    "basic_definitions_unavailable" -> "The five English M21 basic lands are missing. Update card records and retry."
    "deck_not_submitted" -> "Submit your pod deck before challenging."
    "opponent_not_member", "not_found" -> "This pod or player is no longer available."
    "invalid_opponent" -> "Choose another member of this pod."
    "invalid_basic_definitions" -> "Basic land definitions were refused. Update card records and retry."
    "invalid_deck" -> "This deck exceeds the game's 1,000-card limit."
    else -> "Could not reach the game service. Your input is kept. Check your connection and retry."
}
