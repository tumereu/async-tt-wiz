package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AuthenticatedShell(controller: AuthController, landscape: Boolean, catalog: CatalogViewModel? = null,
    deckImport: DeckImportController? = null, decks: DeckLibraryController? = null, lobby: LobbyController? = null) {
    val state = controller.state
    val tabletop = state.destination == ShellDestination.GAMES && lobby?.state?.selected != null &&
        lobby.state.detail?.lifecycle == org.asyncttwiz.protocol.GameLifecycle.ACTIVE
    BackHandler(state.drawerOpen || state.destination != ShellDestination.MENU) { controller.back() }
    LaunchedEffect(state.destination) {
        if (state.destination != ShellDestination.DECKS && !(state.destination == ShellDestination.GAMES && lobby?.state?.picker?.importing == true)) deckImport?.exit()
        else if (decks?.state?.selected == null) decks?.refresh()
        if (state.destination != ShellDestination.GAMES) lobby?.closePicker()
        lobby?.refresh()
    }
    LaunchedEffect(state.account?.accountId) {
        while (true) { kotlinx.coroutines.delay(5_000); lobby?.sync() }
    }
    LaunchedEffect(state.destination, deckImport?.state?.active) {
        if (state.destination == ShellDestination.DECKS && deckImport?.state?.active != true) {
            while (true) { kotlinx.coroutines.delay(5_000); decks?.sync() }
        }
    }
    val content: @Composable () -> Unit = {
        if (state.destination == ShellDestination.GAMES && lobby?.state?.picker?.importing == true && deckImport?.state?.active == true) {
            DeckImportScreen(deckImport, landscape, if (!landscape) ({ controller.drawer(true) }) else null, catalog)
        } else if (state.destination == ShellDestination.GAMES && lobby?.state?.selected != null) {
            LaunchedEffect(lobby.state.picker?.importing, deckImport?.state?.active) {
                if (lobby.state.picker?.importing == true && deckImport?.state?.active != true) lobby.importedForPicker()
            }
            WaitingLobby(lobby, state.account!!.accountId, if (!landscape || tabletop) ({ controller.drawer(true) }) else null, landscape,
                importDeck = deckImport?.let { importer -> { lobby.importForPicker(); importer.open() } })
        } else if (state.destination == ShellDestination.DECKS && deckImport?.state?.active == true) {
            DeckImportScreen(deckImport, landscape, if (!landscape) ({ controller.drawer(true) }) else null, catalog)
        } else if (state.destination == ShellDestination.DECKS && decks?.state?.selected != null) {
            SavedDeckScreen(decks, if (!landscape) ({ controller.drawer(true) }) else null,
                importDeck = deckImport?.let { importer -> { summary -> decks.dismiss(); importer.openExisting(summary) } })
        } else ShellContent(state.destination, !landscape, { controller.drawer(true) }, { destination ->
            if (destination == ShellDestination.GAMES) lobby?.back()
            controller.navigate(destination)
        }, catalog, deckImport, decks, lobby, state.account!!.accountId)
    }
    Box(Modifier.fillMaxSize().testTag("authenticated-identity")
        .semantics { contentDescription = "Signed in as ${state.account!!.username}" }) {
        if (landscape && !tabletop) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(220.dp).fillMaxHeight(), color = Color.White,
                    border = BorderStroke(1.dp, Color(0xffd0d6dd))) {
                    Navigation(controller, close = false, lobby)
                }
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            // One source of truth for Back/Close/outside dismissal; no mirrored animated DrawerState.
            Box(Modifier.fillMaxSize()) {
                Box(if (state.drawerOpen) Modifier.clearAndSetSemantics {} else Modifier) { content() }
                if (state.drawerOpen) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .2f))
                        .clickable { controller.drawer(false) }.semantics { contentDescription = "Close navigation menu" })
                    Surface(Modifier.widthIn(max = 280.dp).fillMaxWidth(.8f).fillMaxHeight()
                        .semantics { paneTitle = "Navigation menu" }, color = Color.White) {
                        Navigation(controller, close = true, lobby)
                    }
                    BackHandler { controller.drawer(false) }
                }
            }
        }
    }
    // Own the single dialog host here, outside route/orientation branches, not in MainActivity.
    // This also keeps dialogs available when the authenticated shell is composed on its own.
    if (lobby != null) {
        SetupCancellationDialogs(lobby)
        GameDeletionDialogs(lobby)
    }
}

