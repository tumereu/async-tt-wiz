package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.json.*

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

@Serializable sealed interface PlayAction {
    @Serializable @SerialName("suggestResult") data class SuggestResult(val outcome: SuggestedResult) : PlayAction
    @Serializable @SerialName("setTapped") data class SetTapped(val cardId: String, val tapped: Boolean) : PlayAction
    @Serializable @SerialName("setLife") data class SetLife(val accountId: String, val value: String) : PlayAction
    @Serializable @SerialName("adjustLife") data class AdjustLife(val accountId: String, val delta: String) : PlayAction
    @Serializable @SerialName("setPlayerCounter") data class SetPlayerCounter(val accountId: String,
        val color: CounterColor, val value: String) : PlayAction
    @Serializable @SerialName("adjustPlayerCounter") data class AdjustPlayerCounter(val accountId: String,
        val color: CounterColor, val delta: String) : PlayAction
    @Serializable @SerialName("nextStep") data object NextStep : PlayAction
    @Serializable @SerialName("passAttention") data object PassAttention : PlayAction
    @Serializable @SerialName("endTurn") data object EndTurn : PlayAction
    @Serializable @SerialName("chooseStep") data class ChooseStep(val position: TurnPosition) : PlayAction
    /** Original client samples are literal inputs, not instructions for a server-side reroll. */
    @Serializable @SerialName("rollDie") data class RollDie(val sides: String, val value: Int) : PlayAction {
        constructor(sides: Int, value: Int) : this(sides.toString(), value)
    }
    @Serializable @SerialName("flipCoin") data class FlipCoin(val value: CoinFace) : PlayAction
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable @SerialName("moveCard") data class MoveCard(val cardId: String, val source: MoveSource,
        val destination: MoveDestination, val expectedStackDigest: String? = null,
        /** Null uses zone-entry defaults; only Battlefield-to-Battlefield preserves explicit covering. */
        @EncodeDefault(EncodeDefault.Mode.NEVER) val faceDown: Boolean? = null,
        /** Pulling a top Stack projection moves its source and removes the projection in ONE Undo. */
        @EncodeDefault(EncodeDefault.Mode.NEVER) val removeStackEntryId: String? = null) : PlayAction
    @Serializable @SerialName("shuffleLibrary") data class ShuffleLibrary(val containerId: String,
        val seed: String, val algorithm: String = "shuffle-v1") : PlayAction
    @Serializable @SerialName("setKeepTopRevealed") data class SetKeepTopRevealed(val containerId: String,
        val enabled: Boolean) : PlayAction
    @Serializable @SerialName("mulligan") data class Mulligan(val libraryId: String, val handId: String,
        val displayedHand: List<String>, val drawCount: Int, val seed: String) : PlayAction
    @Serializable @SerialName("showContents") data class ShowContents(val containerId: String,
        val selection: ContentsSelection, val count: Int = 1, val reveal: Boolean = false) : PlayAction
    @Serializable @SerialName("revealHand") data class RevealHand(val handId: String, val cardIds: List<String>) : PlayAction
    /** Fresh independent seed and literal chosen identity, verified at acceptance, never rerolled on replay. */
    @Serializable @SerialName("randomHand") data class RandomHand(val handId: String, val selectedIds: List<String>,
        val seed: String, val result: HandRandomResult, val cardId: String) : PlayAction
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable @SerialName("dismissContents") data class DismissContents(val displayId: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val rest: ContentsRest = ContentsRest.TOP,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val seed: String? = null) : PlayAction
    @Serializable @SerialName("revealContentsCard") data class RevealContentsCard(val displayId: String, val cardId: String) : PlayAction
    @Serializable @SerialName("reorderContents") data class ReorderContents(val displayId: String, val cardId: String,
        val beforeId: String? = null) : PlayAction
    @Serializable @SerialName("eventComment") data class EventComment(val reference: EventReference, val text: String) : PlayAction
    @Serializable @SerialName("ping") data class Ping(val reference: EventReference, val target: PingTarget) : PlayAction
    @Serializable @SerialName("unping") data class Unping(val reference: EventReference, val target: PingTarget) : PlayAction
    @Serializable sealed interface Manual : PlayAction
    @Serializable @SerialName("setDamage") data class SetDamage(val cardId: String, val value: String) : Manual
    @Serializable @SerialName("adjustDamage") data class AdjustDamage(val cardId: String, val delta: String) : Manual
    @Serializable @SerialName("setEotCorner") data class SetEotCorner(val cardId: String, val value: String) : Manual
    @Serializable @SerialName("adjustEotCorner") data class AdjustEotCorner(val cardId: String, val part: CornerPart,
        val delta: Int) : Manual
    @Serializable @SerialName("setEotNote") data class SetEotNote(val cardId: String, val value: String) : Manual
    @Serializable @SerialName("setCorner") data class SetCorner(val cardId: String, val value: String) : Manual
    @Serializable @SerialName("adjustCorner") data class AdjustCorner(val cardId: String, val part: CornerPart,
        val delta: Int) : Manual
    @Serializable @SerialName("resetCorner") data class ResetCorner(val cardId: String) : Manual
    @Serializable @SerialName("setCounter") data class SetCounter(val target: CounterTarget, val targetId: String,
        val color: CounterColor, val value: String) : Manual
    @Serializable @SerialName("adjustCounter") data class AdjustCounter(val target: CounterTarget, val targetId: String,
        val color: CounterColor, val delta: String) : Manual
    @Serializable @SerialName("setNote") data class SetNote(val cardId: String, val value: String) : Manual
    @Serializable @SerialName("setProjectionNote") data class SetProjectionNote(val occurrenceId: String, val value: String) : Manual
    @Serializable @SerialName("setOwner") data class SetOwner(val cardId: String, val accountId: String) : Manual
    @Serializable @SerialName("setFace") data class SetFace(val cardId: String, val faceId: String) : Manual
    @Serializable @SerialName("setCovered") data class SetCovered(val cardId: String, val covered: Boolean) : Manual
    /** Explicit disclosure binds the requested face AND region, never whichever card/side is now there. */
    @Serializable @SerialName("inspectFace") data class InspectFace(val cardId: String, val faceId: String,
        val containerId: String) : Manual
    /** Entry conveniences can be batch members; projection pulls are a single mutation. */
    @Serializable @SerialName("clearCardCounter") data class ClearCardCounter(val cardId: String, val color: CounterColor) : PlayAction
    @Serializable @SerialName("createToken") data class CreateToken(val definition: SubmittedDefinition) : PlayAction
    @Serializable @SerialName("cloneToken") data class CloneToken(val cardId: String) : PlayAction
    @Serializable @SerialName("removeToken") data class RemoveToken(val cardId: String) : PlayAction
    @Serializable @SerialName("link") data class Link(val first: LinkEndpoint, val second: LinkEndpoint) : PlayAction
    @Serializable @SerialName("removeLink") data class RemoveLink(val linkId: String) : PlayAction
    /** Occurrence endpoints only. Never resolve these IDs through liveSource or accept mixed kinds. */
    @Serializable @SerialName("linkProjections") data class LinkProjections(val first: ProjectionEndpoint,
        val second: ProjectionEndpoint) : PlayAction
    @Serializable @SerialName("removeProjectionLink") data class RemoveProjectionLink(val linkId: String) : PlayAction
    @Serializable @SerialName("combat") data class Combat(val action: CombatAction) : PlayAction
    @Serializable @SerialName("stack") data class Stack(val action: StackAction) : PlayAction
}

