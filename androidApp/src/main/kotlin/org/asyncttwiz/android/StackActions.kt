package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import org.asyncttwiz.protocol.*

enum class StackContextKind { EFFECT, SPELL, PERMANENT, UNCLASSIFIED }
data class StackContext(val target: CombatStackTarget, val kind: StackContextKind,
    val destination: PlayZone? = null)
data class StackMoveChoice(val label: String, val zone: PlayZone, val destination: MoveDestination)

object StackContexts {
    private val permanentTypes = setOf("Artifact", "Battle", "Creature", "Enchantment", "Land", "Planeswalker")
    private val spellTypes = setOf("Instant", "Sorcery")
    private val supertypes = setOf("Basic", "Legendary", "Snow", "World", "Ongoing")
    fun types(typeLine: String): Set<String> = typeLine.substringBefore('—').split(Regex("\\s+"))
        .filter(String::isNotEmpty).toSet()
    fun kind(typeLine: String): StackContextKind {
        val types = types(typeLine)
        val spell = types.any { it in spellTypes }
        val permanent = types.any { it in permanentTypes }
        return when {
            spell && !permanent -> StackContextKind.SPELL
            permanent && !spell && types.all { it in permanentTypes || it in supertypes } -> StackContextKind.PERMANENT
            else -> StackContextKind.UNCLASSIFIED
        }
    }
    /** Spell halves may be folded into rules text; every physical face must be a non-land permanent. */
    fun permanentOnly(definition: CardDefinition?): Boolean = definition != null && definition.faces.isNotEmpty() &&
        definition.layout !in setOf("adventure", "split", "aftermath") &&
        definition.faces.all { kind(it.typeLine) == StackContextKind.PERMANENT && "Land" !in types(it.typeLine) }
    fun destinations(context: StackContext, configured: List<StackMoveChoice>, quick: Boolean): List<StackMoveChoice> =
        if (context.target.sourceId == null) emptyList() else configured.filter {
            !quick || (context.kind != StackContextKind.EFFECT && it.zone == context.destination)
        }
    /** Explicit selected-face metadata only. No rules-text parsing, owner routing guesses or implicit top target. */
    fun context(state: PlayState, viewId: String): StackContext {
        val board = state.board
        require(viewId in CombatStackRules.stack(board).cards)
        val target = CombatStackController.bind(board, viewId)
        if (board.stackEffects.any { it.id == viewId }) return StackContext(target, StackContextKind.EFFECT)
        val card = board.cards.single { it.id == viewId }
        val kind = kind(state.definitions.find { it.digest == card.definition }?.definition?.faces
            ?.find { it.faceId == card.faceId }?.typeLine.orEmpty())
        return StackContext(target, kind, when (kind) {
            StackContextKind.SPELL -> PlayZone.GRAVEYARD
            StackContextKind.PERMANENT -> PlayZone.BATTLEFIELD
            else -> null
        })
    }
}

/** Actual occurrence controls use shared commands. Real destinations are fully chosen/bound by the
 * normal move adapter (I13/I16), not inferred from ownership or implemented as a local board edit.
 */
@Composable
fun StackActions(state: PlayState, actor: String, targets: List<CombatStackTarget>, connected: Boolean,
    command: (PlayAction) -> GameCommand, onPlan: (CombatStackPlan) -> Unit) {
    val effects = targets.filter { target -> state.board.stackEffects.any { it.id == target.viewId } }
    val canAdd = CombatStackController.sources(targets).any { source ->
        state.board.cards.find { it.id == source }?.let { card ->
            CombatStackRules.canProject(state.board, card)
        } == true
    }
    Column {
        MenuButton("Add Stack effect", enabled = connected && canAdd, onClick = {
            onPlan(CombatStackController.addEffects(state, actor, targets, command))
        })
        MenuButton("Remove Stack effect", enabled = connected && effects.isNotEmpty(), onClick = {
            onPlan(CombatStackController.remove(state, actor, effects, command))
        })
    }
}

/** Reusable quick/full contextual destination component; callback is the real shared-move boundary.
 * An unclassified actual card gets explicit manual destinations, never a guessed resolver.
 */
@Composable
fun StackDestinationActions(context: StackContext, destinations: List<StackMoveChoice>,
    enabled: Boolean, quick: Boolean = false, onMove: (boundSourceId: String, MoveDestination) -> Unit) {
    val source = context.target.sourceId ?: return
    Column { for (choice in StackContexts.destinations(context, destinations, quick)) MenuButton(choice.label, enabled = enabled,
        onClick = { onMove(source, choice.destination) }) }
}
