package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Account activity, never board state, covered-card names, read receipts or player progress.
 * Only the authenticated feed carries display content; ActivityPushHint stays opaque. */
@Serializable enum class ActivityKind { INVITATION, GAME, POD_INVITATION, POD_YOUR_PICK, POD_DRAFT_COMPLETE, POD_DECKS_READY, POD_DRAFT_CANCELLED, POD_DESTROYED, POD_AUTO_START_OFF }
/** gameId is the pod ID for POD_* kinds; identity is the draft attempt for
 * POD_YOUR_PICK / POD_DRAFT_COMPLETE. The existing game hint shape is unchanged. */
@Serializable data class ActivityHint(val activityId: String, val kind: ActivityKind, val gameId: String,
    val identity: String, val authorId: String? = null, val notice: GameNoticeContent? = null, val resourceName: String? = null)
/** Authenticated projection only; never included in the FCM data hint. [reminder]: the one-off
 * "opponent is waiting" notice after their moves went unanswered (omitted when false). */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class GameNoticeContent(val gameName: String, val opponentName: String,
    val lastEventAt: String, val yourMove: Boolean, val newMessage: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val reminder: Boolean = false)
@Serializable data class ActivityPage(val entries: List<ActivityHint>, val nextCursor: String,
    val retentionFloor: String, val summaryReset: Boolean, val hasMore: Boolean,
    val readyNotices: List<ActivityHint>? = null)
@Serializable data class GamePresence(val gameId: String, val presenceId: String,
    val didSomething: Boolean = false, val left: Boolean = false)
@Serializable data class RegisterInstallation(val installationId: String, val binding: String, val pushToken: String? = null)
@Serializable data class ActivityRegistration(val generation: String)
@Serializable data class ActivityPushHint(val generation: String, val gameId: String, val activityId: String,
    val identity: String)

/** Authorized navigation result, not a command or a board/covered-card preview. */
@Serializable data class ActivityTarget(val gameId: String, val destination: ActivityDestination,
    val invitationId: String? = null, val playId: String? = null, val groupId: String? = null,
    val boundaryNodeId: String? = null)
@Serializable enum class ActivityDestination { INVITATION, GROUP, CURRENT, UNAVAILABLE, POD }

/** Local durable delivery window codec; not a game history/read-progress record. */
@Serializable data class PendingActivity(val hint: ActivityHint, val firstAt: Long, val dueAt: Long)

object ActivityWire {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    fun ordinal(value: String): Long? = value.takeIf { it.matches(Regex("0|[1-9][0-9]{0,18}")) }?.toLongOrNull()
    fun valid(input: RegisterInstallation) = DeckValidation.uuid(input.installationId) && DeckValidation.uuid(input.binding) &&
        (input.pushToken == null || (input.pushToken.length in 1..4096 && input.pushToken.all { it.code in 33..126 }))
    fun valid(hint: ActivityPushHint) = DeckValidation.uuid(hint.generation) && DeckValidation.uuid(hint.gameId) &&
        ordinal(hint.activityId) != null && DeckValidation.uuid(hint.identity)
}
