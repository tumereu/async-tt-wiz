package org.asyncttwiz.android

import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

data class GameNameDraft(val recipient: AccountIdentity, val name: String)

/** LobbyController's invitations: naming and creating a new game with a contact, challenging from a
 * pod, and answering or repeating an invitation. Main thread; requests run on [worker]. */
internal class LobbyInvitations(private val scope: LobbyScope, private val api: Lobbies, private val deckApi: Decks?,
    private val credential: () -> String?, private val accountId: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val session: GameSession?, private val queue: LobbyActionQueue, private val suggest: () -> String,
    private val basicLandDefinitions: (() -> List<SubmittedDefinition>)?) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val epoch get() = scope.epoch
    private val latest get() = scope.latest
    private var pendingCreate: CreateInvitation? = null
    private data class PodChallenge(val podId: String, val input: ChallengeFromPod, val submission: SubmitDefinitions)
    private var pendingChallenge: PodChallenge? = null
    private var pendingRecipient: AccountIdentity? = null
    fun clear() { pendingCreate = null; pendingChallenge = null; pendingRecipient = null }
    fun respond(game: GameSummary, accept: Boolean) {
        if (game.recipient.accountId != accountId() || game.invitationState != InvitationState.PENDING || game.lifecycle != GameLifecycle.LOBBY) return
        // A held pod challenge can be declined at once; accepting waits for the player's submitted deck.
        if (accept && game.invitationHeld) return
        session?.submit(game, if (accept) GameAction.AcceptInvitation else GameAction.DeclineInvitation)
    }
    fun reinvite(game: GameSummary) {
        if (game.inviter.accountId != accountId() || game.invitationState != InvitationState.DECLINED || game.lifecycle != GameLifecycle.LOBBY) return
        queue.whenReady(game.id, "reinvite") { session?.submit(game, GameAction.Reinvite) }
    }
    fun begin(contact: AccountIdentity) {
        if (closed || state.mutating || state.contacts.none { it.accountId == contact.accountId }) return
        val pending = pendingCreate
        state = state.copy(draft = if (pending == null) GameNameDraft(contact, suggest())
            else GameNameDraft(pendingRecipient!!, pending.gameName), uncertain = pending != null, error = null)
    }
    fun name(value: String) { if (!state.mutating && pendingCreate == null) state = state.copy(draft = state.draft?.copy(name = value), error = null) }
    fun reroll() { if (!state.mutating && pendingCreate == null) name(suggest()) }
    fun dismiss() { if (!state.mutating) state = state.copy(draft = null, error = null) }
    fun create() {
        if (closed || state.mutating) return
        val draft = state.draft ?: return
        if (!LobbyValidation.name(draft.name)) { state = state.copy(error = "Enter a game name."); return }
        val token = credential() ?: return
        val input = pendingCreate ?: CreateInvitation(UUID.randomUUID().toString(), draft.recipient.accountId, draft.name)
        pendingCreate = input; pendingRecipient = draft.recipient
        val generation = epoch
        state = state.copy(mutating = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { serviceRetry(current = { generation == epoch }) { api.create(token, input) } } }
            post { if (!closed && generation == epoch) {
                val game = result.getOrNull()
                if (game != null) {
                    pendingCreate = null; pendingRecipient = null
                    state = state.copy(mutating = false, draft = null, uncertain = false)
                    // An idempotent create response is an original outcome, not current lifecycle state.
                    if (latest[game.id]?.lifecycle !in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED) && sessionAvailable(game.id)) open(game.id)
                    else refresh()
                } else {
                    val failure = result.exceptionOrNull()
                    val terminal = (failure as? LobbyFailure)?.code in listOf("invalid_name", "contact_required", "invalid_request", "request_conflict")
                    if (terminal) { pendingCreate = null; pendingRecipient = null }
                    state = state.copy(mutating = false, uncertain = !terminal, error = lobbyError(failure!!))
                }
            } }
        }
    }
    fun retryPodChallenge() {
        val request = pendingChallenge ?: return
        challengeFromPod(request.podId, request.input.opponentId, request.input.gameName)
    }
    /** Pod UI supplies a suggested name; retries retain the original name and request identities. */
    fun challengeFromPod(podId: String, opponentId: String, gameName: String) {
        if (closed || state.mutating) return
        val token = credential() ?: return
        val decks = deckApi ?: return
        val resolve = basicLandDefinitions ?: return
        val previous = pendingChallenge
        if (previous != null && (previous.podId != podId || previous.input.opponentId != opponentId)) {
            state = state.copy(error = "Retry the original pod challenge before creating another."); return
        }
        if (previous == null && !LobbyValidation.name(gameName)) { state = state.copy(error = "Enter a game name."); return }
        val generation = epoch
        state = state.copy(mutating = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            var captured = previous
            val result = runCatching { runBlocking {
                val request = captured ?: resolve().let { definitions ->
                    PodChallenge(podId, ChallengeFromPod(UUID.randomUUID().toString(), opponentId, gameName, definitions),
                        SubmitDefinitions(UUID.randomUUID().toString(), definitions))
                }.also { captured = it }
                serviceRetry(current = { generation == epoch }) { decks.submit(token, request.submission) }
                serviceRetry(current = { generation == epoch }) { api.challengeFromPod(token, request.podId, request.input) }
            } }
            post { if (!closed && generation == epoch) {
                val game = result.getOrNull()
                val failure = result.exceptionOrNull()
                val code = when (failure) { is LobbyFailure -> failure.code; is DeckFailure -> failure.code; else -> null }
                val terminal = code in listOf("not_found", "opponent_not_member", "invalid_opponent",
                    "deck_not_submitted", "invalid_name", "invalid_request", "request_conflict", "invalid_basic_definitions", "invalid_deck", "invalid_definition")
                pendingChallenge = if (game != null || terminal) null else captured
                state = state.copy(mutating = false, error = failure?.let(::lobbyError),
                    retryPodChallengeName = pendingChallenge?.input?.gameName)
                if (game != null) {
                    if (latest[game.id]?.lifecycle !in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED) && sessionAvailable(game.id)) open(game.id)
                    else refresh()
                }
            } }
        }
    }
    private fun sessionAvailable(id: String) = scope.sessionAvailable(id)
    private fun open(id: String) = scope.open(id)
    private fun refresh() = scope.refresh()
}

