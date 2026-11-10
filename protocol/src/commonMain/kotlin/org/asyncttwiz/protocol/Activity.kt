package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Account activity, never board state, covered-card names, read receipts or player progress.
 * Only the authenticated feed carries display content; ActivityPushHint stays opaque. */
/** [GAME_REMOVAL] and [POD_REMOVAL]: the cleanup's warning 3 days before a game in play or a pod is deleted for lack of
 * activity (game history spec §4.5). A pod chat message notifies no one (DRF-170: the in-app dots tell it). */
@Serializable enum class ActivityKind { INVITATION, GAME, POD_INVITATION, POD_YOUR_PICK, POD_DRAFT_COMPLETE, POD_DECKS_READY, POD_DRAFT_CANCELLED, POD_DESTROYED, POD_AUTO_START_OFF,
    GAME_REMOVAL, POD_REMOVAL }
/** gameId is the pod ID for POD_* kinds; identity is the draft attempt for
 * POD_YOUR_PICK / POD_DRAFT_COMPLETE. The existing game hint shape is unchanged. [resourceName]: the pod's name for
 * POD_* kinds, the game's for GAME_REMOVAL and INVITATION (a pod challenge's: its pod's). */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class ActivityHint(val activityId: String, val kind: ActivityKind, val gameId: String,
    val identity: String, val authorId: String? = null, val notice: GameNoticeContent? = null, val resourceName: String? = null,
    /** POD_* kinds (I-D57): the set or cube the pod drafts. Omitted when unknown. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val sourceName: String? = null,
    /** GAME_REMOVAL: the recipient's opponent in the game; INVITATION and POD_INVITATION: who invited them. Omitted otherwise. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val opponentName: String? = null,
    /** INVITATION of a pod challenge (DRF-123): the pod. Omitted otherwise. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val podId: String? = null,
    /** INVITATION of a pod challenge sent while the recipient is still building: it can be accepted once they submit
     * their deck (LOB-025). Omitted when false. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val held: Boolean = false)
/** Authenticated projection only; never included in the FCM data hint. [reminder]: the one-off
 * "opponent is waiting" notice after their moves went unanswered (omitted when false). [gameOver]: the opponent
 * accepted the recipient's result suggestion, which was this outcome from the recipient's (its author's) side
 * (omitted otherwise). [opponentReady]: the opponent is Ready while the recipient is still in the opening "Mulliganing"
 * status (LOB-114; omitted when false). [conceded]: the opponent conceded, accepting their own suggested loss (CR 104.3a,
 * I-D52); [gameOver] is then that loss, from the opponent's side (omitted when false). */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
/** [byRules] (I-D53): the game ended by the rules and the opponent confirmed it; [gameOver] is then the result from the
 * recipient's side. */
@Serializable data class GameNoticeContent(val gameName: String, val opponentName: String,
    val lastEventAt: String, val yourMove: Boolean, val newMessage: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val reminder: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val gameOver: SuggestedResult? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val opponentReady: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val conceded: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val byRules: Boolean = false,
    /** I-D56 §8 (LOB-116): the opponent's real answer differed from what the recipient assumed; their later actions
     * were taken back. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val branched: Boolean = false,
    /** I-D57: where the recipient would resume: the turn and step at their own local head, never the main line's head. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val resumeAt: ResumePoint? = null,
    /** I-D57: the recipient's own deck in this game. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val deckName: String? = null)

/** A point of a game as a notification names it (I-D57): [turn] number (0: the opening, both players Mulliganing),
 * whether it is the recipient's own turn ([yours]) and the [step] (null in the opening). */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class ResumePoint(val turn: Int, val yours: Boolean,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val step: TurnPosition? = null)
@Serializable data class ActivityPage(val entries: List<ActivityHint>, val nextCursor: String,
    val retentionFloor: String, val summaryReset: Boolean, val hasMore: Boolean,
    val readyNotices: List<ActivityHint>? = null)
/** Games now waiting for the account (GET /activity/waiting): in play, the opponent made the latest event and
 * left the move with the account, and the opponent is idle by the push rule. Each [ActivityHint] carries the
 * latest event's activity and identity and a waiting-reminder [GameNoticeContent]. */
/** What waits for the account: GAME hints, then POD_YOUR_PICK / POD_DRAFT_COMPLETE hints of pods waiting for its pick or build. */
@Serializable data class WaitingGames(val games: List<ActivityHint>)
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