data class GameplayChange(val state: PlayState? = null, val mutation: GameplayMutation? = null,
    val reason: String? = null) {
    val outcome: String get() = if (reason != null) "rejected" else if (mutation == null) "noOp" else "accepted"
}

/** The sole gameplay processor, used for volatile prediction and authoritative execution alike. */
object GameplayRules {
    fun valid(action: PlayAction): Boolean = when (action) {
        is PlayAction.SuggestResult -> true
        is PlayAction.SetTapped -> DeckValidation.uuid(action.cardId)
        is PlayAction.ClearCardCounter -> DeckValidation.uuid(action.cardId)
        is PlayAction.SetLife -> DeckValidation.uuid(action.accountId) && DeckValidation.text(action.value)
        is PlayAction.AdjustLife -> DeckValidation.uuid(action.accountId) && DeckValidation.text(action.delta) &&
            GameplayNumbers.integer(action.delta)
        is PlayAction.SetPlayerCounter -> DeckValidation.uuid(action.accountId) && DeckValidation.text(action.value)
        is PlayAction.AdjustPlayerCounter -> DeckValidation.uuid(action.accountId) && TableActions.numeric(action.delta)
        PlayAction.PassAttention, PlayAction.NextStep, PlayAction.EndTurn, is PlayAction.ChooseStep, is PlayAction.FlipCoin -> true
        is PlayAction.RollDie -> action.sides.matches(Regex("[1-9][0-9]*")) &&
            action.sides.toIntOrNull()?.let { action.value in 1..it } == true
        is PlayAction.Manual -> ManualEditRules.valid(action)
        is PlayAction.MoveCard -> DeckValidation.uuid(action.cardId) && DeckValidation.uuid(action.source.containerId) &&
            (action.destination.libraryInsertion == null || action.destination.libraryInsertion in setOf(MoveInsertion.TOP, MoveInsertion.BOTTOM)) &&
            (!action.source.takeFromEdge || action.source.edge != MoveSourceEdge.MEMBER || action.source.bottomSlice.isNotEmpty()) &&
            DeckValidation.uuid(action.destination.containerId) &&
            listOfNotNull(action.source.placement?.pileId, action.destination.pileId, action.destination.afterId,
                action.destination.beforeId, action.destination.targetCardId).all(DeckValidation::uuid) &&
            (action.source.bottomSlice + action.destination.pendingIds).all(DeckValidation::uuid) &&
            action.source.bottomSlice.distinct().size == action.source.bottomSlice.size &&
            action.destination.pendingIds.distinct().size == action.destination.pendingIds.size &&
            (action.destination.insertion != MoveInsertion.PILE_GAP || (action.destination.pendingIds.size <= 2 &&
                action.destination.pendingIds.none { it in listOf(action.cardId, action.destination.afterId,
                    action.destination.beforeId, action.destination.targetCardId) })) &&
            (action.destination.insertion == MoveInsertion.PILE_GAP || action.destination.targetCardId == null) &&
            (action.expectedStackDigest == null || DeckValidation.digest(action.expectedStackDigest)) &&
            (action.removeStackEntryId == null || DeckValidation.uuid(action.removeStackEntryId))
        is PlayAction.SetKeepTopRevealed -> DeckValidation.uuid(action.containerId)
        is PlayAction.ShuffleLibrary -> DeckValidation.uuid(action.containerId) && action.algorithm == "shuffle-v1" &&
            runCatching { RandomV1.decodeSeed(action.seed) }.isSuccess
        is PlayAction.Mulligan -> listOf(action.libraryId, action.handId).all(DeckValidation::uuid) &&
            action.displayedHand.all(DeckValidation::uuid) && action.displayedHand.distinct().size == action.displayedHand.size &&
            action.drawCount >= 0 && runCatching { RandomV1.decodeSeed(action.seed) }.isSuccess
        is PlayAction.ShowContents -> DeckValidation.uuid(action.containerId) && action.count >= 0
        is PlayAction.RevealHand -> DeckValidation.uuid(action.handId) && action.cardIds.isNotEmpty() &&
            action.cardIds.all(DeckValidation::uuid) && action.cardIds.distinct().size == action.cardIds.size
        is PlayAction.RandomHand -> listOf(action.handId, action.cardId).all(DeckValidation::uuid) &&
            action.selectedIds.all(DeckValidation::uuid) && action.selectedIds.distinct().size == action.selectedIds.size &&
            runCatching { RandomV1.decodeSeed(action.seed) }.isSuccess
        is PlayAction.DismissContents -> DeckValidation.uuid(action.displayId) &&
            (action.seed == null || runCatching { RandomV1.decodeSeed(action.seed) }.isSuccess)
        is PlayAction.RevealContentsCard -> DeckValidation.uuid(action.displayId) && DeckValidation.uuid(action.cardId)
        is PlayAction.ReorderContents -> DeckValidation.uuid(action.displayId) && DeckValidation.uuid(action.cardId) &&
            (action.beforeId == null || DeckValidation.uuid(action.beforeId))
        is PlayAction.EventComment -> EventAnnotations.valid(action.reference) &&
            action.text.isNotBlank() && action.text.length <= 16384 && '\u0000' !in action.text
        is PlayAction.Ping -> EventAnnotations.valid(action.reference) && EventAnnotations.valid(action.target)
        is PlayAction.Unping -> EventAnnotations.valid(action.reference) && EventAnnotations.valid(action.target)
        is PlayAction.CreateToken -> TokenLinkRules.validDefinition(action.definition)
        is PlayAction.CloneToken -> DeckValidation.uuid(action.cardId)
        is PlayAction.RemoveToken -> DeckValidation.uuid(action.cardId)
        is PlayAction.Link -> DeckValidation.uuid(action.first.id) && DeckValidation.uuid(action.second.id)
        is PlayAction.RemoveLink -> DeckValidation.uuid(action.linkId)
        is PlayAction.LinkProjections -> DeckValidation.uuid(action.first.occurrenceId) && DeckValidation.uuid(action.second.occurrenceId)
        is PlayAction.RemoveProjectionLink -> DeckValidation.uuid(action.linkId)
        is PlayAction.Combat -> CombatStackRules.valid(action.action)
        is PlayAction.Stack -> CombatStackRules.valid(action.action)
    }

