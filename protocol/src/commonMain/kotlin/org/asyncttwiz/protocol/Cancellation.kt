package org.asyncttwiz.protocol

/** Round-bound setup cancellation, independent of invitation attempts and opponent presence. */
object CancellationRules {
    fun rejection(game: GameSummary, actor: String, scope: GameScope): String? = when {
        game.lifecycle != GameLifecycle.LOBBY -> "lifecycleEnded"
        scope.gameId != game.id || scope.preparationId != game.preparationRound ||
            scope.invitationId != null || scope.playId != null -> "inapplicable"
        actor != game.inviter.accountId &&
            (actor != game.recipient.accountId || game.invitationState != InvitationState.ACCEPTED) -> "accessDenied"
        else -> null
    }
}
