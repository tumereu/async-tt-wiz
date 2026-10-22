package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.asyncttwiz.protocol.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun gameDate(game: GameSummary): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(game.createdAt))
}.getOrDefault("")
internal fun gameStatus(game: GameSummary): String = if (game.lifecycle == GameLifecycle.ACTIVE) "In play" else when (game.invitationState) {
    InvitationState.PENDING -> "Invitation pending"
    InvitationState.DECLINED -> "Invitation declined"
    InvitationState.ACCEPTED -> "Preparing"
}
internal fun GameSummary.opponent(accountId: String) = if (inviter.accountId == accountId) recipient else inviter

@Composable
internal fun ContactsContent(controller: LobbyController) {
    val state = controller.state
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val search: @Composable () -> Unit = {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Find a player", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(state.query, controller::query, Modifier.fillMaxWidth().semantics { contentDescription = "Search usernames" },
                        label = { Text("Search usernames") }, singleLine = true)
                    Button(onClick = controller::search, enabled = !state.searching, modifier = Modifier.align(Alignment.End)) {
                        EntryIcon(null, "search"); Spacer(Modifier.width(8.dp)); Text("Search")
                    }
                    Text("Search results", style = MaterialTheme.typography.labelMedium)
                    if (state.searching) Text("Searching…")
                    state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.searched && !state.searching && state.searchError == null && state.results.isEmpty()) Text("No players found.")
                    for (contact in state.results) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(contact.username, Modifier.weight(1f))
                        if (state.contacts.any { it.accountId == contact.accountId }) Text("Added")
                        else TextButton(onClick = { controller.add(contact) }, enabled = !state.mutating) {
                            EntryIcon(null, "contacts"); Spacer(Modifier.width(4.dp)); Text("Add contact")
                        }
                    }
                }
            }
        }
        val saved: @Composable () -> Unit = {
            Text("Saved contacts", style = MaterialTheme.typography.titleMedium)
            if (state.loaded && state.contacts.isEmpty()) Text("No saved contacts.")
            for (contact in state.contacts) OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(contact.username, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { controller.begin(contact) }, enabled = !state.mutating) {
                        EntryIcon(null, "games"); Spacer(Modifier.width(4.dp)); Text("Invite")
                    }
                }
            }
        }
        if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { search() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LobbyFeedback(controller); saved()
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            search(); LobbyFeedback(controller); saved()
        }
    }
    GameNameDialog(controller)
}

@Composable
private fun LobbyFeedback(controller: LobbyController) {
    val state = controller.state
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.draft == null) state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { controller.refresh() }) { Text("Retry") }
    }
}

@Composable
internal fun GamesContent(controller: LobbyController, accountId: String) {
    val state = controller.state
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LobbyFeedback(controller) }
        if (state.loaded && state.games.isEmpty()) item { Text("No games yet.") }
        items(state.games, key = { it.id }) { game ->
            OutlinedCard(onClick = { controller.open(game.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(game.gameName, fontWeight = FontWeight.SemiBold)
                        Text("${game.opponent(accountId).username} · ${gameStatus(game)} · ${gameDate(game)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", Modifier.padding(start = 8.dp).semantics { contentDescription = "Open ${game.gameName}" })
                }
            }
        }
        item { Text("Invitations", style = MaterialTheme.typography.titleLarge) }
        if (state.loaded && state.invitations.isEmpty()) item { Text("No pending invitations.") }
        items(state.invitations, key = { "invite-${it.id}" }) { game ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(game.gameName, fontWeight = FontWeight.SemiBold)
                    Text("${game.inviter.username} · ${gameStatus(game)} · ${gameDate(game)}")
                    Text("Invitation responses are not available in this build yet.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun WaitingLobby(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?) {
    val state = controller.state
    BackHandler { controller.back() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hamburger != null) IconButton(onClick = hamburger) { EntryIcon("Open navigation", "menu") }
            Text(state.detail?.gameName ?: "Loading game…", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LobbyFeedback(controller)
            state.detail?.let { game ->
                Text("${gameStatus(game)} · ${gameDate(game)}")
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(if (game.inviter.accountId == accountId) game.inviter.username else game.recipient.username, fontWeight = FontWeight.SemiBold)
                    Text("Not ready")
                    Text("Deck preparation is not available in this build yet.")
                } }
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(game.opponent(accountId).username, fontWeight = FontWeight.SemiBold)
                    Text(gameStatus(game))
                    if (game.invitationState == InvitationState.PENDING) Text("Waiting for ${game.recipient.username} to accept.")
                } }
            }
        }
    }
}

@Composable
private fun GameNameDialog(controller: LobbyController) {
    val state = controller.state
    val draft = state.draft ?: return
    var value by remember(draft.recipient.accountId) { mutableStateOf(TextFieldValue(draft.name, TextRange(0, draft.name.length))) }
    LaunchedEffect(draft.name) { if (value.text != draft.name) value = TextFieldValue(draft.name, TextRange(0, draft.name.length)) }
    Dialog(onDismissRequest = controller::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.imePadding().padding(16.dp).widthIn(max = 420.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New game", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = controller::dismiss, enabled = !state.mutating) { EntryIcon("Cancel game creation", "close") }
                }
                Text("Invite ${draft.recipient.username}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value, { next -> value = next; controller.name(next.text) },
                        Modifier.weight(1f).semantics { contentDescription = "Game name" }
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
                            }, label = { Text("Game name") }, singleLine = true, enabled = !state.mutating && !state.uncertain)
                    IconButton(onClick = controller::reroll, enabled = !state.mutating && !state.uncertain) { EntryIcon("Suggest another game name", "refresh") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.uncertain) Text("Retry keeps the original name and invitation.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = controller::dismiss, enabled = !state.mutating, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = controller::create, enabled = !state.mutating, modifier = Modifier.weight(1f)) {
                        EntryIcon(null, "games"); Spacer(Modifier.width(4.dp)); Text(if (state.mutating) "Creating…" else "Create & invite")
                    }
                }
            }
        }
    }
}
