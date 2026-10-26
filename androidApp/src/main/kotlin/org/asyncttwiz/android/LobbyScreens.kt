package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun gameDate(game: GameSummary): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM uuuu").withZone(ZoneId.systemDefault()).format(Instant.parse(game.createdAt))
}.getOrDefault("")
internal fun gameStatus(game: GameSummary): String = if (game.lifecycle == GameLifecycle.ACTIVE) "In play" else when (game.invitationState) {
    InvitationState.PENDING -> "Invitation pending"
    InvitationState.DECLINED -> "Invitation declined"
    InvitationState.ACCEPTED -> "Preparing"
}
internal fun GameSummary.opponent(accountId: String) = if (inviter.accountId == accountId) recipient else inviter
internal fun readyReason(preparation: LobbyPreparation?, accountId: String): String? = when {
    preparation?.players?.find { it.accountId == accountId }?.deck == null -> "Select your deck to get ready."
    PodGamePreparation.readyProblem(preparation, accountId) == "mainTooSmall" -> "Main must contain at least 40 cards including basics."
    PodGamePreparation.readyProblem(preparation, accountId) == "sideboardLimit" -> "Side exceeds this pod's sideboard limit (${preparation?.podRules?.sideboardLimit}). Move cards to Main."
    else -> null
}