    fun change(state: PlayState, actor: String, command: GameCommand,
        references: EventReferenceResolver = EventReferenceResolver.UNAVAILABLE): GameplayChange {
        if (!GameWire.valid(command)) return GameplayChange(reason = "invalidInput")
        val resolved = LibraryKnowledge.resolveEdge(state, command)
        val change = execute(state, actor, resolved, references)
        var mutation = change.mutation ?: return change
        val next = change.state ?: return change
        if (mutation.action == PlayAction.NextStep || mutation.action == PlayAction.EndTurn || mutation.action is PlayAction.ChooseStep)
            mutation = mutation.copy(transition = mutation.transition.copy(reads = mutation.transition.reads +
                GameplayRead(state.id, GameplayField.TURN_PLAYER, state.board.turn.accountId)))
        mutation.transition.contents?.to?.takeIf { mutation.action is PlayAction.ShowContents }?.let { display ->
            mutation = mutation.copy(transition = mutation.transition.copy(reads = mutation.transition.reads +
                display.cardIds.map { id -> GameplayRead(id, GameplayField.FACE, state.board.cards.single { it.id == id }.faceId) }))
        }
        val delta = GameplayDeltas.capture(state, next, mutation.id, (mutation.action as? PlayAction.SetProjectionNote)?.occurrenceId)
        val recorded = GameplayHistory.recontextualize(state, mutation.copy(schemaVersion = 2,
            transition = mutation.transition.copy(delta = delta,
                concerns = GameplayConcerns.capture(state, next, mutation, delta)),
            bookkeeping = GameplayBookkeeping(state.currentGroup, state.currentBranchId, next.currentGroup)))
        return change.copy(mutation = recorded.copy(transition = recorded.transition.copy(
            delta = recorded.transition.delta!!.copy(seal = GameplayDeltas.seal(recorded)))))
    }

