package org.asyncttwiz.protocol

/**
 * A notification's words (LOB-072, LOB-111, LOB-114, LOB-116, LOB-134, DRF-136, DRF-137), shared by the Android app, which
 * posts its notifications itself, and the server, which sends a browser's ready-made (WEB-052).
 */
object ActivityTexts {
    /** A game notification's title: "<game> · vs <opponent>". */
    fun gameTitle(content: GameNoticeContent) = "${content.gameName} · vs ${content.opponentName}"

    /** A game notification's text: whose move it is (or the game's end, a reminder, a branch), where you would resume
     * (I-D57) and " · new message" after unread chat. */
    fun gameText(content: GameNoticeContent): String =
        (content.gameOver?.let { if (content.byRules) "Game over · ${byRulesResult(it)}" // I-D53
            else if (content.conceded) "Game over · ${content.opponentName} conceded"
            else "Game over · ${content.opponentName} accepted ${gameOverResult(it)}" }
            ?: ((if (content.reminder) "${content.opponentName} is waiting for you"
            else if (content.opponentReady) "${content.opponentName} is ready" // LOB-114
            else if (content.branched) "${content.opponentName} responded differently · your later actions were taken back" // LOB-116
            else if (content.yourMove) "Your move"
            else "${content.opponentName}'s move") +
                // I-D57: where you would resume (the point you last saw, never further)
                content.resumeAt?.let { " · ${resumePoint(it, content.opponentName)}" }.orEmpty())) +
            (if (content.newMessage) " · new message" else "")

    /** I-D57: the point a game notification says you would resume at, in the table's own words (the turn square, TRN-007):
     * "Turn 5, your turn, Upkeep" or "Turn 5, <opponent>'s turn, End step"; the opening is "Mulliganing" (TRN-027). */
    fun resumePoint(point: ResumePoint, opponentName: String): String =
        if (point.turn == 0) "Mulliganing"
        else "Turn ${point.turn}, ${if (point.yours) "your" else "$opponentName's"} turn" + point.step?.let { ", ${it.label}" }.orEmpty()

    /** Game history §4.5: the cleanup's warning 3 days before a game in play is deleted: "<game> · vs <opponent>" and
     * "Removed in 3 days if nobody plays". */
    fun gameRemoval(hint: ActivityHint): Pair<String, String> =
        "${hint.resourceName ?: "Game"} · vs ${hint.opponentName ?: "your opponent"}" to "Removed in 3 days if nobody plays"

    /** I-D57: a pod notification's text: the set or cube the pod drafts. */
    fun podDetail(hint: ActivityHint): String? = hint.sourceName?.takeIf { pod(hint.kind) }

    /** LOB-252: the line saying a game's creator lends you decks: "<name> lends you decks". */
    fun lendsDecks(name: String) = "$name lends you decks"

    /** An invitation's or pod notification's text under its title: a game invitation's lent decks (LOB-252), a pod's set
     * or cube ([podDetail]). */
    fun detail(hint: ActivityHint): String? =
        if (hint.kind == ActivityKind.INVITATION && hint.lendsDecks && hint.podId == null) lendsDecks(hint.opponentName ?: "Your opponent")
        else podDetail(hint)

    /** I-D53: the rules' result, from the notified player's side. */
    fun byRulesResult(outcome: SuggestedResult) = when (outcome) {
        SuggestedResult.WIN -> "You won"
        SuggestedResult.LOSS -> "You lost"
        SuggestedResult.DRAW -> "Draw"
    }

    /** LOB-111: the accepted suggestion, from its author's (the notified player's) side. */
    private fun gameOverResult(outcome: SuggestedResult) = when (outcome) {
        SuggestedResult.WIN -> "your win"
        SuggestedResult.LOSS -> "your loss"
        SuggestedResult.DRAW -> "the draw"
    }

