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
    /** The session adapter queues this selection as one atomic invocation. */
    constructor(current: () -> PlayState?, submit: (PlayAction) -> Boolean,
        submitInvocation: (List<PlayAction>) -> Boolean) : this(current, submit) {
        this.submitInvocation = submitInvocation
    }
    var scope by mutableStateOf<TokenLinkScope?>(null)
        private set
    var projectionScope by mutableStateOf<ProjectionLinkScope?>(null)
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
        if (ids.any { id -> play.board.cards.none { it.id == id } }) return
        close()
        playId = play.id
        scope = TokenLinkScope(contextCardId, ids.toList())
    }

    fun close() {
        scope = null; projectionScope = null; showingLinks = false; showingCatalog = false; error = null; playId = null
    }

    fun openOccurrences(context: ProjectionEndpoint, selected: List<ProjectionEndpoint>) {
        val play = current() ?: return
        require(DeckValidation.uuid(context.occurrenceId) && selected.all { DeckValidation.uuid(it.occurrenceId) })
        val endpoints = if (context in selected) selected.distinct() else listOf(context)
        if (endpoints.any { !CombatStackRules.exists(play.board, it) }) return
        close(); playId = play.id
        projectionScope = ProjectionLinkScope(context, endpoints.toList())
    }

    private fun boundState(): PlayState? = current()?.takeIf { it.id == playId }
    private fun cards(): List<PlayCard> {
        val board = boundState()?.board ?: return emptyList()
        return scope?.cardIds.orEmpty().mapNotNull { id -> board.cards.singleOrNull { it.id == id } }
    }

    fun clone() { sendSelection(cards().map { PlayAction.CloneToken(it.id) }) }
    fun removeTokens() { sendSelection(cards().filter { it.token }.map { PlayAction.RemoveToken(it.id) }) }
    private fun sendSelection(actions: List<PlayAction>) {
        if (actions.size == 1) send(actions.single()) else if (actions.isNotEmpty()) sendLinks(actions)
    }
    private fun occurrences(): List<ProjectionEndpoint> = boundState()?.board?.let { board ->
        projectionScope?.endpoints.orEmpty().filter { CombatStackRules.exists(board, it) }
    }.orEmpty()
    private fun hasEndpoints() = cards().isNotEmpty() || occurrences().isNotEmpty()
    fun canLink() = cards().size >= 2 || occurrences().size >= 2
    fun links() { if (hasEndpoints()) { showingLinks = true; error = null } }

    /** Every missing selected pair is queued in ONE invocation; never enter a table-pick mode. */
    fun linkSelected(): Boolean {
        val board = boundState()?.board ?: return false
        val actions = mutableListOf<PlayAction>()
        if (projectionScope != null) {
            val endpoints = occurrences()
            val existing = board.projectionLinks.map { setOf(it.first, it.second) }.toHashSet()
            for (i in endpoints.indices) for (j in i + 1 until endpoints.size) {
                if (setOf(endpoints[i].occurrenceId, endpoints[j].occurrenceId) !in existing)
                    actions += PlayAction.LinkProjections(endpoints[i], endpoints[j])
            }
        } else {
            val endpoints = cards().map { LinkEndpoint(LinkEndpointKind.CARD, it.id) }
            val existing = board.links.map { TokenLinkRules.pair(it.first, it.second) }.toHashSet()
            for (i in endpoints.indices) for (j in i + 1 until endpoints.size) {
                if (TokenLinkRules.pair(endpoints[i], endpoints[j]) !in existing)
                    actions += PlayAction.Link(endpoints[i], endpoints[j])
            }
        }
        error = null
        return if (actions.isEmpty()) canLink() else sendLinks(actions)
    }

    private fun sendLinks(actions: List<PlayAction>): Boolean {
        if (actions.isEmpty()) return false
        val batch = submitInvocation
        if (batch != null) return batch(actions).also {
            error = if (it) null else "Action could not be queued. Reconnect and try again."
        }
        error = "Action could not be queued. Reconnect and try again."
        return false // Never silently split a selection into unrelated undo invocations.
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
            sendLinks(listOf(PlayAction.RemoveProjectionLink(id)))
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
