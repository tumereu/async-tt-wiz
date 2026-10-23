package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
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
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(game.createdAt))
}.getOrDefault("")
internal fun gameStatus(game: GameSummary): String = if (game.lifecycle == GameLifecycle.ACTIVE) "In play" else when (game.invitationState) {
    InvitationState.PENDING -> "Invitation pending"
    InvitationState.DECLINED -> "Invitation declined"
    InvitationState.ACCEPTED -> "Preparing"
}
internal fun GameSummary.opponent(accountId: String) = if (inviter.accountId == accountId) recipient else inviter
internal fun readyReason(preparation: LobbyPreparation?, accountId: String): String? = when {
    preparation?.players?.find { it.accountId == accountId }?.deck == null -> "Select your deck to get ready."
    preparation.starter == null -> "Choose the starting player to get ready."
    else -> null
}

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
    state.connectionMessage?.let { message ->
        Text(message, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = controller::reconnect) { Text("Reconnect") }
    }
    if (state.pendingGames.isNotEmpty()) Text("Checking game outcome…", style = MaterialTheme.typography.bodySmall)
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
            OutlinedCard(modifier = Modifier.fillMaxWidth().gameRowActions(controller, game) { controller.open(game.id) }) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { controller.respond(game, false) }, enabled = game.id in state.connectedGames && game.id !in state.pendingGames,
                            modifier = Modifier.weight(1f)) { Text("Decline") }
                        Button(onClick = { controller.respond(game, true) }, enabled = game.id in state.connectedGames && game.id !in state.pendingGames,
                            modifier = Modifier.weight(1f)) { Text("Accept") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GameDeletionDialogs(controller: LobbyController) {
    controller.state.gameContext?.let { game ->
        AlertDialog(onDismissRequest = controller::dismissGameContext,
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.gameName, Modifier.weight(1f))
                IconButton(onClick = controller::dismissGameContext) { EntryIcon("Close game actions", "close") }
            } }, text = { Text("${gameStatus(game)} · ${gameDate(game)}") },
            confirmButton = { TextButton(onClick = { controller.requestGameDeletion(game) }) {
                DeleteGameIcon(); Spacer(Modifier.width(8.dp)); Text("Delete game")
            } })
    }
    controller.state.deletion?.let { capture ->
        AlertDialog(onDismissRequest = controller::dismissGameDeletion,
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(capture.title, Modifier.weight(1f))
                IconButton(onClick = controller::dismissGameDeletion) { EntryIcon("Keep game", "close") }
            } }, text = { Text(capture.warning) },
            dismissButton = { OutlinedButton(onClick = controller::dismissGameDeletion) { Text("Keep game") } },
            confirmButton = { Button(onClick = { controller.confirmGameDeletion() }) { Text("Delete game") } })
    }
}

@Composable
private fun DeleteGameIcon() {
    val color = LocalContentColor.current
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w / 12)
        drawRect(color, androidx.compose.ui.geometry.Offset(w * .25f, w * .3f), androidx.compose.ui.geometry.Size(w * .5f, w * .55f), style = stroke)
        drawLine(color, androidx.compose.ui.geometry.Offset(w * .15f, w * .25f), androidx.compose.ui.geometry.Offset(w * .85f, w * .25f), w / 12)
        drawLine(color, androidx.compose.ui.geometry.Offset(w * .4f, w * .12f), androidx.compose.ui.geometry.Offset(w * .6f, w * .12f), w / 12)
    }
}

