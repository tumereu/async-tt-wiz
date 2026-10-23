package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

data class TokenLinkScope(val contextCardId: String, val cardIds: List<String>)
data class ProjectionLinkScope(val context: ProjectionEndpoint, val endpoints: List<ProjectionEndpoint>)

/** Personal captured input only. The callback must enqueue through the real game session, not edit a board.
 * Retain this controller in the table's ViewModel. The current-state supplier follows prediction/rebase.
 * Source actions and occurrence links have separate captures; no occurrence is normalized to its source.
 */
class TokenLinkController(private val current: () -> PlayState?, private val submit: (PlayAction) -> Boolean) {
    private var submitInvocation: ((List<PlayAction>) -> Boolean)? = null
    /** The session adapter allocates ONE invocation and individual request IDs, queues without waiting
     * for results, and reports local enqueue failure only. It must not introduce an atomic bulk command. */
    constructor(current: () -> PlayState?, submit: (PlayAction) -> Boolean,
        submitInvocation: (List<PlayAction>) -> Boolean) : this(current, submit) {
        this.submitInvocation = submitInvocation
    }
    var scope by mutableStateOf<TokenLinkScope?>(null)
        private set
    var projectionScope by mutableStateOf<ProjectionLinkScope?>(null)
        private set
    var pickingLink by mutableStateOf(false)
        private set
    var showingLinks by mutableStateOf(false)
        private set
    var showingCatalog by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var playId: String? = null

    fun open(contextCardId: String, selectedCardIds: List<String>) {
        val play = current() ?: return
        require(DeckValidation.uuid(contextCardId) && selectedCardIds.all(DeckValidation::uuid))
        val ids = if (contextCardId in selectedCardIds) selectedCardIds.distinct() else listOf(contextCardId)
        require(ids.all { id -> play.board.cards.any { it.id == id } })
        close()
        playId = play.id
        scope = TokenLinkScope(contextCardId, ids.toList())
    }

    fun close() {
        scope = null; projectionScope = null; pickingLink = false; showingLinks = false; showingCatalog = false; error = null; playId = null
    }

    fun openOccurrences(context: ProjectionEndpoint, selected: List<ProjectionEndpoint>) {
        val play = current() ?: return
        require(DeckValidation.uuid(context.occurrenceId) && selected.all { DeckValidation.uuid(it.occurrenceId) })
        val endpoints = if (context in selected) selected.distinct() else listOf(context)
        require(endpoints.all { CombatStackRules.exists(play.board, it) })
        close(); playId = play.id
        projectionScope = ProjectionLinkScope(context, endpoints.toList())
    }

    private fun boundState(): PlayState? = current()?.takeIf { it.id == playId }
    private fun cards(): List<PlayCard> {
        val board = boundState()?.board ?: return emptyList()
        return scope?.cardIds.orEmpty().mapNotNull { id -> board.cards.singleOrNull { it.id == id } }
    }

    fun clone() { cards().forEach { send(PlayAction.CloneToken(it.id)) } }
    fun removeTokens() { cards().filter { it.token }.forEach { send(PlayAction.RemoveToken(it.id)) } }
    private fun occurrences(): List<ProjectionEndpoint> = boundState()?.board?.let { board ->
        projectionScope?.endpoints.orEmpty().filter { CombatStackRules.exists(board, it) }
    }.orEmpty()
    private fun hasEndpoints() = cards().isNotEmpty() || occurrences().isNotEmpty()
    fun beginLink() { if (hasEndpoints()) { pickingLink = true; showingLinks = false; error = null } }
    fun cancelLink() { pickingLink = false; error = null }
    fun links() { if (hasEndpoints()) { showingLinks = true; pickingLink = false; error = null } }

    /** Table hit handlers call this FIRST. True consumes the tap, including a now-absent target. */
    fun pick(endpoint: LinkEndpoint): Boolean {
        if (!pickingLink) return false
        if (projectionScope != null) return true // Consume incompatible hits; never create mixed links.
        require(DeckValidation.uuid(endpoint.id))
        val board = boundState()?.board
        if (board == null || !TokenLinkRules.exists(board, endpoint)) {
            error = "This endpoint is no longer available."
            return true
        }
        val actions = cards().map { LinkEndpoint(LinkEndpointKind.CARD, it.id) }.filter { it != endpoint }
            .map { PlayAction.Link(it, endpoint) }
        val sent = sendLinks(actions, requireInvocation = false)
        if (sent) pickingLink = false
        return true
    }

    /** Call before ordinary table taps. Frozen effects remain valid endpoints in their own right. */
    fun pick(endpoint: ProjectionEndpoint): Boolean {
        if (!pickingLink) return false
        if (projectionScope == null) return true
        require(DeckValidation.uuid(endpoint.occurrenceId))
        val board = boundState()?.board
        if (board == null || !CombatStackRules.exists(board, endpoint)) {
            error = "This endpoint is no longer available."
            return true
        }
        val actions = occurrences().filter { it != endpoint }.map { PlayAction.LinkProjections(it, endpoint) }
        if (sendLinks(actions, requireInvocation = true)) pickingLink = false
        return true
    }

    private fun sendLinks(actions: List<PlayAction>, requireInvocation: Boolean): Boolean {
        if (actions.isEmpty()) return false
        val batch = submitInvocation
        if (batch != null) return batch(actions).also {
            error = if (it) null else "Action could not be queued. Reconnect and try again."
        }
        // Legacy card-only callers remain source-compatible. Occurrence fanout requires the real
        // session invocation adapter rather than silently inventing unrelated invocation identities.
        if (requireInvocation) { error = "Action could not be queued. Reconnect and try again."; return false }
        var sent = false
        actions.forEach { sent = send(it) || sent }
        return sent
    }

    fun removeLink(id: String) {
        require(DeckValidation.uuid(id))
        val board = boundState()?.board ?: return
        val endpoints = scope?.cardIds.orEmpty().map { LinkEndpoint(LinkEndpointKind.CARD, it) }.toSet()
        if (board.links.any { it.id == id && (it.first in endpoints || it.second in endpoints) }) send(PlayAction.RemoveLink(id))
    }

    fun removeProjectionLink(id: String) {
        require(DeckValidation.uuid(id))
        val board = boundState()?.board ?: return
        val endpoints = projectionScope?.endpoints.orEmpty().map { it.occurrenceId }.toSet()
        if (board.projectionLinks.any { it.id == id && (it.first in endpoints || it.second in endpoints) })
            sendLinks(listOf(PlayAction.RemoveProjectionLink(id)), requireInvocation = true)
    }

    fun catalog() {
        val play = current() ?: return
        close(); playId = play.id; showingCatalog = true
    }
    fun create(definition: SubmittedDefinition) {
        if (showingCatalog && boundState() != null && TokenLinkRules.validDefinition(definition) && send(PlayAction.CreateToken(definition)))
            showingCatalog = false
    }
    private fun send(action: PlayAction): Boolean = submit(action).also {
        error = if (it) null else "Action could not be queued. Reconnect and try again."
    }
}