    private fun execute(state: PlayState, actor: String, command: GameCommand,
        references: EventReferenceResolver): GameplayChange {
        fun reject(reason: String) = GameplayChange(reason = reason)
        val action = (command.action as? GameAction.Play)?.action ?: return reject("invalidInput")
        if (!GameWire.valid(command)) return reject("invalidInput")
        if (command.scope.gameId != state.gameId || command.scope.playId != state.id) return reject("inapplicable")
        if (actor !in state.board.players.map { it.accountId }) return reject("accessDenied")
        val board = state.board
        val next: BoardState
        val transition: GameplayTransition
        when (action) {
            PlayAction.PassAttention -> {
                val holder = board.players.singleOrNull { it.accountId != board.effectiveAttentionHolder }
                    ?.accountId ?: return reject("inapplicable")
                next = board.copy(attentionHolder = holder)
                transition = GameplayTransition(attention = AttentionChange(board.attentionHolder, holder))
            }
            is PlayAction.SuggestResult -> {
                if (board.resultSuggestion != null) return reject("inapplicable")
                val suggestion = ResultSuggestion(CommandIds.uuidV5(command.requestId, "result-suggestion"), actor, action.outcome)
                next = board.copy(resultSuggestion = suggestion)
                transition = GameplayTransition(resultSuggestion = ResultSuggestionChange(null, suggestion))
            }
            is PlayAction.SetTapped -> {
                val card = board.cards.singleOrNull { it.id == action.cardId } ?: return reject("missingReference")
                if (board.containers.none { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD })
                    return reject("inapplicable")
                if (card.tapped == action.tapped) return GameplayChange(state)
                next = board.copy(cards = board.cards.map { if (it.id == card.id) it.copy(tapped = action.tapped) else it })
                transition = GameplayTransition(listOf(GameplayFieldChange(card.id, GameplayField.TAPPED,
                    card.tapped.toString(), action.tapped.toString())))
            }
            is PlayAction.ClearCardCounter -> {
                val card = board.cards.singleOrNull { it.id == action.cardId } ?: return reject("missingReference")
                if (board.containers.none { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD })
                    return reject("inapplicable")
                val value = card.counters[action.color] ?: return GameplayChange(state)
                val fields = listOf(GameplayFieldChange(card.id, GameplayField.COUNTER, value, null, action.color))
                val changed = card.copy(counters = card.counters - action.color)
                next = board.copy(cards = board.cards.map { if (it.id == card.id) changed else it })
                transition = GameplayTransition(fields)
            }
            is PlayAction.SetLife, is PlayAction.AdjustLife -> {
                val account = when (action) { is PlayAction.SetLife -> action.accountId; is PlayAction.AdjustLife -> action.accountId; else -> error("unreachable") }
                val player = board.players.singleOrNull { it.accountId == account } ?: return reject("missingReference")
                val value = if (action is PlayAction.SetLife) TableActions.trimText(action.value) else {
                    if (!GameplayNumbers.integer(player.life)) return reject("inapplicable")
                    val delta = (action as PlayAction.AdjustLife).delta
                    // A zero delta does not edit or canonicalize the current text.
                    if (delta.removePrefix("+").removePrefix("-").all { it == '0' }) return GameplayChange(state)
                    TableActions.addNumber(player.life, delta) ?: return reject("invalidInput")
                }
                if (!DeckValidation.text(value)) return reject("invalidInput")
                if (player.life == value) return GameplayChange(state)
                next = board.copy(players = board.players.map { if (it.accountId == account) it.copy(life = value) else it })
                transition = GameplayTransition(listOf(GameplayFieldChange(account, GameplayField.LIFE, player.life, value)))
            }
            is PlayAction.MoveCard -> return move(state, actor, command, action)
            is PlayAction.RevealHand, is PlayAction.RandomHand -> return HandRules.change(state, actor, command, action)
            is PlayAction.SetKeepTopRevealed, is PlayAction.Mulligan, is PlayAction.ShuffleLibrary, is PlayAction.ShowContents, is PlayAction.DismissContents,
            is PlayAction.RevealContentsCard, is PlayAction.ReorderContents ->
                return LibraryRules.change(state, actor, command, action)
            PlayAction.NextStep, PlayAction.EndTurn, is PlayAction.ChooseStep,
            is PlayAction.RollDie, is PlayAction.FlipCoin,
            is PlayAction.SetPlayerCounter, is PlayAction.AdjustPlayerCounter ->
                return TableActions.change(state, actor, command, action)
            is PlayAction.EventComment, is PlayAction.Ping, is PlayAction.Unping ->
                return EventAnnotations.change(state, actor, command, references)
            is PlayAction.Manual -> return ManualEditRules.change(state, actor, command, action)
            is PlayAction.CreateToken, is PlayAction.CloneToken, is PlayAction.RemoveToken,
            is PlayAction.Link, is PlayAction.RemoveLink,
            is PlayAction.LinkProjections, is PlayAction.RemoveProjectionLink -> return TokenLinkRules.change(state, actor, command, action)
            is PlayAction.Combat, is PlayAction.Stack -> return CombatStackRules.change(state, actor, command)
        }
        return record(state, actor, command, next, transition)
    }