    /**
     * DRF-137, LOB-072: an invitation's notification: "<name> invited you · <game>"; a pod challenge "<pod>: <name> challenged
     * you", while it waits for your deck with " · submit your deck to accept"; a pod invitation "<name> invited you to <pod>"
     * ([podName]: the pod's name when the hint has none). Without the inviter's name, "New invitation" or "New pod invitation".
     */
    fun invitation(hint: ActivityHint, podName: String? = null): String {
        val name = hint.opponentName
        return when {
            hint.kind == ActivityKind.POD_INVITATION -> name?.let { who -> (hint.resourceName ?: podName)?.let { "$who invited you to $it" } }
                ?: podActivity(ActivityKind.POD_INVITATION)
            name == null -> "New invitation"
            hint.podId != null -> "${hint.resourceName ?: "Pod"}: $name challenged you" + if (hint.held) " · submit your deck to accept" else ""
            else -> "$name invited you" + hint.resourceName?.let { " · $it" }.orEmpty()
        }
    }

    /** A pod notification's title (DRF-136, DRF-137, LOB-134), [name] the pod's. */
    fun podActivity(kind: ActivityKind, name: String? = null): String = when (kind) {
        ActivityKind.POD_INVITATION -> "New pod invitation"
        ActivityKind.POD_YOUR_PICK -> name?.let { "Your pick — $it" } ?: "Your pick"
        ActivityKind.POD_DECKS_READY -> name?.let { "Decks ready — $it" } ?: "Decks ready"
        ActivityKind.POD_DRAFT_COMPLETE -> "Draft complete: build your deck"
        ActivityKind.POD_DRAFT_CANCELLED -> "Draft cancelled"
        ActivityKind.POD_DESTROYED -> "Pod destroyed"
        // DRF-308: the creator removed you in setup
        ActivityKind.POD_MEMBER_REMOVED -> "Removed from ${name ?: "a pod"}"
        ActivityKind.POD_AUTO_START_OFF -> name?.let { "Auto-start off — $it" } ?: "Auto-start off"
        // Game history §4.5: the cleanup's warning 3 days before a stalled pod is deleted
        ActivityKind.POD_REMOVAL -> "${name ?: "Pod"}: Removed in 3 days if nobody picks or plays"
        else -> "Game activity"
    }

    /**
     * LOB-277: a chat's notification: a game's "<game> · vs <author>", a pod's "<pod>: <author>" ([podName]: the pod's name
     * when the hint has none), over the newest message, after "<n> new messages · " while more than one is unread.
     */
    fun chat(hint: ActivityHint, podName: String? = null): Pair<String, String> {
        val author = hint.opponentName ?: "Your opponent"
        val title = if (hint.kind == ActivityKind.POD_CHAT) "${hint.resourceName ?: podName ?: "Pod"}: $author"
            else "${hint.resourceName ?: "Game"} · vs $author"
        val message = hint.chat
        val text = message?.text ?: "New message"
        val unread = message?.unread ?: 1
        return title to if (unread > 1) "$unread new messages · $text" else text
    }

    /** A notification's title and text as the Android app posts them (its ActivityNotifications): an invitation's, a chat's,
     * a pod's, a removal warning's or a game's. */
    fun notification(hint: ActivityHint, podName: String? = null): Pair<String, String?> {
        val game = hint.notice
        return when {
            hint.kind == ActivityKind.CHAT || hint.kind == ActivityKind.POD_CHAT -> chat(hint, podName)
            hint.kind == ActivityKind.INVITATION || hint.kind == ActivityKind.POD_INVITATION -> invitation(hint, podName) to detail(hint)
            pod(hint.kind) -> podActivity(hint.kind, hint.resourceName ?: podName) to podDetail(hint)
            hint.kind == ActivityKind.GAME_REMOVAL -> gameRemoval(hint)
            game != null -> gameTitle(game) to gameText(game)
            else -> "Game activity" to null
        }
    }

    /** Whether [kind] is about a pod (the hint's gameId is the pod's). */
    fun pod(kind: ActivityKind): Boolean = kind.name.startsWith("POD_")
}