@Composable
internal fun ContactsContent(controller: LobbyController) {
    val state = controller.state
    ScreenBusyOverlay(if (state.mutating) "Saving changes…" else null) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.contactsTab.ordinal) {
            ContactsTab.entries.forEach { tab -> Tab(selected = state.contactsTab == tab,
                onClick = { controller.contactsTab(tab) }, text = { Text(if (tab == ContactsTab.CONTACTS) "Contacts" else "Search") }) }
        }
        InitialLoadArea(state.loaded, Modifier.weight(1f).fillMaxWidth(), state.error != null, controller::refresh, loading = state.busy) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                if (state.contactsTab == ContactsTab.CONTACTS) {
                    ScreenTextField(state.contactFilter, controller::filterContacts, Modifier.fillMaxWidth()
                        .semantics { contentDescription = "Filter contacts" }, placeholder = "Filter contacts")
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        if (state.filteredContacts.isEmpty()) item { Text(if (state.contacts.isEmpty()) "No saved contacts." else "No matching contacts.", Modifier.padding(vertical = 12.dp)) }
                        items(state.filteredContacts, key = { it.accountId }) { contact ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .then(if (state.choosingOpponent) Modifier.clickable(enabled = !state.mutating) { controller.begin(contact) } else Modifier),
                                verticalAlignment = Alignment.CenterVertically) {
                                Avatar(contact); Spacer(Modifier.width(8.dp))
                                Text(contact.username, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                if (!state.choosingOpponent) ContactActionIcon("Remove contact", "delete", !state.mutating) { controller.requestRemoveContact(contact) }
                                else EntryIcon(null, "chevron")
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScreenTextField(state.query, controller::query, Modifier.weight(1f).semantics { contentDescription = "Search usernames" }, placeholder = "Search usernames")
                        ScreenIconButton("Search players", "search", controller::search, enabled = !state.searching)
                    }
                    InitialLoadArea(!state.searching || state.searched, Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (state.searched && state.searchError == null && state.results.isEmpty()) item { Text("No players found.") }
                            items(state.results, key = { it.accountId }) { contact ->
                                val added = state.contacts.any { it.accountId == contact.accountId }
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(contact); Spacer(Modifier.width(8.dp))
                                    Text(contact.username, Modifier.weight(1f))
                                    ContactActionIcon(if (added) "Added contact" else "Add contact", if (added) "ready" else "person", !state.mutating && !added) { controller.add(contact) }
                                    if (state.choosingOpponent) ContactActionIcon("Invite", "send", !state.mutating, primary = true) { controller.invite(contact) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    state.removingContact?.let { contact -> ScreenDialog(controller::dismissRemoveContact) {
        Text("Remove ${contact.username}?", style = MaterialTheme.typography.titleMedium)
        Text("Existing games and invitations will not change.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenOutlinedButton(controller::dismissRemoveContact, Modifier.weight(1f)) { Text("Cancel") }
            ScreenButton(controller::removeContact, Modifier.weight(1f)) { Text("Remove") }
        }
    } }
    GameNameDialog(controller)
}

@Composable
private fun ContactActionIcon(label: String, icon: String, enabled: Boolean, primary: Boolean = false, action: () -> Unit) {
    var tooltip by remember { mutableStateOf(false) }
    val currentAction by rememberUpdatedState(action)
    Box(Modifier.size(48.dp).semantics {
        contentDescription = label; role = Role.Button
        if (!enabled) disabled()
        onClick { if (enabled) action(); enabled }
    }.pointerInput(enabled) {
        detectTapGestures(onPress = { try { tryAwaitRelease() } finally { tooltip = false } },
            onTap = { if (enabled) currentAction() }, onLongPress = { tooltip = true })
    }, contentAlignment = Alignment.Center) {
        Icon(screenIcon(icon), null, Modifier.size(22.dp), tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        if (tooltip) HoldTooltip(label)
    }
}

private fun lobbyBusyMessage(state: LobbyState, loading: String): String? = when {
    state.searching -> "Searching players…"
    state.mutating -> if (state.draft != null) "Creating game…" else "Saving changes…"
    state.pendingGames.isNotEmpty() -> "Checking game outcome…"
    state.busy && !state.backgroundLoading && !state.loaded -> loading
    else -> null
}

@Composable
internal fun GamesContent(controller: LobbyController, accountId: String) {
    val state = controller.state
    ScreenBusyOverlay(lobbyBusyMessage(state, "Loading games…")) {
    InitialLoadArea(state.loaded, failed = state.error != null, retry = controller::refresh, loading = state.busy) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row { Text("GAMES", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); Text(state.games.size.toString(), style = MaterialTheme.typography.bodySmall) }
                    HorizontalDivider()
                    if (state.loaded && state.games.isEmpty()) Text("No games yet.")
                    GameRows(state.games, controller, accountId, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row { Text("Invitations", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); Text("${state.invitations.size} pending", style = MaterialTheme.typography.bodySmall) }
                    HorizontalDivider()
                    if (state.loaded && state.invitations.isEmpty()) Text("No pending invitations.")
                    for (game in state.invitations) Surface(color = ScreenBackground, border = BorderStroke(1.dp, ScreenBorder), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(game.gameName, fontWeight = FontWeight.SemiBold)
                            game.podName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Avatar(game.inviter)
                                Text(game.inviter.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${gameStatus(game)} · Created ${gameDate(game)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScreenButton(onClick = { controller.respond(game, true) }, enabled = game.id in state.connectedGames && game.id !in state.pendingGames,
                                    modifier = Modifier.weight(1f)) { EntryIcon(null, "ready"); Spacer(Modifier.width(8.dp)); Text("Accept") }
                                ScreenOutlinedButton(onClick = { controller.respond(game, false) }, enabled = game.id in state.connectedGames && game.id !in state.pendingGames,
                                    modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    EntryIcon(null, "close"); Spacer(Modifier.width(8.dp)); Text("Decline")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
internal fun GameDeletionDialogs(controller: LobbyController) {
    controller.state.gameContext?.let { game ->
        ScreenDialog(controller::dismissGameContext, Modifier.widthIn(max = 300.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.gameName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                ScreenIconButton("Close game actions", "close", controller::dismissGameContext)
            }
            TextButton(onClick = { controller.requestGameDeletion(game) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                EntryIcon(null, "delete"); Spacer(Modifier.width(8.dp)); Text("Delete game")
            }
        }
    }
    controller.state.deletion?.let { capture ->
        ScreenDialog(controller::dismissGameDeletion) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(capture.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                ScreenIconButton("Keep game", "close", controller::dismissGameDeletion)
            }
            Text(capture.warning)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenOutlinedButton(onClick = controller::dismissGameDeletion, Modifier.weight(1f)) { EntryIcon(null, "close"); Spacer(Modifier.width(6.dp)); Text("Keep game") }
                ScreenOutlinedButton(onClick = { controller.confirmGameDeletion() }, Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    EntryIcon(null, "delete"); Spacer(Modifier.width(6.dp)); Text("Delete game")
                }
            }
        }
    }
}

@Composable
internal fun WaitingLobby(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean = false,
    importDeck: (() -> Unit)? = null) {
    val state = controller.state
    BackHandler { controller.back() }
    if (state.detail?.lifecycle == GameLifecycle.ACTIVE) {
        val reconnecting = state.detail.id in state.reconnectingGames
        var dismissedReconnect by remember(state.detail.id, reconnecting) { mutableStateOf(false) }
        TableScreen(controller, accountId, hamburger, landscape)
        if (reconnecting && state.play != null && !dismissedReconnect)
            ReconnectingDialog(controller::back, dismiss = { dismissedReconnect = true })
        return
    }
    if (state.picker != null) { LobbyDeckPicker(controller, importDeck, hamburger); return }
    if (state.preparing && state.detail?.lifecycle == GameLifecycle.LOBBY) {
        PreparationScreen(controller, accountId, landscape, hamburger); return
    }
    ScreenBusyOverlay(lobbyBusyMessage(state, "Loading game…")) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(state.detail?.gameName ?: "Loading game…", hamburger) {
            state.detail?.takeIf(controller::offersSetupCancellation)?.let { game ->
                ScreenIconButton("Cancel game", "cancel", { controller.requestCancelSetup(game) }, danger = true)
            }
        }
        InitialLoadArea(state.detail != null, Modifier.weight(1f).fillMaxWidth(), state.error != null, controller::refresh, loading = state.busy) {
        Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.detail?.let { game ->
                val ownPlayer = state.preparation?.players?.find { it.accountId == accountId }
                val status = if (ownPlayer?.ready == true && game.invitationState == InvitationState.ACCEPTED)
                    "Waiting for ${game.opponent(accountId).username}" else gameStatus(game)
                Text("$status · Created ${gameDate(game)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val own: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedCard(modifier) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerReadiness(if (game.inviter.accountId == accountId) game.inviter else game.recipient, ownPlayer?.ready == true)
                        PreparedSummary(state.preparation, accountId)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             if (game.podId == null && state.preparation?.podRules == null) ScreenOutlinedButton(onClick = controller::chooseDeck, enabled = game.lifecycle == GameLifecycle.LOBBY &&
                                game.id in state.connectedGames && game.id !in state.pendingGames && state.preparation != null,
                                 modifier = Modifier.weight(1f)) {
                                EntryIcon(null, "decks"); Spacer(Modifier.width(8.dp))
                                Text(if (ownPlayer?.deck == null) "Choose deck" else "Change deck")
                            }
                                 ScreenOutlinedButton(onClick = controller::prepareDeck, Modifier.weight(1f), enabled = ownPlayer?.deck != null && game.lifecycle == GameLifecycle.LOBBY) {
                                    EntryIcon(null, "prepare"); Spacer(Modifier.width(8.dp)); Text("Prepare deck")
                                }
                        }
                    } }
                }
                val opponent: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedCard(modifier) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerReadiness(game.opponent(accountId),
                            state.preparation?.players?.find { it.accountId == game.opponent(accountId).accountId }?.ready == true)
                        if (game.invitationState == InvitationState.ACCEPTED) PreparedSummary(state.preparation, game.opponent(accountId).accountId)
                         else Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.CenterStart) {
                             Text(gameStatus(game), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                         }
                        if (game.inviter.accountId == accountId && game.invitationState == InvitationState.DECLINED)
                            ScreenOutlinedButton(onClick = { controller.reinvite(game) }, enabled = game.id in state.connectedGames && game.id !in state.pendingGames) {
                                EntryIcon(null, "send"); Spacer(Modifier.width(8.dp)); Text("Reinvite")
                            }
                    } }
                }
                val starter: @Composable () -> Unit = {
                 StartingPlayerControl(game, state.preparation?.startingChoice ?: StarterChoice(),
                     enabled = game.id in state.connectedGames && game.id !in state.pendingGames && state.preparation != null,
                     choose = controller::starter)
                }
                if (landscape) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    own(Modifier.weight(1f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { opponent(Modifier.fillMaxWidth()); starter() }
                } else { own(Modifier.fillMaxWidth()); opponent(Modifier.fillMaxWidth()); starter() }
            }
        }
        val own = state.preparation?.players?.find { it.accountId == accountId }
        val reason = readyReason(state.preparation, accountId)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 20.dp)) {
                if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall)
            }
            val enabled = state.detail?.id in state.connectedGames && state.detail?.id !in state.pendingGames &&
                own != null && (own.ready || reason == null)
            val readyContent: @Composable RowScope.() -> Unit = {
                EntryIcon(null, if (own?.ready == true) "withdraw" else "ready"); Spacer(Modifier.width(8.dp))
                Text(if (own?.ready == true) "Withdraw Ready" else "Ready")
            }
            if (own?.ready == true) ScreenOutlinedButton(onClick = controller::withdrawReady, enabled = enabled,
                modifier = Modifier.fillMaxWidth(), content = readyContent)
            else ScreenButton(onClick = controller::ready,
                enabled = state.detail?.id in state.connectedGames && state.detail?.id !in state.pendingGames &&
                    own != null && (own.ready || reason == null), modifier = Modifier.fillMaxWidth(), content = readyContent)
        }
        }
        }
    }
    }
}