    internal fun record(state: PlayState, actor: String, command: GameCommand, board: BoardState,
        transition: GameplayTransition, contentsRulesVersion: Int = 2, rulesVersion: Int = 20): GameplayChange {
        // Movement/token adapters must enter here BEFORE publishing. Capture disappearance before resets,
        // close empty pairs and remove incident occurrence links in this same individual mutation.
        val action = (command.action as GameAction.Play).action
        val removedFromPanel = when (action) {
            is PlayAction.MoveCard -> action.cardId
            is PlayAction.RemoveToken -> action.cardId.takeIf { rulesVersion >= 20 }
            else -> null
        }
        val panel = state.board.contentsDisplays.firstOrNull { removedFromPanel in it.cardIds }
        if (panel != null && panel.author != actor) return GameplayChange(reason = "accessDenied")
        val resolved = LibraryKnowledge.after(state.board, if (panel == null) board else board.copy(contentsDisplays = board.contentsDisplays.mapNotNull {
            if (it.id != panel.id) it else {
                val updated = it.copy(cardIds = it.cardIds - removedFromPanel!!,
                    revealedIds = it.revealedIds - removedFromPanel)
                updated.takeUnless { contentsRulesVersion >= 2 && it.cardIds.isEmpty() }
            }
        }), action)
        val cleanup = if (rulesVersion >= 17) TurnMarks.clear(state.board, resolved) else resolved
        val closed = CombatStackRules.closeInvariants(state.board, cleanup, rulesVersion)
            ?: return GameplayChange(reason = "invalidStructure")
        // At most one newly exposed card per library, recorded in this transition, never a new event.
        val topDisclosures = closed.containers.filter { it.zone == PlayZone.LIBRARY && it.keepTopRevealed }.mapNotNull { library ->
            val id = library.cards.firstOrNull() ?: return@mapNotNull null
            val card = closed.cards.single { it.id == id }
            val old = state.board.containers.single { it.id == library.id }
            if (old.keepTopRevealed && old.cards.firstOrNull() == id &&
                state.board.cards.single { it.id == id }.faceId == card.faceId) return@mapNotNull null
            GameplayDisclosure(id, card.faceId, library.id, closed.players.map { it.accountId }.sorted(),
                // Only replacement tops are automatic; an explicit face change is still a Reveal.
                automatic = action !is PlayAction.SetKeepTopRevealed && old.cards.firstOrNull() != id)
        }
        // Version 17 adds temporary marks and atomic turn/zone cleanup; 18 adds pair targets/cleanup
        // and printed loyalty/defense/lore defaults. Version 20 adds sticky blocking,
        // resolved player-target dependencies, owner-routed random moves and token tray cleanup.
        // Retained events keep their original sealed deltas; acceptance never rewrites old effects.
        val relevant = transition.copy(rulesVersion = rulesVersion,
            fields = transition.fields + if (rulesVersion >= 17) TurnMarks.changes(resolved, cleanup) else emptyList(),
            combatStack = CombatStackRules.transition(state, closed, action),
            disclosures = transition.disclosures.filterNot { disclosure -> topDisclosures.any {
                it.cardId == disclosure.cardId && it.containerId == disclosure.containerId } } + topDisclosures,
            contentsRulesVersion = if (panel != null || transition.contents != null || transition.movement != null)
                contentsRulesVersion else transition.contentsRulesVersion,
            contents = if (panel == null) transition.contents else ContentsDisplayChange(panel, closed.contentsDisplays.singleOrNull { it.id == panel.id }))
        val mutation = GameplayMutation(CommandIds.uuidV5(command.requestId, "board-node"), state.currentNodeId,
            state.currentBranchId, command.requestId, actor, command.invocationId,
            command.action.action, relevant,
            grouping = HistoryGrouping.classify(command.action.action, state.board, actor, relevant),
            revealsInformation = relevant.disclosures.isNotEmpty() ||
                command.action.action is PlayAction.ShowContents && relevant.contents?.to?.cardIds?.isNotEmpty() == true)
        val group = GameplayHistory.append(state.currentGroup, mutation, state.board.containers)
        return GameplayChange(state.copy(board = closed, currentNodeId = mutation.id, currentGroup = group), mutation)
    }

    /** Constituents retain their own history nodes inside the caller's atomic invocation. */
    fun moveActions(state: PlayState, action: PlayAction.MoveCard): List<PlayAction> {
        // r13 defaults belong to the move itself, including its exact inverse.
        return listOf(action)
    }

    /** D-247: semantic ordered entries only; no face, text, orientation, coordinates or owner. */
    fun stackDigest(state: PlayState): String = CombatStackRules.stackDigest(state)

