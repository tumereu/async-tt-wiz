package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/**
 * LOB-072, LOB-276 (owner, 2026-11-26): the kinds of notification, each an Android notification channel of its own and a
 * switch in Settings → Notifications kept on the server for the account ([NotificationSettings]). [id] names it on the wire,
 * in the database and as the Android channel's id; [label] and [explanation] are its Settings row's (and the channel's) words.
 */
sealed class NotificationKind(val id: String, val label: String, val explanation: String) {
    /** A game waits for you (LOB-073): "Your move", "<opponent>'s move", "<opponent> is ready" (LOB-114), a game you are Ready
     * for starting (LOB-077). */
    data object YourMove : NotificationKind("your_move", "Your move", "When a game waits for you")
    /** "<opponent> is waiting for you" (LOB-075) and the app's own reminders of games and pods waiting for you (LOB-076). */
    data object Reminders : NotificationKind("reminders", "Reminders", "When a game or draft has waited for you a while")
    /** A new message in a game's or a pod's chat (LOB-277). */
    data object Chat : NotificationKind("chat", "Chat", "New messages in games and pods")
    /** Game and pod invitations, pod challenges (LOB-185, DRF-122). */
    data object Invitations : NotificationKind("invitations", "Invitations", "Games, pods and challenges")
    /** The draft's notices: your pick, draft complete, decks ready, draft cancelled, auto-start off (DRF-136, DRF-137). */
    data object Draft : NotificationKind("draft", "Draft", "Picks, decks ready and other draft news")
    /** A game's end (LOB-111), your later actions taken back (LOB-116), the cleanup's warnings (LOB-134), a pod destroyed or
     * you removed from one (DRF-137, DRF-308). */
    data object GameOver : NotificationKind("game_over", "Game over & warnings",
        "Results, actions taken back, removals")

    companion object {
        /** Every kind, in the order Settings lists them. */
        val all: List<NotificationKind> get() = listOf(YourMove, Reminders, Chat, Invitations, Draft, GameOver)

        fun of(id: String): NotificationKind? = all.firstOrNull { it.id == id }

        /** The kind of the notification [hint] shows; [reminder]: the app's own reminder of it (LOB-076). */
        fun of(hint: ActivityHint, reminder: Boolean = false): NotificationKind {
            val notice = hint.notice
            return when {
                hint.kind == ActivityKind.CHAT || hint.kind == ActivityKind.POD_CHAT -> Chat
                hint.kind == ActivityKind.INVITATION || hint.kind == ActivityKind.POD_INVITATION -> Invitations
                hint.kind == ActivityKind.GAME_REMOVAL || hint.kind == ActivityKind.POD_REMOVAL ||
                    hint.kind == ActivityKind.POD_DESTROYED || hint.kind == ActivityKind.POD_MEMBER_REMOVED -> GameOver
                reminder -> Reminders
                hint.kind == ActivityKind.GAME && notice != null && (notice.gameOver != null || notice.branched) -> GameOver
                hint.kind == ActivityKind.GAME && notice?.reminder == true -> Reminders
                hint.kind == ActivityKind.GAME -> YourMove
                else -> Draft
            }
        }
    }
}

/**
 * LOB-276 (owner, 2026-11-26): which kinds of notification ([NotificationKind]) the account gets, on every phone and browser;
 * each on unless switched off, but Chat off unless switched on (owner, 2026-11-27). The server sends no push of a kind that is
 * off and tells no device to show one.
 */
@Serializable
data class NotificationSettings(val yourMove: Boolean = true, val reminders: Boolean = true, val chat: Boolean = false,
    val invitations: Boolean = true, val draft: Boolean = true, val gameOver: Boolean = true) {
    fun allows(kind: NotificationKind): Boolean = when (kind) {
        NotificationKind.YourMove -> yourMove
        NotificationKind.Reminders -> reminders
        NotificationKind.Chat -> chat
        NotificationKind.Invitations -> invitations
        NotificationKind.Draft -> draft
        NotificationKind.GameOver -> gameOver
    }

    /** Whether the notification [hint] shows may show ([NotificationKind.of]). */
    fun allows(hint: ActivityHint, reminder: Boolean = false): Boolean = allows(NotificationKind.of(hint, reminder))

    fun with(kind: NotificationKind, on: Boolean): NotificationSettings = when (kind) {
        NotificationKind.YourMove -> copy(yourMove = on)
        NotificationKind.Reminders -> copy(reminders = on)
        NotificationKind.Chat -> copy(chat = on)
        NotificationKind.Invitations -> copy(invitations = on)
        NotificationKind.Draft -> copy(draft = on)
        NotificationKind.GameOver -> copy(gameOver = on)
    }

    /** The kinds switched off, for the logs. */
    val off: List<NotificationKind> get() = NotificationKind.all.filterNot(::allows)

    companion object {
        /** An account's kinds until it switches one: all on but Chat (LOB-276, owner 2026-11-27). */
        val DEFAULT = NotificationSettings()
    }
}