@Composable
internal fun Navigation(controller: AuthController, close: Boolean, lobby: LobbyController?) {
    Column(Modifier.fillMaxSize()) {
        if (close) IconButton(onClick = { controller.drawer(false) }, Modifier.align(Alignment.End)) {
            EntryIcon("Close navigation", "close")
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            NavigationItem("Menu", null, controller.state.destination == ShellDestination.MENU) {
                controller.navigate(ShellDestination.MENU)
            }
            for (destination in listOf(ShellDestination.DECKS, ShellDestination.CONTACTS)) {
                NavigationItem(destination.title, destination.icon(), controller.state.destination == destination, indented = true) {
                    controller.navigate(destination)
                }
            }
            NavigationItem("Games", null, controller.state.destination == ShellDestination.GAMES) {
                lobby?.back()
                controller.navigate(ShellDestination.GAMES)
            }
            for (game in lobby?.state?.games.orEmpty()) {
                val selectedGame = controller.state.destination == ShellDestination.GAMES && lobby?.state?.selected == game.id
                // One combined handler owns tap/hold; nesting another clickable drawer item would
                // allow a recognized context hold to also open the game on release.
                Surface(Modifier.padding(start = 20.dp).fillMaxWidth().heightIn(min = 72.dp)
                    .gameRowActions(lobby!!, game) { lobby.open(game.id); controller.drawer(false) }
                    .semantics { selected = selectedGame }, shape = MaterialTheme.shapes.extraLarge,
                    color = if (selectedGame) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EntryIcon(null, "games")
                        Column(Modifier.weight(1f)) {
                            Text(game.gameName)
                            Text("${game.opponent(controller.state.account!!.accountId).username} · ${gameStatus(game)} · ${gameDate(game)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EntryIcon(null, "chevron")
                    }
                }
            }
        }
        HorizontalDivider()
        TextButton(onClick = controller::logout, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("logout"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            EntryIcon(null, "logout"); Spacer(Modifier.width(8.dp)); Text("Logout")
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavigationItem(label: String, icon: String?, selected: Boolean, indented: Boolean = false, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = action).semantics { this.selected = selected }
        .padding(start = if (indented) 20.dp else 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) EntryIcon(null, icon)
        Text(label, fontWeight = if (icon == null) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
internal fun ShellContent(destination: ShellDestination, hamburger: Boolean, open: () -> Unit, navigate: (ShellDestination) -> Unit,
    catalog: CatalogViewModel?, deckImport: DeckImportController?, decks: DeckLibraryController?, lobby: LobbyController?, accountId: String) {
    Column(Modifier.fillMaxSize()) {
        if (hamburger || destination != ShellDestination.MENU) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hamburger) IconButton(onClick = open) { EntryIcon("Open navigation", "menu") }
            if (destination != ShellDestination.MENU)
                Text(destination.title, Modifier.padding(12.dp).testTag("destination-title"), style = MaterialTheme.typography.titleLarge,
                     fontWeight = FontWeight.Bold)
            if (destination == ShellDestination.GAMES) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { navigate(ShellDestination.CONTACTS) }) { EntryIcon(null, "add"); Spacer(Modifier.width(8.dp)); Text("New game") }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (destination == ShellDestination.CONTACTS && lobby != null) ContactsContent(lobby)
            else if (destination == ShellDestination.GAMES && lobby != null) GamesContent(lobby, accountId)
            else if (destination == ShellDestination.DECKS && decks != null) SavedDeckCollection(decks, catalog)
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(16.dp),
                verticalArrangement = if (destination == ShellDestination.MENU) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (destination == ShellDestination.MENU) {
                    WizardLogo()
                    Spacer(Modifier.height(24.dp))
                    for (item in listOf(ShellDestination.GAMES, ShellDestination.DECKS, ShellDestination.CONTACTS)) {
                         OutlinedButton(onClick = { navigate(item) }, modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth()
                             .heightIn(min = if (hamburger) 78.dp else 60.dp).testTag("menu-${item.name}"),
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xffcdd3dc)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White,
                                contentColor = if (item == ShellDestination.GAMES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) {
                             Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ScreenBorder),
                                 color = if (item == ShellDestination.GAMES) MaterialTheme.colorScheme.primaryContainer else ScreenBackground) {
                                 Box(Modifier.size(if (hamburger) 44.dp else 36.dp), contentAlignment = Alignment.Center) { EntryIcon(null, item.icon()) }
                             }
                             Spacer(Modifier.width(16.dp))
                            Text(item.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                             EntryIcon(null, "chevron")
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                } else if (destination == ShellDestination.DECKS && catalog != null) {
                    CatalogControls(catalog)
                } else {
                    Text(when (destination) {
                        ShellDestination.GAMES -> "Games and invitations are not available in this build yet."
                        ShellDestination.DECKS -> "Deck import and management are not available in this build yet."
                        else -> "Contact search and invitations are not available in this build yet."
                    }, Modifier.widthIn(max = 540.dp).fillMaxWidth())
                }
            }
        }
        if (destination == ShellDestination.DECKS && deckImport != null) {
            ScreenButton(onClick = deckImport::open, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("import-deck")) {
                EntryIcon(null, "import"); Spacer(Modifier.width(8.dp)); Text("Import deck")
            }
        }
    }
}

private fun ShellDestination.icon(): String = when (this) {
    ShellDestination.GAMES -> "games"
    ShellDestination.DECKS -> "decks"
    ShellDestination.CONTACTS -> "contacts"
    ShellDestination.MENU -> "menu"
}
