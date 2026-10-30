package org.asyncttwiz.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Semantic presentation categories, not request types or rules-engine interpretations. */
@Serializable enum class HistoryCategory(val key: String) {
    T("tap"), M("move"), I("stack"), E("edit"), C("creation"), K("combat"), L("stackLayout"),
    V("view"), H("shuffle"), F("closure"), R("random"), P("ping"), B("backward"), S("ending"), O("leading"), U("untap")
}

/** D-299 ranks. The representative retains the event's literal icon, never a rank-label icon. Wire names are the
 * constant names; a name this build does not know (a newer server) decodes as [UNKNOWN] instead of failing. */
@Serializable(with = HistorySignificanceSerializer::class)
enum class HistorySignificance(val category: HistoryCategory, val rank: Int, val anchor: Boolean = true) {
    ENDING_SUGGESTION(HistoryCategory.S, 10), STACK_ENTRY(HistoryCategory.I, 20), STACK_REMOVAL(HistoryCategory.I, 20),
    EXPLICIT_DISCLOSURE(HistoryCategory.V, 30), TOKEN_CLONE_CREATION(HistoryCategory.C, 40),
    COMBAT_MEMBERSHIP(HistoryCategory.K, 50), COMBAT_AREA_CREATION(HistoryCategory.K, 60),
    CROSS_ZONE_MOVE(HistoryCategory.M, 70), SHUFFLE(HistoryCategory.H, 80), TOKEN_REMOVAL(HistoryCategory.E, 90),
    FACE_COVER_EDIT(HistoryCategory.E, 100), LIFE_PLAYER_COUNTER_EDIT(HistoryCategory.E, 110),
    CARD_CORNER_COUNTER_EDIT(HistoryCategory.E, 120), OWNER_EDIT(HistoryCategory.E, 130),
    LINK_EDIT(HistoryCategory.E, 140), CARD_TEXT_NOTE_EDIT(HistoryCategory.E, 150),
    COMBAT_LAYOUT(HistoryCategory.K, 160), STACK_LAYOUT(HistoryCategory.L, 170), SAME_ZONE_PLACEMENT(HistoryCategory.M, 180),
    COMBAT_CLOSURE(HistoryCategory.F, 10, false), VIEW_REVEAL_DISMISSAL(HistoryCategory.F, 20, false),
    DIE_COIN_RUN(HistoryCategory.R, 30, false), ORIENTATION(HistoryCategory.T, 40, false),
    TURN_TRANSITION(HistoryCategory.B, 50, false), PHASE_STEP_TRACKING(HistoryCategory.B, 60, false),
    EVENT_COMMENT(HistoryCategory.B, 70, false), PING(HistoryCategory.P, 80, false),
    ATTENTION_PASS(HistoryCategory.B, 90, false),
    /** Rules v32: a step passed inside a multi-step advance ([StepRun]); the run's own final event represents it. */
    STEP_PASSED(HistoryCategory.B, 95, false),
    /** Decoded from a name this build does not know: it never anchors or represents a group and joins like a comment. */
    UNKNOWN(HistoryCategory.B, 100, false)
}