@Composable
internal fun WaitingLobby(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean = false,
    importDeck: (() -> Unit)? = null) {
    val state = controller.state
    BackHandler { controller.back() }
    if (state.detail?.lifecycle == GameLifecycle.ACTIVE) {
        TableScreen(controller, accountId, hamburger, landscape); return
    }
    if (state.picker != null) { LobbyDeckPicker(controller, importDeck); return }
    if (state.preparing && state.detail?.lifecycle == GameLifecycle.LOBBY) {
        PreparationScreen(controller, accountId, landscape); return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hamburger != null) IconButton(onClick = hamburger) { EntryIcon("Open navigation", "menu") }
            Text(state.detail?.gameName ?: "Loading game…", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleLarge)
            state.detail?.takeIf(controller::offersSetupCancellation)?.let { game ->
                TextButton(onClick = { controller.requestCancelSetup(game) }) { Text("Cancel") }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LobbyFeedback(controller)
            state.detail?.let { game ->
                Text("${gameStatus(game)} · ${gameDate(game)}")
                val own: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedCard(modifier) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (game.inviter.accountId == accountId) game.inviter.username else game.recipient.username, fontWeight = FontWeight.SemiBold)
                        PreparedSummary(state.preparation, accountId)
                        Button(onClick = controller::chooseDeck, enabled = game.lifecycle == GameLifecycle.LOBBY &&
                            game.id in state.connectedGames && game.id !in state.pendingGames && state.preparation != null) {
                            EntryIcon(null, "decks"); Spacer(Modifier.width(4.dp))
                            Text(if (state.preparation?.players?.find { it.accountId == accountId }?.deck == null) "Choose deck" else "Change deck")
                        }
                        if (state.preparation?.players?.find { it.accountId == accountId }?.deck != null && game.lifecycle == GameLifecycle.LOBBY)
                            OutlinedButton(onClick = controller::prepareDeck) { Text("Prepare deck") }
                    } }
                }
                val opponent: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedCard(modifier) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(game.opponent(accountId).username, fontWeight = FontWeight.SemiBold)
                        if (game.invitationState == InvitationState.ACCEPTED) PreparedSummary(state.preparation, game.opponent(accountId).accountId)
                        else Text(gameStatus(game))
                        if (game.invitationState == InvitationState.PENDING) Text("Waiting for ${game.recipient.username} to accept.")
                        if (game.inviter.accountId == accountId && game.invitationState == InvitationState.DECLINED)
                            TextButton(onClick = { controller.reinvite(game) }, enabled = game.id in state.connectedGames && game.id !in state.pendingGames) { Text("Reinvite") }
                    } }
                }
                if (landscape) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { own(Modifier.weight(1f)); opponent(Modifier.weight(1f)) }
                else { own(Modifier.fillMaxWidth()); opponent(Modifier.fillMaxWidth()) }
                Text("Starting player", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((label, choice) in listOf(game.inviter.username to StarterChoice(game.inviter.accountId),
                        game.recipient.username to StarterChoice(game.recipient.accountId), "Random" to StarterChoice())) {
                        FilterChip(selected = state.preparation?.starter == choice,
                            onClick = { controller.starter(choice) }, label = { Text(label) },
                            enabled = game.id in state.connectedGames && game.id !in state.pendingGames && state.preparation != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Starting player: $label" })
                    }
                }
            }
        }
        val own = state.preparation?.players?.find { it.accountId == accountId }
        val reason = readyReason(state.preparation, accountId)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { if (own?.ready == true) controller.withdrawReady() else controller.ready() },
                enabled = state.detail?.id in state.connectedGames && state.detail?.id !in state.pendingGames &&
                    own != null && (own.ready || reason == null), modifier = Modifier.fillMaxWidth()) {
                Text(if (own?.ready == true) "Withdraw Ready" else "Ready")
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
                IconButton(onClick = cancellation::dismissContext) { EntryIcon("Close game actions", "close") }
            }
            TextButton(onClick = { controller.requestCancelSetup(game) }) {
                EntryIcon(null, "close"); Spacer(Modifier.width(8.dp)); Text("Cancel game")
            }
        }
    }
    cancellation.confirmation?.let { game ->
        val connected = game.id in controller.state.connectedGames
        val pending = game.id in controller.state.pendingGames
        SetupCancellationDialog(cancellation::dismiss) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel game?", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = cancellation::dismiss) { EntryIcon("Close cancellation", "close") }
            }
            Text(game.gameName, fontWeight = FontWeight.SemiBold)
            Text("Cancel this unstarted game and its invitation? This cannot be undone.")
            if (!connected) Text("Disconnected. Reconnect to check the game.", style = MaterialTheme.typography.bodySmall)
            if (pending) Text("Checking game outcome…", style = MaterialTheme.typography.bodySmall)
            cancellation.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = cancellation::dismiss, modifier = Modifier.weight(1f)) { Text("Keep game") }
                Button(onClick = controller::confirmCancelSetup, enabled = connected && !pending,
                    modifier = Modifier.weight(1f)) { Text("Cancel game") }
            }
        }
    }
}

@Composable
private fun SetupCancellationDialog(dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun PreparedSummary(preparation: LobbyPreparation?, accountId: String) {
    val player = preparation?.players?.find { it.accountId == accountId }
    Text(if (player?.ready == true) "✓ Ready" else "Not ready",
        color = if (player?.ready == true) androidx.compose.ui.graphics.Color(0xff287a45) else MaterialTheme.colorScheme.onSurfaceVariant)
    val deck = player?.deck
    if (deck == null) Text("No deck selected") else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val definition = preparation.definitions.find { it.digest == deck.cover }?.definition
            definition?.faces?.firstOrNull()?.let { face ->
                CardArtwork(ArtworkReference(face.scryfallId, 0, deck.cover!!, "small", face.imageUri), definition.name,
                    48, 68, Modifier.size(48.dp, 68.dp))
            }
            Column {
                Text(deck.name, fontWeight = FontWeight.SemiBold)
                Text("Mainboard · ${deck.count(SavedDeckSection.MAINBOARD)}")
                Text("Sideboard · ${deck.count(SavedDeckSection.SIDEBOARD)}")
            }
        }
    }
}

@Composable
private fun LobbyDeckPicker(controller: LobbyController, importDeck: (() -> Unit)?) {
    val picker = controller.state.picker ?: return
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Choose deck", Modifier.weight(1f).padding(16.dp), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = controller::closePicker) { EntryIcon("Close deck picker", "close") }
        }
        if (picker.loading || picker.confirming) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                picker.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!picker.loading && picker.decks.isEmpty()) Text("No saved decks yet.")
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
                        Column(Modifier.weight(1f)) { Text(deck.name, fontWeight = FontWeight.SemiBold); Text("${deck.mainboard} / ${deck.sideboard}") }
                    }
                }
            }
            item { TextButton(onClick = controller::refreshPicker, enabled = !picker.loading && !picker.confirming) { Text("Refresh saved decks") } }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importDeck?.invoke() }, enabled = importDeck != null && !picker.confirming, modifier = Modifier.weight(1f)) { Text("Import deck") }
            Button(onClick = controller::confirmDeck, enabled = picker.draft != null && !picker.loading && !picker.confirming &&
                picker.game.id in controller.state.connectedGames && picker.game.id !in controller.state.pendingGames, modifier = Modifier.weight(1f)) {
                Text(if (picker.confirming) "Loading deck…" else "Use selected deck")
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