@Composable
internal fun StartingPlayerControl(game: GameSummary, selected: StarterChoice, enabled: Boolean,
    choose: (StarterChoice) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Starting player", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                for ((label, choice) in listOf("Random" to StarterChoice(),
                    game.inviter.username to StarterChoice(game.inviter.accountId),
                    game.recipient.username to StarterChoice(game.recipient.accountId))) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == choice,
                        enabled = enabled, role = Role.RadioButton, onClick = { choose(choice) })
                        .semantics { contentDescription = "Starting player: $label" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected == choice, onClick = null, enabled = enabled)
                        Text(label, fontWeight = if (selected == choice) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** Shared by Games and inherited navigation rows; a recognized hold must consume Open. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.gameRowActions(controller: LobbyController, game: GameSummary, open: () -> Unit): Modifier {
    val cancel = controller.offersSetupCancellation(game)
    val delete = controller.offersGameDeletion(game)
    return combinedClickable(onClick = open, role = Role.Button, onLongClickLabel = if (cancel || delete) "Game actions" else null,
        onLongClick = when {
            cancel -> ({ controller.showGameContext(game) })
            delete -> ({ controller.gameContext(game) })
            else -> null
        })
        .semantics {
            if (cancel) customActions = listOf(CustomAccessibilityAction("Cancel game") {
                controller.requestCancelSetup(game); true
            })
            else if (delete) customActions = listOf(CustomAccessibilityAction("Delete game") {
                controller.requestGameDeletion(game); true
            })
        }
}

@Composable
internal fun SetupCancellationDialogs(controller: LobbyController) {
    val cancellation = controller.setupCancellation
    cancellation.context?.let { game ->
        SetupCancellationDialog(cancellation::dismissContext) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.gameName, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                ScreenIconButton("Close game actions", "close", cancellation::dismissContext)
            }
            TextButton(onClick = { controller.requestCancelSetup(game) }) {
                EntryIcon(null, "close"); Spacer(Modifier.width(8.dp)); Text("Cancel game")
            }
        }
    }
    cancellation.confirmation?.let { game ->
        val connected = game.id in controller.state.connectedGames
        val pending = game.id in controller.state.pendingGames
        ScreenDialog(cancellation::dismiss, busyMessage = if (pending) "Checking game outcome…" else null,
            error = cancellation.error, dismissError = cancellation::clearError) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel ${game.gameName}?", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                ScreenIconButton("Close cancellation", "close", cancellation::dismiss)
            }
            Text("Removes this unstarted game and its pending invitation.")
            Box(Modifier.fillMaxWidth().height(36.dp)) {
                if (!connected) Text("Disconnected. Reconnect to check the game.", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenOutlinedButton(onClick = cancellation::dismiss, modifier = Modifier.weight(1f)) { EntryIcon(null, "close"); Spacer(Modifier.width(6.dp)); Text("Keep game") }
                ScreenOutlinedButton(onClick = controller::confirmCancelSetup, enabled = connected && !pending,
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    EntryIcon(null, "cancel"); Spacer(Modifier.width(6.dp)); Text("Cancel game")
                }
            }
        }
    }
}

