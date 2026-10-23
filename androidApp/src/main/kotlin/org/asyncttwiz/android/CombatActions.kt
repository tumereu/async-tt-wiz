package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.asyncttwiz.protocol.*

object CombatOfferings {
    fun declare(turn: TurnState, multiselection: Boolean) =
        turn.phase == "COMBAT" && turn.step == "DECLARE_ATTACKERS" && multiselection
    fun advanceAndClose(turn: TurnState) = turn.phase == "COMBAT" && turn.step == "END_OF_COMBAT"
}

/** Full targeted paths. Quick visibility never gates these individual tools by phase or turn player. */
@Composable
fun CombatActions(state: PlayState, actor: String, targets: List<CombatStackTarget>, connected: Boolean,
    command: (PlayAction) -> GameCommand, onPlan: (CombatStackPlan) -> Unit) {
    // A retained open menu cannot silently retarget Close after sync replaces its area.
    val capturedAreaId = remember { state.board.combat?.id }
    val sources = CombatStackController.sources(targets)
    val battlefield = sources.any { source -> state.board.cards.find { it.id == source }?.let { card ->
        state.board.containers.find { it.id == card.containerId }?.zone == PlayZone.BATTLEFIELD
    } == true }
    Column {
        MenuButton("Create combat area", enabled = connected, onClick = {
            onPlan(CombatStackController.single(state, actor, PlayAction.Combat(CombatAction.Open), command))
        })
        MenuButton("Close combat area", enabled = connected && capturedAreaId != null && state.board.combat?.id == capturedAreaId, onClick = {
            onPlan(CombatStackController.single(state, actor,
                PlayAction.Combat(CombatAction.Close(capturedAreaId)), command))
        })
        MenuButton("Declare without tapping", enabled = connected && battlefield, onClick = {
            onPlan(CombatStackController.declare(state, actor, targets, false, command))
        })
        MenuButton("Declare + tap", enabled = connected && battlefield, onClick = {
            onPlan(CombatStackController.declare(state, actor, targets, true, command))
        })
        val orientationTargets = targets.filter { it.viewId !in CombatStackRules.stack(state.board).cards }
        for (tapped in listOf(true, false)) MenuButton(if (tapped) "Tap" else "Untap", enabled = connected && orientationTargets.any { target ->
            val source = target.sourceId
            state.board.cards.any { it.id == source }
        }, onClick = {
            onPlan(CombatStackController.sourceActions(state, actor, orientationTargets, command) { PlayAction.SetTapped(it, tapped) })
        })
        val occurrences = targets.filter { target -> CombatStackRules.occurrences(state.board.combat).any { it.id == target.viewId } }
        MenuButton("Remove from combat", enabled = connected && occurrences.isNotEmpty(), onClick = {
            onPlan(CombatStackController.remove(state, actor, occurrences, command))
        })
    }
}

/** Accessible alternative to precision drops; each row binds the full pair/side/gap destination. */
@Composable
fun CombatDestinationActions(destinations: List<Pair<String, CombatDestination>>, enabled: Boolean,
    onDestination: (CombatDestination) -> Unit) {
    Column { for ((name, destination) in destinations) MenuButton(name, enabled = enabled,
        onClick = { onDestination(destination) }) }
}

/** Shared I20 tracking integration supplies the real relative advancement composition, not a local step write. */
@Composable
fun CombatQuickActions(state: PlayState, multiselection: Boolean, enabled: Boolean,
    onDeclare: (tap: Boolean) -> Unit, onAdvanceAndClose: (capturedAreaId: String?) -> Unit) {
    Column {
        if (CombatOfferings.declare(state.board.turn, multiselection)) {
            MenuButton("Declare + tap", enabled = enabled, onClick = { onDeclare(true) })
            MenuButton("Declare without tapping", enabled = enabled, onClick = { onDeclare(false) })
        }
        if (CombatOfferings.advanceAndClose(state.board.turn)) {
            val areaId = state.board.combat?.id
            MenuButton("Advance + close combat", enabled = enabled, onClick = { onAdvanceAndClose(areaId) })
        }
    }
}
