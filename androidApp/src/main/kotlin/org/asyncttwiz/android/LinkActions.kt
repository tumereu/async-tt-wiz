package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

data class LinkEndpointItem(val endpoint: LinkEndpoint, val label: String)
data class ProjectionEndpointItem(val endpoint: ProjectionEndpoint, val label: String)

object LinkActions {
    /** Covered cards do not leak names via the accessible alternative to table endpoint picking. */
    fun endpoints(play: PlayState, playerNames: Map<String, String>): List<LinkEndpointItem> =
        play.board.players.map { LinkEndpointItem(LinkEndpoint(LinkEndpointKind.PLAYER, it.accountId),
            playerNames[it.accountId] ?: "Player") } +
            play.board.cards.map { card ->
                val face = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces?.singleOrNull { it.faceId == card.faceId }
                LinkEndpointItem(LinkEndpoint(LinkEndpointKind.CARD, card.id),
                    "${if (card.covered) "Covered card" else face?.name ?: "Card"} · ${card.id.takeLast(6)}")
            }

    fun connections(play: PlayState, scope: TokenLinkScope): List<PlayLink> {
        val endpoints = scope.cardIds.map { LinkEndpoint(LinkEndpointKind.CARD, it) }.toSet()
        return play.board.links.filter { it.first in endpoints || it.second in endpoints }
    }

    fun projectionEndpoints(play: PlayState): List<ProjectionEndpointItem> =
        (CombatStackRules.occurrences(play.board.combat).map { it.id } + play.board.stackEffects.map { it.id }).map { id ->
            val card = CombatStackRules.inspection(play.board, id)
            val face = play.definitions.singleOrNull { it.digest == card?.definition }?.definition?.faces
                ?.singleOrNull { it.faceId == card?.faceId }
            ProjectionEndpointItem(ProjectionEndpoint(id),
                "${if (card?.covered == true) "Covered card" else face?.name ?: "Card"} · ${id.takeLast(6)}")
        }

    fun connections(play: PlayState, scope: ProjectionLinkScope): List<ProjectionLink> {
        val ids = scope.endpoints.map { it.occurrenceId }.toSet()
        return play.board.projectionLinks.filter { it.first in ids || it.second in ids }
    }
}

/** Removing a connection is explicit; it never removes either card. */
@Composable
fun LinksListDialog(controller: TokenLinkController, play: PlayState, playerNames: Map<String, String>, enabled: Boolean = true) {
    controller.projectionScope?.let { scope ->
        if (controller.showingLinks) ProjectionLinksListDialog(controller, play, scope, enabled)
        return
    }
    val scope = controller.scope ?: return
    if (!controller.showingLinks) return
    val labels = LinkActions.endpoints(play, playerNames).associate { it.endpoint to it.label }
    val links = LinkActions.connections(play, scope)
    TableMenuPanel(controller::close, title = "Links · ${scope.cardIds.size} captured") {
        Column {
            if (links.isEmpty()) Text("No links")
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(links, key = { it.id }) { link ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${labels[link.first] ?: "Unavailable"} — ${labels[link.second] ?: "Unavailable"}", Modifier.weight(1f))
                        ManualActionSquare("Remove link", "", enabled, onClick = {
                            controller.removeLink(link.id); if (controller.error == null) controller.close()
                        })
                    }
                }
            }
            controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ProjectionLinksListDialog(controller: TokenLinkController, play: PlayState, scope: ProjectionLinkScope, enabled: Boolean) {
    val labels = LinkActions.projectionEndpoints(play).associate { it.endpoint.occurrenceId to it.label }
    val links = LinkActions.connections(play, scope)
    TableMenuPanel(controller::close, title = "Links · ${scope.endpoints.size} captured") {
        Column {
            if (links.isEmpty()) Text("No links")
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(links, key = { it.id }) { link ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${labels[link.first] ?: "Unavailable"} — ${labels[link.second] ?: "Unavailable"}", Modifier.weight(1f))
                        ManualActionSquare("Remove link", "", enabled, onClick = {
                            controller.removeProjectionLink(link.id); if (controller.error == null) controller.close()
                        })
                    }
                }
            }
            controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
