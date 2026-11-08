package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault

/** Shared logical coordinates, never pixels. Each free-standing card also has a stable pile ID. */
@Serializable data class BoardPosition(val x: Int, val y: Int)
@Serializable data class PlayPlacement(val position: BoardPosition, val pileId: String)
@Serializable enum class MoveSourceEdge { MEMBER, TOP, BOTTOM }
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class MoveSource(val containerId: String, val placement: PlayPlacement? = null,
    val edge: MoveSourceEdge = MoveSourceEdge.MEMBER, val bottomSlice: List<String> = emptyList(),
    /** Explicit edge intent for rapid multi-draw/mill plans spanning a branch reseed. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val takeFromEdge: Boolean = false)
@Serializable enum class MoveInsertion { TOP, BOTTOM, GAP, UNORDERED, EMPTY, PILE_TOP, PILE_BOTTOM, PILE_GAP }
/** GAP binds both untouched neighbors; null denotes an edge. EMPTY creates request-derived pile/0.
 * PILE_GAP additionally binds the addressed card at the named pile/position. Only explicitly pending
 * invocation members are excluded from neighbor validation, never from capacity or the actual board.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class MoveDestination(val containerId: String, val insertion: MoveInsertion,
    val position: BoardPosition? = null, val pileId: String? = null,
    val afterId: String? = null, val beforeId: String? = null,
    val pendingIds: List<String> = emptyList(), val targetCardId: String? = null,
    /** Original edge placement intent retained when a multi-card block lowers to GAP moves. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val libraryInsertion: MoveInsertion? = null)

/**
 * What the table's own controls make of a player's gesture on a game on engine state (engine-responsiveness R4a): a card
 * moved within its own Battlefield (its place), the combat staging, a Stack entry dropped or removed, "Inspect other side",
 * passing the move, and the records beside the board (early answers). The device works them out on the board shown and
 * shares what it takes as table changes; the rest is refused (nothing changes the game by hand, I-D52).
 */
@Serializable sealed interface PlayAction {
    @Serializable @SerialName("passAttention") data object PassAttention : PlayAction
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable @SerialName("moveCard") data class MoveCard(val cardId: String, val source: MoveSource,
        val destination: MoveDestination, val expectedStackDigest: String? = null,
        /** Null uses zone-entry defaults; Battlefield-to-Battlefield preserves explicit covering. True puts the
         * card onto the Battlefield face down (manifest, face-down cast). */
        @EncodeDefault(EncodeDefault.Mode.NEVER) val faceDown: Boolean? = null,
        /** Pulling a top Stack projection moves its source and removes the projection in ONE action. */
        @EncodeDefault(EncodeDefault.Mode.NEVER) val removeStackEntryId: String? = null) : PlayAction
    /** Explicit disclosure binds the requested face AND region, never whichever card/side is now there. */
    @Serializable @SerialName("inspectFace") data class InspectFace(val cardId: String, val faceId: String,
        val containerId: String) : PlayAction
    @Serializable @SerialName("combat") data class Combat(val action: CombatAction) : PlayAction
    @Serializable @SerialName("stack") data class Stack(val action: StackAction) : PlayAction
    /** The records beside the board ([MtgAction.SetRecord]: an early answer, I-D33). */
    @Serializable @SerialName("mtg") data class Mtg(val action: MtgAction) : PlayAction
}

/** SYS-053: refusal reasons for actions the rules don't allow; the app shows each with a message saying why. */
object RuleRefusals {
    /** Anything that would put a real card into the Stack (STK-003); only entries pointing at it go there. */
    const val REAL_CARD_ON_STACK = "realCardOnStack"
    /** Damage or an until-end-of-turn mark (or a note) on a card that isn't on the Battlefield. */
    const val OFF_BATTLEFIELD = "offBattlefield"
    /** I-D23: no branch starts while an Automatum flow is open. */
    const val FLOW_OPEN = "flowOpen"
}
