package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Semantic presentation categories, not request types or rules-engine interpretations. */
@Serializable enum class HistoryCategory(val key: String) {
    T("tap"), M("move"), I("stack"), E("edit"), C("creation"), K("combat"), L("stackLayout"),
    V("view"), H("shuffle"), F("closure"), R("random"), P("ping"), B("backward"), S("ending"), O("leading")
}

/** D-299 ranks. The representative retains the event's literal icon, never a rank-label icon. */
@Serializable enum class HistorySignificance(val category: HistoryCategory, val rank: Int, val anchor: Boolean = true) {
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
    ATTENTION_PASS(HistoryCategory.B, 90, false)
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class HistorySemantic(val significance: HistorySignificance, val icon: String, val key: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val panelSession: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val isolated: Boolean = false) {
    init {
        require(icon.isNotBlank())
        require((significance.category in setOf(HistoryCategory.M, HistoryCategory.H)) == (key != null))
    }
    val category: HistoryCategory get() = significance.category
}
@Serializable data class HistoryRepresentative(val nodeId: String, val semantic: HistorySemantic)

object HistoryGrouping {
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
        is PlayAction.SuggestResult -> HistorySemantic(HistorySignificance.ENDING_SUGGESTION, "flag")
        PlayAction.PassAttention -> HistorySemantic(HistorySignificance.ATTENTION_PASS, "outlined_flag")
        is PlayAction.SetTapped -> HistorySemantic(HistorySignificance.ORIENTATION, "rotate_90_degrees_cw")
        is PlayAction.SetLife, is PlayAction.AdjustLife -> HistorySemantic(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, "favorite")
        is PlayAction.SetPlayerCounter, is PlayAction.AdjustPlayerCounter ->
            HistorySemantic(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, "exposure_plus_1")
        PlayAction.EndTurn -> HistorySemantic(HistorySignificance.TURN_TRANSITION, "outlined_flag")
        PlayAction.NextStep -> HistorySemantic(if (transition?.fields?.any { it.field == GameplayField.TURN_PLAYER } == true)
            HistorySignificance.TURN_TRANSITION else HistorySignificance.PHASE_STEP_TRACKING, "outlined_flag")
        is PlayAction.ChooseStep -> HistorySemantic(HistorySignificance.PHASE_STEP_TRACKING, "outlined_flag")
        is PlayAction.RollDie -> HistorySemantic(HistorySignificance.DIE_COIN_RUN, "casino")
        is PlayAction.FlipCoin -> HistorySemantic(HistorySignificance.DIE_COIN_RUN, "monetization_on")
        is PlayAction.ShuffleLibrary -> HistorySemantic(HistorySignificance.SHUFFLE, "shuffle", action.containerId)
        is PlayAction.ShowContents -> HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE,
            if (action.reveal) "visibility" else "pageview")
        is PlayAction.DismissContents -> HistorySemantic(HistorySignificance.VIEW_REVEAL_DISMISSAL, "close")
        is PlayAction.RevealContentsCard -> HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE, "visibility", isolated = true)
        is PlayAction.ReorderContents -> HistorySemantic(HistorySignificance.SAME_ZONE_PLACEMENT, "open_with", action.displayId)
        is PlayAction.EventComment -> HistorySemantic(HistorySignificance.EVENT_COMMENT, "chat_bubble")
        is PlayAction.Ping, is PlayAction.Unping -> HistorySemantic(HistorySignificance.PING, "ads_click")
        is PlayAction.SetCorner, is PlayAction.AdjustCorner, is PlayAction.ClearCardCounter ->
            HistorySemantic(HistorySignificance.CARD_CORNER_COUNTER_EDIT, "exposure_plus_1")
        is PlayAction.ResetCorner -> HistorySemantic(HistorySignificance.CARD_CORNER_COUNTER_EDIT, "settings_backup_restore")
        is PlayAction.SetCounter -> HistorySemantic(if (action.target == CounterTarget.PLAYER)
            HistorySignificance.LIFE_PLAYER_COUNTER_EDIT else HistorySignificance.CARD_CORNER_COUNTER_EDIT, "exposure_plus_1")
        is PlayAction.AdjustCounter -> HistorySemantic(if (action.target == CounterTarget.PLAYER)
            HistorySignificance.LIFE_PLAYER_COUNTER_EDIT else HistorySignificance.CARD_CORNER_COUNTER_EDIT, "exposure_plus_1")
        is PlayAction.SetNote -> HistorySemantic(HistorySignificance.CARD_TEXT_NOTE_EDIT, "sticky_note_2")
        is PlayAction.SetOwner -> HistorySemantic(HistorySignificance.OWNER_EDIT, "switch_account")
        is PlayAction.SetFace -> HistorySemantic(HistorySignificance.FACE_COVER_EDIT, "flip_camera_android")
        is PlayAction.SetCovered -> HistorySemantic(HistorySignificance.FACE_COVER_EDIT,
            if (action.covered) "flip" else "flip_to_front")
        is PlayAction.InspectFace -> HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE, "visibility")
        is PlayAction.CreateToken -> HistorySemantic(HistorySignificance.TOKEN_CLONE_CREATION, "add_box")
        is PlayAction.CloneToken -> HistorySemantic(HistorySignificance.TOKEN_CLONE_CREATION, "control_point_duplicate")
        is PlayAction.RemoveToken -> HistorySemantic(HistorySignificance.TOKEN_REMOVAL, "delete")
        is PlayAction.Link, is PlayAction.LinkProjections -> HistorySemantic(HistorySignificance.LINK_EDIT, "link")
        is PlayAction.RemoveLink, is PlayAction.RemoveProjectionLink -> HistorySemantic(HistorySignificance.LINK_EDIT, "link_off")
        is PlayAction.Combat -> when (val combat = action.action) {
            CombatAction.Open -> HistorySemantic(HistorySignificance.COMBAT_AREA_CREATION, "swords")
            is CombatAction.Close -> HistorySemantic(HistorySignificance.COMBAT_CLOSURE, "layers_clear")
            is CombatAction.Declare -> HistorySemantic(HistorySignificance.COMBAT_MEMBERSHIP, "swords")
            is CombatAction.Remove -> HistorySemantic(HistorySignificance.COMBAT_MEMBERSHIP, "remove_circle_outline")
            is CombatAction.Place -> HistorySemantic(if (combat.expectedOccurrence == null ||
                combat.expectedOccurrence.role != combat.destination.role) HistorySignificance.COMBAT_MEMBERSHIP
                else HistorySignificance.COMBAT_LAYOUT, "open_with")
        }
        is PlayAction.Stack -> when (action.action) {
            is StackAction.MarkText, is StackAction.ToggleTextMark -> HistorySemantic(HistorySignificance.CARD_TEXT_NOTE_EDIT, "format_ink_highlighter")
            is StackAction.AddEffect, is StackAction.CopyEffect -> HistorySemantic(HistorySignificance.STACK_ENTRY, "layers")
            is StackAction.SetController -> HistorySemantic(HistorySignificance.STACK_LAYOUT, "swap_horiz")
            is StackAction.RemoveEffect -> HistorySemantic(HistorySignificance.STACK_REMOVAL, "layers_clear")
            is StackAction.ReorderEffect -> HistorySemantic(HistorySignificance.STACK_LAYOUT, "open_with")
        }
        is PlayAction.MoveCard -> {
            val from = containers.single { it.id == action.source.containerId }.zone
            val to = containers.single { it.id == action.destination.containerId }.zone
            when {
                action.removeStackEntryId != null -> HistorySemantic(HistorySignificance.STACK_REMOVAL, "layers_clear")
                from == PlayZone.STACK && to == PlayZone.STACK ->
                    HistorySemantic(HistorySignificance.STACK_LAYOUT, "open_with")
                from == PlayZone.STACK -> HistorySemantic(HistorySignificance.STACK_REMOVAL, "layers")
                to == PlayZone.STACK -> HistorySemantic(HistorySignificance.STACK_ENTRY, "layers")
                else -> HistorySemantic(if (from == to) HistorySignificance.SAME_ZONE_PLACEMENT else HistorySignificance.CROSS_ZONE_MOVE,
                    if (from == PlayZone.LIBRARY && to == PlayZone.HAND && action.source.edge == MoveSourceEdge.TOP)
                        "download" else "open_with", "${action.source.containerId}/${action.destination.containerId}")
            }
        }
        }
        val panel = transition?.contents?.from ?: transition?.contents?.to
        return if (panel?.search == true && panel.panelVersion >= 2 && action !is PlayAction.RevealContentsCard)
            semantic.copy(panelSession = panel.id) else semantic
    }

    /** Compact prefix fold shared by authoritative append and provisional prediction. Never accepts client group IDs. */
    fun append(previous: GameplayGroup?, nodeId: String, requestId: String, author: String, semantic: HistorySemantic): GameplayGroup {
        val incoming = semantic.category
        val context = previous?.let { group -> HistoryCategory.entries.single { it.key == group.category } }
        val pending = context in setOf(HistoryCategory.T, HistoryCategory.F, HistoryCategory.R)
        val pass = semantic.significance == HistorySignificance.ATTENTION_PASS
        val joins = previous != null && !semantic.isolated && previous.representative?.semantic?.isolated != true &&
            ((semantic.panelSession != null && previous.panelSession == semantic.panelSession && previous.author == author) ||
            (semantic.panelSession == null && previous.panelSession == null && (pass || (previous.author == author && context != HistoryCategory.S && incoming != HistoryCategory.S && when (incoming) {
            HistoryCategory.T -> previous.tapsAndPingsOnly
            HistoryCategory.P, HistoryCategory.B -> true
            HistoryCategory.F, HistoryCategory.R -> pending
            else -> pending || when (context) {
                HistoryCategory.C -> incoming in setOf(HistoryCategory.C, HistoryCategory.E)
                HistoryCategory.M, HistoryCategory.H -> incoming == context && previous.key == semantic.key
                HistoryCategory.E, HistoryCategory.K, HistoryCategory.L -> incoming == context
                else -> false
            }
        }))))
        val representative = HistoryRepresentative(nodeId, semantic)
        if (!joins) return GameplayGroup(CommandIds.uuidV5(requestId, "group/0"), author, nodeId, nodeId,
            (if (incoming in setOf(HistoryCategory.P, HistoryCategory.B)) HistoryCategory.O else incoming).key,
            semantic.key, "1", incoming in setOf(HistoryCategory.T, HistoryCategory.P), representative, semantic.panelSession)
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
            representative = choose(old.representative, representative))
    }

    fun choose(first: HistoryRepresentative?, next: HistoryRepresentative): HistoryRepresentative {
        if (first == null) return next
        val a = first.semantic.significance; val b = next.semantic.significance
        return if ((b.anchor && !a.anchor) || (b.anchor == a.anchor && b.rank < a.rank)) next else first
    }
}