@Composable
private fun SetupCancellationDialog(dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ScreenDialog(dismiss, content = content)
}

@Composable
private fun PlayerReadiness(account: AccountIdentity, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(account)
        Text(account.username, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Icon(screenIcon(if (ready) "ready" else "pending"), null, Modifier.size(18.dp),
            tint = if (ready) androidx.compose.ui.graphics.Color(0xff287a45) else androidx.compose.ui.graphics.Color(0xffa16f26))
        Text(if (ready) "Ready" else "Not ready", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PreparedSummary(preparation: LobbyPreparation?, accountId: String) {
    val player = preparation?.players?.find { it.accountId == accountId }
    val deck = player?.deck
    Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.CenterStart) {
    if (deck == null) {
        if (player?.ready != true) Text("No deck selected", style = MaterialTheme.typography.bodyMedium)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val definition = preparation.definitions.find { it.digest == deck.cover }?.definition
            definition?.faces?.firstOrNull()?.let { face ->
                CardArtwork(ArtworkReference(face.scryfallId, 0, deck.cover!!, "small", face.imageUri), definition.name,
                    48, 68, Modifier.size(48.dp, 68.dp), placeholder = { PodCardPlaceholder(definition.name) })
            }
            Column {
                Text(deck.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${deck.count(SavedDeckSection.MAINBOARD)} main / ${deck.count(SavedDeckSection.SIDEBOARD)} side",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LobbyDeckPicker(controller: LobbyController, importDeck: (() -> Unit)?, hamburger: (() -> Unit)?) {
    val picker = controller.state.picker ?: return
    ScreenBusyOverlay(if (picker.confirming) "Loading deck…" else null) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Choose deck", hamburger)
        InitialLoadArea(picker.loaded || picker.decks.isNotEmpty(), Modifier.weight(1f).fillMaxWidth(), picker.error != null, controller::refreshPicker, loading = picker.loading) {
        PullToRefreshBox(isRefreshing = picker.loading,
            onRefresh = { if (!picker.loading && !picker.confirming) controller.refreshPicker() },
            modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                if (picker.decks.isEmpty()) Box(Modifier.fillMaxWidth().heightIn(min = 20.dp)) {
                    if (!picker.loading) Text("No saved decks yet.")
                }
            }
            items(picker.decks, key = { it.id }) { deck ->
                val chosen = picker.draft?.id == deck.id
                Surface(selected = chosen, onClick = { controller.highlightDeck(deck) }, enabled = !picker.confirming,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (chosen) 3.dp else 1.dp,
                        if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        deck.cover?.let { cover -> CardArtwork(ArtworkReference(cover.scryfallId, 0, cover.definition, "small", cover.imageUri),
                            cover.name, 48, 68, Modifier.size(48.dp, 68.dp)) } ?: EntryIcon(null, "decks")
                         Column(Modifier.weight(1f)) { Text(deck.name, fontWeight = FontWeight.SemiBold)
                             Text("${deck.mainboard} main / ${deck.sideboard} side", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenOutlinedButton(onClick = { importDeck?.invoke() }, enabled = importDeck != null && !picker.confirming, modifier = Modifier.weight(1f)) {
                EntryIcon(null, "import"); Spacer(Modifier.width(8.dp)); Text("Import deck")
            }
            ScreenButton(onClick = controller::confirmDeck, enabled = picker.draft != null && !picker.loading && !picker.confirming &&
                picker.game.id in controller.state.connectedGames && picker.game.id !in controller.state.pendingGames, modifier = Modifier.weight(1f)) {
                EntryIcon(null, "ready"); Spacer(Modifier.width(8.dp)); Text("Use selected deck")
            }
        }
    }
    FloatingTransientError(picker.error, controller::dismissPickerError, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun GameNameDialog(controller: LobbyController) {
    val state = controller.state
    val draft = state.draft ?: return
    var value by remember(draft.recipient.accountId) { mutableStateOf(TextFieldValue(draft.name, TextRange(0, draft.name.length))) }
    LaunchedEffect(draft.name) { if (value.text != draft.name) value = TextFieldValue(draft.name, TextRange(0, draft.name.length)) }
    ScreenDialog(controller::dismiss, busyMessage = if (state.mutating) "Creating game…" else null,
        error = state.error, dismissError = controller::dismissSessionError) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New game", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    ScreenIconButton("Cancel game creation", "close", controller::dismiss, enabled = !state.mutating)
                }
                Text("Invite ${draft.recipient.username}")
                    OutlinedTextField(value, { next -> value = next; controller.name(next.text) },
                         Modifier.fillMaxWidth().semantics { contentDescription = "Game name" }
                            .onFocusChanged { if (it.isFocused) value = value.copy(selection = TextRange(0, value.text.length)) }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    var moved = false
                                    var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                        if (change?.pressed == false) up = change
                                    } while (event.changes.any { it.pressed })
                                    // Observe after TextField's normal tap handling, without consuming drag/long-hold selection.
                                    if (!moved && up != null && up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis)
                                        value = value.copy(selection = TextRange(0, value.text.length))
                                }
                             }, label = { Text("Game name") }, singleLine = true, enabled = !state.mutating && !state.uncertain,
                         trailingIcon = { IconButton(controller::reroll, enabled = !state.mutating && !state.uncertain) { EntryIcon("Suggest another game name", "shuffle") } })
                 // Rare retry state; errors themselves float, so no permanently reserved gap.
                 if (state.uncertain) Text("Retry keeps the original name and invitation.", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenOutlinedButton(onClick = controller::dismiss, enabled = !state.mutating, modifier = Modifier.weight(1f)) { EntryIcon(null, "close"); Spacer(Modifier.width(6.dp)); Text("Cancel") }
                    ScreenButton(onClick = controller::create, enabled = !state.mutating, modifier = Modifier.weight(1f)) {
                         EntryIcon(null, "send"); Spacer(Modifier.width(4.dp)); Text("Create", maxLines = 1)
                    }
                }
    }
}