    private fun moveProjection(state: PlayState, actor: String, command: GameCommand, action: PlayAction.MoveCard,
        coverRulesVersion: Int): GameplayChange {
        val board = state.board
        val entry = action.removeStackEntryId!!
        val effect = board.stackEffects.singleOrNull { it.id == entry } ?: return GameplayChange(reason = "missingReference")
        if (effect.isCopy) return GameplayChange(reason = "inapplicable")
        val card = CombatStackRules.liveSource(board, entry) ?: return GameplayChange(reason = "missingReference")
        if (card.id != action.cardId || effect.sourceId != card.id || card.containerId != action.source.containerId ||
            card.placement != action.source.placement) return GameplayChange(reason = "movementConflict")
        val target = board.containers.singleOrNull { it.id == action.destination.containerId }
            ?: return GameplayChange(reason = "missingReference")
        if (target.zone == PlayZone.STACK) return GameplayChange(state)
        val source = board.containers.single { it.id == card.containerId }
        var candidate = state
        var transition = GameplayTransition()
        if (source.zone != target.zone) {
            val move = action.copy(removeStackEntryId = null)
            val moved = move(state, actor, command.copy(action = GameAction.Play(move)), move, coverRulesVersion)
            if (moved.reason != null) return moved
            candidate = moved.state!!
            transition = moved.mutation?.transition ?: GameplayTransition()
            // Reuse ordinary zone-entry defaults, but keep their effects inside this one mutation.
            for (reset in moveActions(state, move).drop(1)) {
                val changed = change(candidate, actor, command.copy(action = GameAction.Play(reset)))
                if (changed.reason != null) return changed
                candidate = changed.state!!
                transition = transition.copy(fields = transition.fields + changed.mutation?.transition?.fields.orEmpty())
            }
        }
        val next = candidate.board.copy(stackEffects = candidate.board.stackEffects.filterNot { it.id == entry },
            containers = candidate.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = it.cards - entry) else it })
        return record(state, actor, command, next, transition)
    }

    private fun move(state: PlayState, actor: String, command: GameCommand, action: PlayAction.MoveCard,
        // Version 3 preserves Hand order/gaps; version 4 includes temporary hand-projection visibility;
        // version 5 expires reveal knowledge when a card leaves its display;
        // version 6 keeps cards upright outside the Battlefield.
        // Old recorded disclosures must still replay using the visibility rules they were made under.
        coverRulesVersion: Int = 7): GameplayChange {
        if (action.removeStackEntryId != null) return moveProjection(state, actor, command, action, coverRulesVersion)
        fun reject(reason: String) = GameplayChange(reason = reason)
        val board = state.board
        val card = board.cards.singleOrNull { it.id == action.cardId } ?: return reject("missingReference")
        val source = board.containers.singleOrNull { it.id == action.source.containerId } ?: return reject("missingReference")
        val target = board.containers.singleOrNull { it.id == action.destination.containerId } ?: return reject("missingReference")
        if (card.containerId != source.id || card.id !in source.cards || card.placement != action.source.placement)
            return reject("movementConflict")
        val sourceMembers = members(board, source, card.placement)
        if (action.source.edge != MoveSourceEdge.MEMBER && source.zone != PlayZone.LIBRARY) return reject("invalidInput")
        if ((action.source.edge == MoveSourceEdge.TOP && sourceMembers.firstOrNull() != card.id) ||
            (action.source.edge == MoveSourceEdge.BOTTOM && sourceMembers.lastOrNull() != card.id)) return reject("movementConflict")
        val slice = action.source.bottomSlice
        if (slice.isNotEmpty() && (source.zone != PlayZone.LIBRARY || slice.first() != card.id ||
                sourceMembers.takeLast(slice.size) != slice)) return reject("movementConflict")
        val destination = action.destination
        val addingStack = target.zone == PlayZone.STACK && source.zone != PlayZone.STACK
        if (addingStack && action.expectedStackDigest != stackDigest(state)) return reject("stackConflict")
        if (!addingStack && action.expectedStackDigest != null) return reject("invalidInput")
        TokenLinkRules.disappearForMove(state, actor, command, action)?.let { return it }
        val placement: PlayPlacement?
        val reduced = target.cards.filter { it != card.id }
        val ordered: List<String>
        when (target.zone) {
            PlayZone.BATTLEFIELD -> {
                if (destination.insertion !in setOf(MoveInsertion.EMPTY, MoveInsertion.PILE_TOP, MoveInsertion.PILE_BOTTOM, MoveInsertion.PILE_GAP))
                    return reject("invalidInput")
                val position = destination.position ?: return reject("invalidInput")
                if (destination.insertion != MoveInsertion.PILE_GAP && (destination.afterId != null || destination.beforeId != null)) return reject("invalidInput")
                if (destination.insertion == MoveInsertion.EMPTY) {
                    if (destination.pileId != null) return reject("invalidInput")
                    val others = reduced.map { id -> board.cards.single { it.id == id } }
                    if (others.any { it.placement?.position == position }) return reject("movementConflict")
                    // An unchanged solo placement is a no-op, retaining its stable pile identity.
                    if (source.id == target.id && card.placement?.position == position && sourceMembers.size == 1 && action.faceDown == null)
                        return GameplayChange(state)
                    val pile = if (source.id == target.id && card.placement?.position == position && sourceMembers.size == 1)
                        card.placement.pileId else CommandIds.uuidV5(command.requestId, "pile/0")
                    if (board.cards.any { it.id != card.id && it.placement?.pileId == pile }) return reject("inapplicable")
                    placement = PlayPlacement(position, pile)
                    ordered = emptyList()
                } else {
                    val pile = destination.pileId ?: return reject("invalidInput")
                    placement = PlayPlacement(position, pile)
                    ordered = members(board, target, placement).filter { it != card.id }
                    if (ordered.isEmpty() || board.cards.none { it.id in ordered && it.placement == placement }) return reject("movementConflict")
                    if (ordered.size >= 3) return reject("movementConflict")
                    if (destination.insertion == MoveInsertion.PILE_GAP) {
                        val addressed = destination.targetCardId ?: return reject("invalidInput")
                        if (addressed !in ordered) return reject("movementConflict")
                    }
                }
            }
            PlayZone.LIBRARY, PlayZone.STACK -> {
                if (destination.insertion !in setOf(MoveInsertion.TOP, MoveInsertion.BOTTOM, MoveInsertion.GAP)) return reject("invalidInput")
                if (destination.position != null || destination.pileId != null) return reject("invalidInput")
                if (destination.insertion != MoveInsertion.GAP && (destination.afterId != null || destination.beforeId != null)) return reject("invalidInput")
                placement = null; ordered = reduced
            }
            PlayZone.HAND -> {
                if (coverRulesVersion < 3) {
                    if (destination.insertion != MoveInsertion.UNORDERED || destination.position != null || destination.pileId != null ||
                        destination.afterId != null || destination.beforeId != null) return reject("invalidInput")
                    placement = null; ordered = emptyList()
                } else {
                    if (destination.insertion !in setOf(MoveInsertion.GAP, MoveInsertion.UNORDERED) ||
                        destination.position != null || destination.pileId != null ||
                        destination.insertion != MoveInsertion.GAP && (destination.afterId != null || destination.beforeId != null)) return reject("invalidInput")
                    if (source.id == target.id && target.accountId != actor) return reject("inapplicable")
                    placement = null; ordered = reduced
                }
            }
            PlayZone.GRAVEYARD, PlayZone.EXILE -> {
                if (destination.insertion != MoveInsertion.UNORDERED || destination.position != null || destination.pileId != null ||
                    destination.afterId != null || destination.beforeId != null) return reject("invalidInput")
                placement = null; ordered = emptyList()
            }
        }
        val index = when (destination.insertion) {
            MoveInsertion.TOP, MoveInsertion.PILE_TOP, MoveInsertion.EMPTY, MoveInsertion.UNORDERED -> 0
            MoveInsertion.BOTTOM, MoveInsertion.PILE_BOTTOM -> ordered.size
            MoveInsertion.GAP -> {
                if (card.id in destination.pendingIds || destination.afterId in destination.pendingIds ||
                    destination.beforeId in destination.pendingIds) return reject("invalidInput")
                val reducedGap = ordered.filterNot { it in destination.pendingIds }
                val left = destination.afterId?.let { reducedGap.indexOf(it).takeIf { index -> index >= 0 }
                    ?: return reject("movementConflict") } ?: -1
                if (reducedGap.getOrNull(left + 1) != destination.beforeId) return reject("movementConflict")
                destination.afterId?.let { ordered.indexOf(it) + 1 } ?: 0
            }
            MoveInsertion.PILE_GAP -> {
                val neighbors = ordered.filter { it !in destination.pendingIds }
                val left = destination.afterId?.let { neighbors.indexOf(it).takeIf { index -> index >= 0 }
                    ?: return reject("movementConflict") } ?: -1
                if (neighbors.getOrNull(left + 1) != destination.beforeId) return reject("movementConflict")
                // Insert after the actual prefix, not at the projected index (pending members remain).
                destination.afterId?.let { ordered.indexOf(it) + 1 } ?: 0
            }
        }
        val inserted = ordered.toMutableList().also { it.add(index, card.id) }
        val targetCards = when (target.zone) {
            PlayZone.BATTLEFIELD -> reduced.filter { it !in ordered } + inserted
            PlayZone.HAND -> if (coverRulesVersion < 3) (reduced + card.id).sorted()
                else if (destination.insertion == MoveInsertion.GAP) inserted else reduced + card.id
            PlayZone.GRAVEYARD, PlayZone.EXILE -> (reduced + card.id).sorted()
            else -> inserted
        }
        val hiddenZone = target.zone == PlayZone.LIBRARY || target.zone == PlayZone.HAND
        val leaving = coverRulesVersion >= 7 && source.zone == PlayZone.BATTLEFIELD && target.zone != PlayZone.BATTLEFIELD
        val faceDown = !hiddenZone && if (leaving) target.zone == PlayZone.EXILE && card.isFaceDown
            else (action.faceDown ?: (source.zone == PlayZone.BATTLEFIELD && target.zone == PlayZone.BATTLEFIELD && card.isFaceDown))
        val front = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces?.firstOrNull()
        val moved = card.copy(containerId = target.id, placement = placement,
            note = if (coverRulesVersion >= 7 && source.id != target.id &&
                (source.zone != PlayZone.BATTLEFIELD || target.zone != PlayZone.BATTLEFIELD)) "" else card.note,
            corner = if (leaving) front?.defaultCorner().orEmpty() else card.corner,
            counters = if (leaving) emptyMap() else card.counters,
            faceId = if (leaving) front?.faceId ?: card.faceId else card.faceId,
            owner = if (leaving) originalOwner(state, card) else card.owner,
            knownToAll = coverRulesVersion < 5 && card.knownToAll && (source.id == target.id || board.contentsDisplays.any {
                card.id in it.cardIds && (it.reveal || card.id in it.revealedIds) && hiddenZone &&
                    (target.zone == PlayZone.HAND || destination.insertion == MoveInsertion.TOP) }),
            tapped = if (source.zone == PlayZone.STACK || target.zone == PlayZone.STACK ||
                coverRulesVersion >= 6 && target.zone != PlayZone.BATTLEFIELD) false else card.tapped,
            covered = if (coverRulesVersion == 1) card.covered else hiddenZone || faceDown,
            explicitlyCovered = if (coverRulesVersion == 1) card.explicitlyCovered else faceDown)
        val removedLinks = if (leaving) board.links.filter { it.first.id == card.id || it.second.id == card.id } else emptyList()
        val next = canonicalBoard(board.copy(cards = board.cards.map { if (it.id == card.id) moved else it },
            links = board.links - removedLinks.toSet(),
            containers = board.containers.map { when (it.id) {
                target.id -> it.copy(cards = targetCards)
                source.id -> it.copy(cards = it.cards.filter { id -> id != card.id })
                else -> it
            } }))
        val from = location(board, card.id, orderedHand = coverRulesVersion >= 3)
        val to = location(next, card.id, orderedHand = coverRulesVersion >= 3)
        if (from == to && card == moved && board.contentsDisplays.none { card.id in it.cardIds } &&
            LibraryKnowledge.after(board, next, action) == board) return GameplayChange(state)
        val stackChanged = source.zone == PlayZone.STACK || target.zone == PlayZone.STACK
        val fields = listOf(
            GameplayFieldChange(card.id, GameplayField.TAPPED, card.tapped.toString(), moved.tapped.toString()),
            GameplayFieldChange(card.id, GameplayField.COVERED, card.covered.toString(), moved.covered.toString()),
            GameplayFieldChange(card.id, GameplayField.EXPLICITLY_COVERED, card.explicitlyCovered.toString(), moved.explicitlyCovered.toString()),
            GameplayFieldChange(card.id, GameplayField.NOTE, card.note, moved.note),
            GameplayFieldChange(card.id, GameplayField.CORNER, card.corner, moved.corner),
            GameplayFieldChange(card.id, GameplayField.FACE, card.faceId, moved.faceId),
            GameplayFieldChange(card.id, GameplayField.OWNER, card.owner, moved.owner)
        ).filter { it.from != it.to } + card.counters.entries.sortedBy { it.key.name }.mapNotNull { (color, value) ->
            if (color in moved.counters) null else GameplayFieldChange(card.id, GameplayField.COUNTER, value, null, color) }
        fun viewers(board: BoardState, card: PlayCard): List<String> {
            if (coverRulesVersion >= 4) return CardVisibility.viewers(board, card)
            val container = board.containers.single { it.id == card.containerId }
            return board.players.map { it.accountId }.filter {
                CardVisibility.isVisibleFaceUp(card, container.zone, container.accountId, it) }.sorted()
        }
        val newViewers = if (coverRulesVersion == 1) emptyList() else viewers(next, moved) - viewers(board, card).toSet()
        val transition = GameplayTransition(fields = fields, links = removedLinks.map { LinkChange(it, null) },
            disclosures = if (newViewers.isEmpty()) emptyList() else listOf(
                GameplayDisclosure(card.id, moved.faceId, target.id, newViewers)),
            movement = GameplayMoveChange(card.id, from, to,
            if (target.zone == PlayZone.BATTLEFIELD) ordered.size else null,
            if (stackChanged) stackDigest(state) else null,
            if (stackChanged) stackDigest(state.copy(board = next)) else null, coverRulesVersion))
        return record(state, actor, command, next, transition, contentsRulesVersion = if (coverRulesVersion >= 5) 2 else 1)
    }

    /** Initial shuffle membership is immutable deck provenance, independent of edited owner/region.
     * This is bounded by deck size and never consults game history. Tokens have no deck provenance. */
    internal fun originalOwner(state: PlayState, card: PlayCard): String =
        state.shuffles.firstOrNull { card.id in it.order }?.accountId ?: card.owner

    internal fun members(board: BoardState, container: PlayContainer, placement: PlayPlacement?): List<String> =
        if (container.zone == PlayZone.BATTLEFIELD) container.cards.filter { id -> board.cards.single { it.id == id }.placement == placement }
        else container.cards

    /** Battlefield inventory order between piles is nonsemantic; canonical pile IDs retain exact replay bytes.
     * Within each pile the original top-first member order remains semantic and is never sorted by card ID.
     */
    internal fun canonicalBoard(board: BoardState): BoardState {
        val placements = board.cards.associate { it.id to it.placement }
        return board.copy(containers = board.containers.map { container -> if (container.zone == PlayZone.BATTLEFIELD)
            container.copy(cards = container.cards.sortedBy { placements[it]?.pileId }) else container })
    }

    internal fun location(board: BoardState, cardId: String, orderedHand: Boolean = false): CardLocation {
        val card = board.cards.single { it.id == cardId }
        val container = board.containers.single { it.id == card.containerId }
        val ordered = container.zone in setOf(PlayZone.LIBRARY, PlayZone.STACK, PlayZone.BATTLEFIELD) || orderedHand && container.zone == PlayZone.HAND
        val members = if (ordered) members(board, container, card.placement) else emptyList()
        val index = members.indexOf(cardId)
        return CardLocation(container.id, card.placement, if (ordered) members.getOrNull(index - 1) else null,
            if (ordered) members.getOrNull(index + 1) else null)
    }
}

