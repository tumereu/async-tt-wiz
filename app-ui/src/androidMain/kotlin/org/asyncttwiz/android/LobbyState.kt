package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

/** TRN-065: names one match's result of [game] (a game's lobby round changes with each match's end). */
fun resultKey(game: GameSummary) = "${game.id}/${game.preparationRound}"

data class LobbyState(val contacts: List<AccountIdentity> = emptyList(), val query: String = "",
    val results: List<AccountIdentity> = emptyList(), val searched: Boolean = false, val searching: Boolean = false,
    val searchError: String? = null, val games: List<GameSummary> = emptyList(), val invitations: List<GameSummary> = emptyList(),
    val selected: String? = null, val detail: GameSummary? = null, val draft: GameNameDraft? = null,
    val busy: Boolean = false, val mutating: Boolean = false, val uncertain: Boolean = false,
    val error: String? = null, val loaded: Boolean = false, val connectionMessage: String? = null,
    val connectedGames: Set<String> = emptySet(), val pendingGames: Set<String> = emptySet(),
    /** Games whose events missed while in the background have all been received (see GameSession.caughtUp). */
    val syncedGames: Set<String> = emptySet(),
    val picker: DeckPickerState? = null, val acceptedPreparation: LobbyPreparation? = null,
    val preparation: LobbyPreparation? = null, val queuedTransferCards: Long = 0,
    val preparing: Boolean = false, val transferFeedback: TransferFeedback? = null, val activityTarget: ActivityTarget? = null,
    /** The board the table draws of the selected game played on engine state (engine-responsiveness R4a). */
    val board: TableBoard? = null,
    val gameContext: GameSummary? = null, val deletion: GameDeletion? = null,
    val chatOpen: Boolean = false, val chat: ChatPresentation = ChatPresentation(),
    /** LOB-136: the message the chat opens at (held bubble); null: at the newest. */
    val chatFocus: String? = null,
    /** LOB-139: the chat was opened to write (Write in the Life panel): its input takes the focus, with the soft keyboard. */
    val chatWrite: Boolean = false,
    /** LOB-145: the tab the chat shows in a pod game (personal UI). */
    val chatTab: ChatTab = ChatTab.GAME,
    val reconnectingGames: Set<String> = emptySet(), val backgroundLoading: Boolean = false,
    val choosingOpponent: Boolean = false, val contactsTab: ContactsTab = ContactsTab.CONTACTS,
    val contactFilter: String = "", val removingContact: AccountIdentity? = null, val chatLoaded: Boolean = false,
    val retryPodChallengeName: String? = null, val ownPodDeckName: String? = null,
    /** The selected game's opponent presence, when the server sends it. */
    val opponentPresence: ReceivedPresence? = null,
    /** Games with lobby taps waiting for the game connection or an earlier request; each runs once. */
    val queuedGames: Set<String> = emptySet(),
    /** TRN-065: the results OK'd on this phone ([resultKey]), off the screen at once while the server learns it. */
    val resultsOk: Set<String> = emptySet(),
    /** Background refreshes have kept failing for a while; the shown lists may be stale. */
    val unreachable: Boolean = false) {
    val filteredContacts get() = contacts.filter { it.username.contains(contactFilter.trim(), ignoreCase = true) }
    fun tableView() = LobbyTableView(selected, detail, board, connectedGames, reconnectingGames,
        syncedGames, activityTarget, chatOpen, chat.unread, connectionMessage, error, ownPodDeckName, opponentPresence,
        chat.bubble, chat.sent)
}

/** The [LobbyState] fields an open table reads. Games-list refreshes flip busy/loading flags in the whole
 * state every few seconds; the table reads this narrow view ([LobbyController.table]) instead, so only a
 * change to one of these fields recomposes it. */
data class LobbyTableView(val selected: String?, val detail: GameSummary?, val board: TableBoard?, val connectedGames: Set<String>, val reconnectingGames: Set<String>,
    val syncedGames: Set<String>, val activityTarget: ActivityTarget?, val chatOpen: Boolean, val chatUnread: Int,
    val connectionMessage: String?, val error: String?, val ownPodDeckName: String?, val opponentPresence: ReceivedPresence?,
    val chatBubble: IncomingChatBubble? = null, val chatSent: SentChat? = null)

/** [LobbyController.state] and its narrow table view, written together. An equal view is not written, so
 * the busy/loading flips of a list refresh never invalidate the table. (Not derivedStateOf: a pending
 * derived invalidation still recomposes a child whose parent recomposes in the same frame.) */
class LobbyStateHolder : MutableState<LobbyState> {
    private val whole = mutableStateOf(LobbyState())
    val table = mutableStateOf(LobbyState().tableView(), structuralEqualityPolicy())
    override var value: LobbyState
        get() = whole.value
        set(next) { whole.value = next; table.value = next.tableView() }
    override fun component1() = value
    override fun component2(): (LobbyState) -> Unit = { value = it }
}
