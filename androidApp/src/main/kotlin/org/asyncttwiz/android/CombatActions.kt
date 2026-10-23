package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        TextButton(enabled = connected, onClick = {
            onPlan(CombatStackController.single(state, actor, PlayAction.Combat(CombatAction.Open), command))
        }) { Text("Create combat area") }
        TextButton(enabled = connected && capturedAreaId != null && state.board.combat?.id == capturedAreaId, onClick = {
            onPlan(CombatStackController.single(state, actor,
                PlayAction.Combat(CombatAction.Close(capturedAreaId)), command))
        }) { Text("Close combat area") }
        TextButton(enabled = connected && battlefield, onClick = {
            onPlan(CombatStackController.declare(state, actor, targets, false, command))
        }) { Text("Declare without tapping") }
        TextButton(enabled = connected && battlefield, onClick = {
            onPlan(CombatStackController.declare(state, actor, targets, true, command))
        }) { Text("Declare + tap") }
        for (tapped in listOf(true, false)) TextButton(enabled = connected && sources.any { source ->
            state.board.cards.any { it.id == source }
        }, onClick = {
            onPlan(CombatStackController.sourceActions(state, actor, targets, command) { PlayAction.SetTapped(it, tapped) })
        }) { Text(if (tapped) "Tap" else "Untap") }
        val occurrences = targets.filter { target -> CombatStackRules.occurrences(state.board.combat).any { it.id == target.viewId } }
        TextButton(enabled = connected && occurrences.isNotEmpty(), onClick = {
            onPlan(CombatStackController.remove(state, actor, occurrences, command))
        }) { Text("Remove from combat") }
    }
}

/** Accessible alternative to precision drops; each row binds the full pair/side/gap destination. */
@Composable
fun CombatDestinationActions(destinations: List<Pair<String, CombatDestination>>, enabled: Boolean,
    onDestination: (CombatDestination) -> Unit) {
    Column { for ((name, destination) in destinations) TextButton(enabled = enabled,
        onClick = { onDestination(destination) }) { Text(name) } }
}

/** Shared I20 tracking integration supplies the real relative advancement composition, not a local step write. */
@Composable
fun CombatQuickActions(state: PlayState, multiselection: Boolean, enabled: Boolean,
    onDeclare: (tap: Boolean) -> Unit, onAdvanceAndClose: (capturedAreaId: String?) -> Unit) {
    Column {
        if (CombatOfferings.declare(state.board.turn, multiselection)) {
            TextButton(enabled = enabled, onClick = { onDeclare(true) }) { Text("Declare + tap") }
            TextButton(enabled = enabled, onClick = { onDeclare(false) }) { Text("Declare without tapping") }
        }
        if (CombatOfferings.advanceAndClose(state.board.turn)) {
            val areaId = state.board.combat?.id
            TextButton(enabled = enabled, onClick = { onAdvanceAndClose(areaId) }) { Text("Advance + close combat") }
        }
    }
}