/** Text-first signed decimal arithmetic without platform BigInteger or a machine-integer gameplay cap. */
internal object GameplayNumbers {
    fun integer(value: String) = value.matches(Regex("[+-]?[0-9]+"))
    fun add(left: String, right: String): String {
        fun magnitude(s: String) = s.removePrefix("-").removePrefix("+").trimStart('0').ifEmpty { "0" }
        val a = magnitude(left); val b = magnitude(right)
        val negativeA = left.startsWith('-') && a != "0"; val negativeB = right.startsWith('-') && b != "0"
        val result: String; val negative: Boolean
        if (negativeA == negativeB) {
            var carry = 0; val digits = StringBuilder()
            for (i in 0 until maxOf(a.length, b.length)) {
                val sum = (a.getOrNull(a.lastIndex - i)?.digitToInt() ?: 0) + (b.getOrNull(b.lastIndex - i)?.digitToInt() ?: 0) + carry
                digits.append(sum % 10); carry = sum / 10
            }
            if (carry != 0) digits.append(carry)
            result = digits.reverse().toString(); negative = negativeA
        } else {
            val comparison = if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
            val high = if (comparison >= 0) a else b; val low = if (comparison >= 0) b else a
            var borrow = 0; val digits = StringBuilder()
            for (i in high.indices) {
                var value = high[high.lastIndex - i].digitToInt() - (low.getOrNull(low.lastIndex - i)?.digitToInt() ?: 0) - borrow
                borrow = if (value < 0) 1 else 0
                if (value < 0) value += 10
                digits.append(value)
            }
            result = digits.reverse().toString().trimStart('0').ifEmpty { "0" }
            negative = if (comparison >= 0) negativeA else negativeB
        }
        return if (negative && result != "0") "-$result" else result
    }
}
