package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Delivery hints only. No names, content, board state, read receipt or player progress. */
@Serializable enum class ActivityKind { INVITATION, GAME, POD_INVITATION, POD_YOUR_PICK, POD_DRAFT_COMPLETE, POD_DRAFT_CANCELLED, POD_DESTROYED }
/** gameId is the pod ID for POD_* kinds; the existing game hint shape is unchanged. */
@Serializable data class ActivityHint(val activityId: String, val kind: ActivityKind, val gameId: String,
    val identity: String, val authorId: String? = null)
@Serializable data class ActivityPage(val entries: List<ActivityHint>, val nextCursor: String,
    val retentionFloor: String, val summaryReset: Boolean, val hasMore: Boolean)
@Serializable data class RegisterInstallation(val installationId: String, val binding: String, val pushToken: String? = null)
@Serializable data class ActivityRegistration(val generation: String)
@Serializable data class ActivityPushHint(val generation: String, val gameId: String, val activityId: String,
    val identity: String)

/** Authorized navigation result, not a command or a board/covered-card preview. */
@Serializable data class ActivityTarget(val gameId: String, val destination: ActivityDestination,
    val invitationId: String? = null, val playId: String? = null, val groupId: String? = null,
    val boundaryNodeId: String? = null)
@Serializable enum class ActivityDestination { INVITATION, GROUP, CURRENT, UNAVAILABLE }

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