internal val gameNameAdjectives = listOf("Azure", "Crackling", "Moonlit", "Silver", "Arcane", "Velvet", "Ember", "Wandering",
    "Ancient", "Astral", "Amber", "Bewitched", "Celestial", "Crimson", "Crystal", "Duskborn", "Eldritch", "Enchanted",
    "Fabled", "Frosted", "Gilded", "Glimmering", "Hallowed", "Hidden", "Iridescent", "Jade", "Luminous", "Misty",
    "Mystic", "Obsidian", "Opal", "Radiant", "Runic", "Sapphire", "Shimmering", "Silent", "Starlit", "Twilight", "Verdant", "Whispering")
internal val gameNameNouns = listOf("Spark", "Mist", "Wand", "Grove", "Rune", "Prism", "Dragon", "Lantern",
    "Amulet", "Beacon", "Chalice", "Citadel", "Comet", "Crown", "Dawn", "Echo", "Familiar", "Flame", "Gale", "Gate",
    "Grimoire", "Haven", "Hydra", "Isle", "Labyrinth", "Lotus", "Moon", "Oracle", "Phoenix", "Portal", "Relic", "Sanctum",
    "Scepter", "Sigil", "Spire", "Star", "Tempest", "Thicket", "Tower", "Wisp")
internal fun gameNameSuggestion(): String = gameNameAdjectives.random() + " " + gameNameNouns.random()