internal object HistorySignificanceSerializer : KSerializer<HistorySignificance> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.asyncttwiz.protocol.HistorySignificance", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: HistorySignificance) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): HistorySignificance = decoder.decodeString().let { name ->
        HistorySignificance.entries.firstOrNull { it.name == name } ?: HistorySignificance.UNKNOWN }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class HistorySemantic(val significance: HistorySignificance, val icon: HistoryIcon, val key: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val panelSession: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val isolated: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val untap: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val moveIntoLibrary: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val rulesVersion: Int = 1,
    /** Entry created by this event, or the exact projection receiving a text mark/note. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val stackEntryId: String? = null,
    /** Rules v29 step into Declare blockers: joins only the author's attacker declaration and closes the group. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val endsGroup: Boolean = false) {
    init {
        // An unknown (newer) significance may carry a key this build cannot judge; decoding it must not fail.
        require(significance == HistorySignificance.UNKNOWN ||
            (significance.category in setOf(HistoryCategory.M, HistoryCategory.H)) == (key != null))
    }
    val category: HistoryCategory get() = if (untap) HistoryCategory.U else significance.category
}
@Serializable data class HistoryRepresentative(val nodeId: String, val semantic: HistorySemantic)

object HistoryGrouping {
    /** From this gameplay rules version a step change into Declare blockers ends a history group: it joins only the
     * author's preceding attacker declaration, otherwise stands alone, and nothing joins the group after it. */
    const val BLOCKERS_BOUNDARY_RULES_VERSION = 29
    /** From this gameplay rules version closing a card panel (with its rest moves and shuffle) is its own history step. */
    const val PANEL_CLOSE_STEP_RULES_VERSION = 29
    /** From this gameplay rules version a multi-step advance records the steps it passes ([StepRun]): each is
     * [HistorySignificance.STEP_PASSED], joins like any step change and never ends a group (not even Declare
     * blockers), so the whole advance stays one group. */
    const val STEP_RUN_RULES_VERSION = StepRun.RULES_VERSION
    /** Search entry conveniences share its compact prefix; no invocation/history lookup is needed. */
    internal fun classify(action: PlayAction, board: BoardState, author: String, transition: GameplayTransition): HistorySemantic {
        val semantic = classify(action, board.containers, transition)
        if (action !is PlayAction.ResetCorner && action !is PlayAction.ClearCardCounter && action !is PlayAction.SetTapped) return semantic
        val search = board.contentsDisplays.firstOrNull { it.author == author && it.search && it.panelVersion >= 2 }
        return if (search == null) semantic else semantic.copy(panelSession = search.id)
    }
    /** Exhaustive mapping of the actual implemented catalog. New tools must add their exact mapping here.
     * Zone identity comes from accepted containers, not labels, card text, timers or invocation proximity. */
    fun classify(action: PlayAction, containers: List<PlayContainer> = emptyList(),
        transition: GameplayTransition? = null): HistorySemantic {
        val semantic = when (action) {
        is PlayAction.SuggestResult -> HistorySemantic(HistorySignificance.ENDING_SUGGESTION, HistoryIcon.FLAG)
        is PlayAction.DeclineResult, is PlayAction.WithdrawResult -> HistorySemantic(HistorySignificance.ENDING_SUGGESTION, HistoryIcon.CLOSE)
        PlayAction.PassAttention -> HistorySemantic(HistorySignificance.ATTENTION_PASS, HistoryIcon.OUTLINED_FLAG)
        is PlayAction.SetTapped -> HistorySemantic(HistorySignificance.ORIENTATION, HistoryIcon.ROTATE_90_DEGREES_CW)
        is PlayAction.SetLife, is PlayAction.AdjustLife -> HistorySemantic(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, HistoryIcon.FAVORITE)
        is PlayAction.SetPlayerCounter, is PlayAction.AdjustPlayerCounter ->
            HistorySemantic(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, HistoryIcon.EXPOSURE_PLUS_1)
        PlayAction.EndTurn -> HistorySemantic(HistorySignificance.TURN_TRANSITION, HistoryIcon.OUTLINED_FLAG)
        PlayAction.NextStep -> HistorySemantic(if (transition?.fields?.any { it.field == GameplayField.TURN_PLAYER } == true)
            HistorySignificance.TURN_TRANSITION else HistorySignificance.PHASE_STEP_TRACKING, HistoryIcon.OUTLINED_FLAG)
        is PlayAction.ChooseStep -> HistorySemantic(if (action.through) HistorySignificance.STEP_PASSED
            else HistorySignificance.PHASE_STEP_TRACKING, HistoryIcon.OUTLINED_FLAG)
        is PlayAction.RollDie -> HistorySemantic(HistorySignificance.DIE_COIN_RUN, HistoryIcon.CASINO)
        is PlayAction.FlipCoin -> HistorySemantic(HistorySignificance.DIE_COIN_RUN, HistoryIcon.MONETIZATION_ON)
        is PlayAction.ShuffleLibrary -> HistorySemantic(HistorySignificance.SHUFFLE, HistoryIcon.SHUFFLE, action.containerId)
        is PlayAction.SetKeepTopRevealed -> HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE,
            HistoryIcon.VISIBILITY, isolated = true)
        is PlayAction.Mulligan -> HistorySemantic(HistorySignificance.SHUFFLE, HistoryIcon.SHUFFLE, action.libraryId)
        // Grouped like any cross-zone move into that Library.
        is PlayAction.MoveToLibraryRandom -> HistorySemantic(HistorySignificance.CROSS_ZONE_MOVE, HistoryIcon.OPEN_WITH, "random/${action.libraryId}")
        is PlayAction.ShowContents -> HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE,
            if (action.reveal) HistoryIcon.VISIBILITY else HistoryIcon.PAGEVIEW)
        is PlayAction.RevealHand -> HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE, HistoryIcon.VISIBILITY, isolated = true)
        is PlayAction.RandomHand -> HistorySemantic(if (action.result == HandRandomResult.REVEAL)
            HistorySignificance.EXPLICIT_DISCLOSURE else HistorySignificance.CROSS_ZONE_MOVE,
            if (action.result == HandRandomResult.REVEAL) HistoryIcon.VISIBILITY else HistoryIcon.OPEN_WITH,
            if (action.result == HandRandomResult.REVEAL) null else "${action.handId}/${action.result}", isolated = true)
        is PlayAction.DismissContents -> HistorySemantic(HistorySignificance.VIEW_REVEAL_DISMISSAL, HistoryIcon.CLOSE)
        is PlayAction.RevealContentsCard -> HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE, HistoryIcon.VISIBILITY, isolated = true)
        is PlayAction.ReorderContents -> HistorySemantic(HistorySignificance.SAME_ZONE_PLACEMENT, HistoryIcon.OPEN_WITH, action.displayId)
        is PlayAction.EventComment -> HistorySemantic(HistorySignificance.EVENT_COMMENT, HistoryIcon.CHAT_BUBBLE)
        // A status is a message to the other player, not a board edit: it joins the author's group, never leads it.
        is PlayAction.SetStatus -> HistorySemantic(HistorySignificance.EVENT_COMMENT, HistoryIcon.CHAT_BUBBLE)
        is PlayAction.SetTempPile -> HistorySemantic(HistorySignificance.SAME_ZONE_PLACEMENT, HistoryIcon.INBOX, "temp/${action.accountId}/${action.number}")
        is PlayAction.Ping, is PlayAction.Unping -> HistorySemantic(HistorySignificance.PING, HistoryIcon.ADS_CLICK)
        is PlayAction.SetDamage, is PlayAction.AdjustDamage, is PlayAction.SetEotCorner, is PlayAction.AdjustEotCorner,
        is PlayAction.SetCorner, is PlayAction.AdjustCorner, is PlayAction.ClearCardCounter ->
            HistorySemantic(HistorySignificance.CARD_CORNER_COUNTER_EDIT, HistoryIcon.EXPOSURE_PLUS_1)
        is PlayAction.ResetCorner -> HistorySemantic(HistorySignificance.CARD_CORNER_COUNTER_EDIT, HistoryIcon.SETTINGS_BACKUP_RESTORE)
        is PlayAction.SetCounter -> HistorySemantic(if (action.target == CounterTarget.PLAYER)
            HistorySignificance.LIFE_PLAYER_COUNTER_EDIT else HistorySignificance.CARD_CORNER_COUNTER_EDIT, HistoryIcon.EXPOSURE_PLUS_1)
        is PlayAction.AdjustCounter -> HistorySemantic(if (action.target == CounterTarget.PLAYER)
            HistorySignificance.LIFE_PLAYER_COUNTER_EDIT else HistorySignificance.CARD_CORNER_COUNTER_EDIT, HistoryIcon.EXPOSURE_PLUS_1)
        is PlayAction.SetNote, is PlayAction.SetEotNote, is PlayAction.SetProjectionNote -> HistorySemantic(HistorySignificance.CARD_TEXT_NOTE_EDIT, HistoryIcon.STICKY_NOTE_2)
        is PlayAction.SetOwner -> HistorySemantic(HistorySignificance.OWNER_EDIT, HistoryIcon.SWITCH_ACCOUNT)
        is PlayAction.SetFace -> HistorySemantic(HistorySignificance.FACE_COVER_EDIT, HistoryIcon.FLIP_CAMERA_ANDROID)
        is PlayAction.SetCovered -> HistorySemantic(HistorySignificance.FACE_COVER_EDIT,
            if (action.covered) HistoryIcon.FLIP else HistoryIcon.FLIP_TO_FRONT)
        is PlayAction.InspectFace -> HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE, HistoryIcon.VISIBILITY)
        is PlayAction.CreateToken -> HistorySemantic(HistorySignificance.TOKEN_CLONE_CREATION, HistoryIcon.ADD_BOX)
        is PlayAction.CloneToken -> HistorySemantic(HistorySignificance.TOKEN_CLONE_CREATION, HistoryIcon.CONTROL_POINT_DUPLICATE)
        is PlayAction.RemoveToken -> HistorySemantic(HistorySignificance.TOKEN_REMOVAL, HistoryIcon.DELETE)
        is PlayAction.Link, is PlayAction.LinkProjections -> HistorySemantic(HistorySignificance.LINK_EDIT, HistoryIcon.LINK)
        is PlayAction.RemoveLink, is PlayAction.RemoveProjectionLink -> HistorySemantic(HistorySignificance.LINK_EDIT, HistoryIcon.LINK_OFF)
        is PlayAction.Combat -> when (val combat = action.action) {
            CombatAction.Open -> HistorySemantic(HistorySignificance.COMBAT_AREA_CREATION, HistoryIcon.SWORDS)
            is CombatAction.Close -> HistorySemantic(HistorySignificance.COMBAT_CLOSURE, HistoryIcon.LAYERS_CLEAR)
            is CombatAction.Declare -> HistorySemantic(HistorySignificance.COMBAT_MEMBERSHIP, HistoryIcon.SWORDS)
            is CombatAction.Remove -> HistorySemantic(HistorySignificance.COMBAT_MEMBERSHIP, HistoryIcon.REMOVE_CIRCLE_OUTLINE)
            is CombatAction.SetAttackTarget -> HistorySemantic(HistorySignificance.COMBAT_MEMBERSHIP, HistoryIcon.SWORDS)
            is CombatAction.Place -> HistorySemantic(if (combat.expectedOccurrence == null ||
                combat.expectedOccurrence.role != combat.destination.role) HistorySignificance.COMBAT_MEMBERSHIP
                else HistorySignificance.COMBAT_LAYOUT, HistoryIcon.OPEN_WITH)
        }
        is PlayAction.Stack -> when (action.action) {
            is StackAction.MarkText, is StackAction.ToggleTextMark -> HistorySemantic(HistorySignificance.CARD_TEXT_NOTE_EDIT, HistoryIcon.FORMAT_INK_HIGHLIGHTER)
            is StackAction.AddEffect, is StackAction.CopyEffect -> HistorySemantic(HistorySignificance.STACK_ENTRY, HistoryIcon.LAYERS)
            is StackAction.SetController -> HistorySemantic(HistorySignificance.STACK_LAYOUT, HistoryIcon.SWAP_HORIZ)
            is StackAction.RemoveEffect -> HistorySemantic(HistorySignificance.STACK_REMOVAL, HistoryIcon.LAYERS_CLEAR)
            is StackAction.ReorderEffect -> HistorySemantic(HistorySignificance.STACK_LAYOUT, HistoryIcon.OPEN_WITH)
        }
        is PlayAction.MoveCard -> {
            val fromContainer = containers.single { it.id == action.source.containerId }
            val toContainer = containers.single { it.id == action.destination.containerId }
            val from = fromContainer.zone
            val to = toContainer.zone
            // A Temp pile shares the LIBRARY zone, but moving into or out of it is a cross-zone move.
            // Each numbered Temp pile is its own zone (rules v29).
            val sameZone = from == to && fromContainer.temp == toContainer.temp && (!fromContainer.temp || fromContainer.id == toContainer.id)
            when {
                action.removeStackEntryId != null -> HistorySemantic(HistorySignificance.STACK_REMOVAL, HistoryIcon.LAYERS_CLEAR)
                from == PlayZone.STACK && to == PlayZone.STACK ->
                    HistorySemantic(HistorySignificance.STACK_LAYOUT, HistoryIcon.OPEN_WITH)
                from == PlayZone.STACK -> HistorySemantic(HistorySignificance.STACK_REMOVAL, HistoryIcon.LAYERS)
                to == PlayZone.STACK -> HistorySemantic(HistorySignificance.STACK_ENTRY, HistoryIcon.LAYERS)
                else -> HistorySemantic(if (sameZone) HistorySignificance.SAME_ZONE_PLACEMENT else HistorySignificance.CROSS_ZONE_MOVE,
                    if (from == PlayZone.LIBRARY && !fromContainer.temp && to == PlayZone.HAND && action.source.edge == MoveSourceEdge.TOP)
                        HistoryIcon.DOWNLOAD else HistoryIcon.OPEN_WITH, "${action.source.containerId}/${action.destination.containerId}")
            }
        }
        }
        val versioned = if (transition?.rulesVersion?.let { it >= 13 } == true) semantic.copy(rulesVersion = 2,
            untap = action is PlayAction.SetTapped && !action.tapped || transition.fields.any {
                it.field == GameplayField.TAPPED && it.to == "false" } && action !is PlayAction.MoveCard && action !is PlayAction.Mulligan,
            moveIntoLibrary = (action as? PlayAction.MoveToLibraryRandom)?.libraryId ?: (action as? PlayAction.MoveCard)?.takeIf { move ->
                move.source.containerId != move.destination.containerId && containers.any {
                    it.id == move.destination.containerId && it.zone == PlayZone.LIBRARY } }?.destination?.containerId)
            else semantic
        val casting = if (transition?.rulesVersion?.let { it >= 15 } == true) versioned.copy(rulesVersion = 3,
            stackEntryId = when (action) {
                is PlayAction.MoveCard -> action.cardId.takeIf { semantic.significance == HistorySignificance.STACK_ENTRY }
                is PlayAction.Stack -> when (val stack = action.action) {
                    is StackAction.MarkText -> stack.entryId
                    is StackAction.ToggleTextMark -> stack.entryId
                    is StackAction.AddEffect, is StackAction.CopyEffect -> transition.combatStack?.effects
                        ?.firstOrNull { it.from == null && it.to != null }?.id
                    else -> null
                }
                is PlayAction.SetProjectionNote -> action.occurrenceId
                is PlayAction.SetNote -> action.cardId
                else -> null
            }) else versioned
        // Rules v29: reaching Declare blockers ends a group, a timeline endpoint the defender can continue from.
        val blockers = if (transition != null && transition.rulesVersion >= BLOCKERS_BOUNDARY_RULES_VERSION &&
            (action is PlayAction.ChooseStep && !action.through || action == PlayAction.NextStep) &&
            transition.fields.any { it.field == GameplayField.STEP && it.to == TurnPosition.DECLARE_BLOCKERS.step } &&
            transition.fields.none { it.field == GameplayField.PHASE && it.to != TurnPosition.DECLARE_BLOCKERS.phase })
            casting.copy(endsGroup = true) else casting
        // Rules v29: closing a panel never joins its session group, and nothing joins the close.
        val closing = if (action is PlayAction.DismissContents && transition != null &&
            transition.rulesVersion >= PANEL_CLOSE_STEP_RULES_VERSION) blockers.copy(isolated = true) else blockers
        val panel = transition?.contents?.from ?: transition?.contents?.to
        return if (panel?.search == true && panel.panelVersion >= 2 && action !is PlayAction.RevealContentsCard)
            closing.copy(panelSession = panel.id) else closing
    }

    /** Compact prefix fold shared by authoritative append and provisional prediction. Never accepts client group IDs. */
    fun append(previous: GameplayGroup?, nodeId: String, requestId: String, author: String, semantic: HistorySemantic): GameplayGroup {
        val incoming = semantic.category
        // A category key this build does not know (a newer server) continues like a group led by a comment or ping.
        val context = previous?.let { group -> HistoryCategory.entries.firstOrNull { it.key == group.category } ?: HistoryCategory.O }
        // Rules v32: a group of nothing but passed steps waits, like a closure run, for what the same action does
        // next inside the step it reached (the draw in the Draw step), which then leads the group.
        val passing = previous?.representative?.semantic?.significance == HistorySignificance.STEP_PASSED
        val pending = context in setOf(HistoryCategory.T, HistoryCategory.F, HistoryCategory.R) || passing
        val pass = semantic.significance == HistorySignificance.ATTENTION_PASS
        val casting = semantic.rulesVersion >= 3 && previous?.stackEntryId != null
        val joins = previous != null && !previous.closed && previous.representative?.semantic?.isolated != true &&
            if (semantic.endsGroup) previous.author == author && context == HistoryCategory.K && previous.stackEntryId == null &&
                previous.panelSession == null && semantic.panelSession == null
            else !semantic.isolated && (if (casting) previous.author == author && when (semantic.significance) {
                HistorySignificance.ORIENTATION -> !semantic.untap
                HistorySignificance.CARD_TEXT_NOTE_EDIT -> semantic.stackEntryId == previous.stackEntryId
                HistorySignificance.PING, HistorySignificance.EVENT_COMMENT -> true
                else -> false
            } else ((semantic.panelSession != null && previous.panelSession == semantic.panelSession && previous.author == author &&
                (semantic.rulesVersion < 2 || incoming in setOf(HistoryCategory.P, HistoryCategory.B) ||
                    incoming != HistoryCategory.U && context != HistoryCategory.U || incoming == context)) ||
            (semantic.panelSession == null && previous.panelSession == null && (pass || (previous.author == author && context != HistoryCategory.S && incoming != HistoryCategory.S && when (incoming) {
            HistoryCategory.U -> context == HistoryCategory.U
            HistoryCategory.T -> previous.tapsAndPingsOnly
            HistoryCategory.P, HistoryCategory.B -> true
            HistoryCategory.F, HistoryCategory.R -> pending
            else -> incoming == HistoryCategory.H && semantic.rulesVersion >= 2 && previous.lastMoveIntoLibrary == semantic.key || pending || when (context) {
                HistoryCategory.C -> incoming in setOf(HistoryCategory.C, HistoryCategory.E)
                HistoryCategory.M, HistoryCategory.H -> incoming == context && previous.key == semantic.key
                HistoryCategory.E, HistoryCategory.K, HistoryCategory.L -> incoming == context
                else -> false
            }
        })))))
        val representative = HistoryRepresentative(nodeId, semantic)
        if (!joins) return GameplayGroup(CommandIds.uuidV5(requestId, "group/0"), author, nodeId, nodeId,
            (if (incoming in setOf(HistoryCategory.P, HistoryCategory.B)) HistoryCategory.O else incoming).key,
             semantic.key, "1", incoming in setOf(HistoryCategory.T, HistoryCategory.P), representative, semantic.panelSession,
             semantic.moveIntoLibrary, semantic.stackEntryId.takeIf { semantic.significance == HistorySignificance.STACK_ENTRY },
             semantic.endsGroup)
        val old = requireNotNull(previous)
        val nextContext = when {
            incoming in setOf(HistoryCategory.P, HistoryCategory.B) -> context!!
            pending && semantic.significance.anchor -> incoming
            context == HistoryCategory.O && incoming == HistoryCategory.T -> HistoryCategory.T
            else -> context!!
        }
        return old.copy(lastNodeId = nodeId, category = nextContext.key,
            key = if (pending && semantic.significance.anchor) semantic.key else old.key,
            count = (old.count.toLong() + 1).toString(),
            tapsAndPingsOnly = old.tapsAndPingsOnly && incoming in setOf(HistoryCategory.T, HistoryCategory.P),
            representative = choose(old.representative, representative), lastMoveIntoLibrary = semantic.moveIntoLibrary,
            stackEntryId = if (semantic.significance == HistorySignificance.STACK_ENTRY) semantic.stackEntryId else old.stackEntryId,
            closed = semantic.endsGroup)
    }

    fun choose(first: HistoryRepresentative?, next: HistoryRepresentative): HistoryRepresentative {
        if (first == null) return next
        val a = first.semantic.significance; val b = next.semantic.significance
        return if ((b.anchor && !a.anchor) || (b.anchor == a.anchor && b.rank < a.rank)) next else first
    }
}
