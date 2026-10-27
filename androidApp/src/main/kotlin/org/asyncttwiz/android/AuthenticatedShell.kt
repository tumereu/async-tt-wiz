package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
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
    deckImport: DeckImportController? = null, decks: DeckLibraryController? = null, lobby: LobbyController? = null,
    cubes: CubeController? = null, pods: PodController? = null) {
    val state = controller.state
    var collectionCubes by remember { mutableStateOf(false) }
    if (catalog != null && state.avatarChooser) { AvatarChooser(controller, catalog); return }
    val tabletop = state.destination == ShellDestination.GAMES && lobby?.state?.selected != null &&
        lobby.state.detail?.lifecycle == org.asyncttwiz.protocol.GameLifecycle.ACTIVE
    BackHandler(state.drawerOpen || state.destination != ShellDestination.MENU) { controller.back() }
    LaunchedEffect(state.destination) {
        if (state.destination != ShellDestination.DECKS && !(state.destination == ShellDestination.GAMES && lobby?.state?.picker?.importing == true)) deckImport?.exit()
        if (state.destination == ShellDestination.DECKS && decks?.state?.selected == null) decks?.refresh()
        if (state.destination != ShellDestination.GAMES) lobby?.closePicker()
        lobby?.refresh()
        if (state.destination == ShellDestination.MENU) pods?.refresh()
    }
    val catalogBusy = !tabletop && state.destination != ShellDestination.MENU && state.destination != ShellDestination.SETTINGS &&
        catalog?.state?.background == false && catalog.state.progress != null
    val content: @Composable () -> Unit = {
        ScreenBusyOverlay(if (catalogBusy) catalog.state.status else null, catalog?.state?.progress) {
            CompositionLocalProvider(LocalBusySuppressed provides catalogBusy) {
                if (state.destination == ShellDestination.GAMES && lobby?.state?.picker?.importing == true && deckImport?.state?.active == true) {
                    DeckImportScreen(deckImport, landscape, if (!landscape) ({ controller.drawer(true) }) else null, catalog)
                } else if (state.destination == ShellDestination.GAMES && lobby?.state?.selected != null) {
                    LaunchedEffect(lobby.state.picker?.importing, deckImport?.state?.active) {
                        if (lobby.state.picker?.importing == true && deckImport?.state?.active != true) lobby.importedForPicker()
                    }
                    WaitingLobby(lobby, state.account!!.accountId, if (!landscape || tabletop) ({ controller.drawer(true) }) else null, landscape,
                        importDeck = deckImport?.let { importer -> { lobby.importForPicker(); importer.open() } })
                } else if (state.destination == ShellDestination.PODS && pods != null) {
                    PodsDestination(pods, state.account!!.accountId, if (!landscape) ({ controller.drawer(true) }) else null, lobby) {
                        collectionCubes = true
                        controller.navigate(ShellDestination.DECKS)
                    }
                } else if (state.destination == ShellDestination.DECKS && cubes != null) {
                    ManageCollectionScreen(cubes, decks, deckImport, landscape, if (!landscape) ({ controller.drawer(true) }) else null, catalog,
                        draftCube = pods?.let { podController -> { id -> podController.newPod(id); controller.navigate(ShellDestination.PODS) } },
                        initiallyCubes = collectionCubes)
                } else if (state.destination == ShellDestination.DECKS && deckImport?.state?.active == true) {
                    DeckImportScreen(deckImport, landscape, if (!landscape) ({ controller.drawer(true) }) else null, catalog)
                } else if (state.destination == ShellDestination.DECKS && decks?.state?.selected != null) {
                    SavedDeckScreen(decks, if (!landscape) ({ controller.drawer(true) }) else null,
                        importDeck = deckImport?.let { importer -> { detail -> decks.dismiss(); importer.openEditor(detail) } })
                } else ShellContent(state.destination, !landscape, { controller.drawer(true) }, { destination ->
                    if (destination == ShellDestination.GAMES) lobby?.back()
                    if (destination == ShellDestination.PODS) pods?.showList()
                    if (destination == ShellDestination.DECKS) collectionCubes = false
                    controller.navigate(destination)
                }, catalog, deckImport, decks, lobby, state.account!!.accountId, controller::logout, controller, pods)
            }
        }
    }
    Box(Modifier.fillMaxSize().testTag("authenticated-identity")
        .semantics { contentDescription = "Signed in as ${state.account!!.username}" }) {
        if (landscape && !tabletop) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(220.dp).fillMaxHeight(), color = Color.White,
                    border = BorderStroke(1.dp, Color(0xffd0d6dd))) {
                    Navigation(controller, close = false, lobby, pods)
                }
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            // One source of truth for Back/Close/outside dismissal; no mirrored animated DrawerState.
            Box(Modifier.fillMaxSize()) {
                Box(if (state.drawerOpen) Modifier.clearAndSetSemantics {} else Modifier) { content() }
                AnimatedVisibility(state.drawerOpen, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .2f))
                        .clickable { controller.drawer(false) }.semantics { contentDescription = "Close navigation menu" })
                }
                AnimatedVisibility(state.drawerOpen,
                    enter = slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { -it },
                    exit = slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { -it }) {
                    Surface(Modifier.widthIn(max = 280.dp).fillMaxWidth(.8f).fillMaxHeight()
                        .semantics { paneTitle = "Navigation menu" }, color = Color.White) {
                        Navigation(controller, close = true, lobby, pods)
                    }
                }
                // Register after route content so its own BackHandler cannot dismiss a draft/table first.
                BackHandler(enabled = state.drawerOpen) { controller.drawer(false) }
            }
        }
        if (!tabletop && lobby != null) FloatingTransientError(
            lobby.state.error?.takeIf { lobby.state.draft == null } ?: lobby.state.searchError ?: lobby.state.connectionMessage,
            lobby::dismissSessionError, Modifier.align(Alignment.BottomCenter))
        FloatingTransientError(state.profileError, controller::dismissProfileError, Modifier.align(Alignment.BottomCenter))
    }
    // Own the single dialog host here, outside route/orientation branches, not in MainActivity.
    // This also keeps dialogs available when the authenticated shell is composed on its own.
    if (lobby != null) {
        SetupCancellationDialogs(lobby)
        GameDeletionDialogs(lobby)
    }
    if (!tabletop && catalog != null && catalog.promptPolicy.shouldPrompt(catalog.state)) {
        CatalogStartupDialog(catalog.state,
            download = { catalog.promptPolicy.dismiss(catalog.state); catalog.consent() },
            dismiss = { catalog.promptPolicy.dismiss(catalog.state) })
    }
}

@Composable
internal fun Navigation(controller: AuthController, close: Boolean, lobby: LobbyController?, pods: PodController? = null) {
    Column(Modifier.fillMaxSize()) {
        if (close) IconButton(onClick = { controller.drawer(false) }, Modifier.align(Alignment.End)) {
            EntryIcon("Close navigation", "close")
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            NavigationItem("Menu", null, controller.state.destination == ShellDestination.MENU) {
                controller.navigate(ShellDestination.MENU)
            }
            for (destination in listOf(ShellDestination.DECKS, ShellDestination.CONTACTS, ShellDestination.SETTINGS)) {
                NavigationItem(destination.title, destination.icon(), controller.state.destination == destination, indented = true) {
                    if (destination == ShellDestination.CONTACTS) lobby?.contacts(false)
                    controller.navigate(destination)
                }
            }
            NavigationItem("Games", null, controller.state.destination == ShellDestination.GAMES) {
                lobby?.back()
                controller.navigate(ShellDestination.GAMES)
            }
            InitialLoadArea(lobby == null || lobby.state.loaded, Modifier.fillMaxWidth()
                .heightIn(min = if (lobby != null && !lobby.state.loaded) 80.dp else 0.dp),
                failed = lobby?.state?.error != null, retry = { lobby?.refresh() }, loading = lobby?.state?.busy == true) {
            Column {
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
                        Avatar(game.opponent(controller.state.account!!.accountId))
                        Column(Modifier.weight(1f)) {
                            Text(game.gameName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${game.opponent(controller.state.account!!.accountId).username} · ${gameStatus(game)} · ${gameDate(game)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        EntryIcon(null, "chevron")
                    }
                }
            }
            }
            }
            NavigationItem("Pods", null, controller.state.destination == ShellDestination.PODS) {
                pods?.showList()
                controller.navigate(ShellDestination.PODS)
            }
            val podsLoaded = pods == null || pods.state.loaded || pods.state.pods.isNotEmpty()
            InitialLoadArea(podsLoaded, Modifier.fillMaxWidth().heightIn(min = if (podsLoaded) 0.dp else 80.dp),
                failed = pods?.state?.error != null, retry = { pods?.refresh() }, loading = pods?.state?.loading == true) {
                Column {
                    for (pod in pods?.state?.pods.orEmpty()) {
                        val selectedPod = controller.state.destination == ShellDestination.PODS && pods?.state?.selected == pod.id
                        Surface(Modifier.padding(start = 20.dp).fillMaxWidth().heightIn(min = 72.dp)
                            .testTag("navigation-pod-${pod.id}")
                            .clickable { pods!!.open(pod.id); controller.navigate(ShellDestination.PODS) }
                            .semantics { selected = selectedPod }, shape = MaterialTheme.shapes.extraLarge,
                            color = if (selectedPod) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    pod.members.take(3).forEach { Avatar(it) }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(pod.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(podStatus(pod), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                EntryIcon(null, "chevron")
                            }
                        }
                    }
                }
            }
            if (controller.accountSwitcher) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Text("Switch account", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                val current = controller.state.account
                // An existing single-session install may not yet have remembered display metadata.
                val accounts = controller.state.rememberedAccounts.let { saved ->
                    if (current != null && saved.none { it.identity.accountId == current.accountId })
                        listOf(RememberedAccount(current, "")) + saved else saved
                }
                for (account in accounts) {
                    val selected = account.identity.accountId == current?.accountId
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("switch-account-${account.identity.accountId}")
                        .clickable { controller.switchAccount(account.identity.accountId) }
                        .semantics { this.selected = selected }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.AccountCircle, null, Modifier.size(22.dp))
                        Text(account.identity.username, Modifier.weight(1f), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (selected) EntryIcon("Current account", "ready")
                    }
                }
                NavigationItem("Add account", "person", false, action = controller::addAccount)
            }
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
    catalog: CatalogViewModel?, deckImport: DeckImportController?, decks: DeckLibraryController?, lobby: LobbyController?, accountId: String,
    logout: () -> Unit = {}, auth: AuthController? = null, pods: PodController? = null) {
    Column(Modifier.fillMaxSize()) {
        if (hamburger || destination != ShellDestination.MENU) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hamburger) IconButton(onClick = open) { EntryIcon("Open navigation", "menu") }
            if (hamburger && destination == ShellDestination.MENU) {
                Spacer(Modifier.weight(1f))
                WizardLogo(Modifier.padding(end = 16.dp), compact = true)
            }
            if (destination != ShellDestination.MENU)
                Text(if (destination == ShellDestination.CONTACTS && lobby?.state?.choosingOpponent == true) "Choose opponent" else destination.title, Modifier.padding(12.dp).testTag("destination-title"), style = MaterialTheme.typography.titleLarge,
                     fontWeight = FontWeight.Bold)
            if (destination == ShellDestination.GAMES) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { lobby?.contacts(true); navigate(ShellDestination.CONTACTS) }) { EntryIcon(null, "add"); Spacer(Modifier.width(8.dp)); Text("New game") }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (destination == ShellDestination.CONTACTS && lobby != null) ContactsContent(lobby)
            else if (destination == ShellDestination.GAMES && lobby != null) GamesContent(lobby, accountId)
            else if (destination == ShellDestination.DECKS && decks != null) SavedDeckCollection(decks)
            else if (destination == ShellDestination.SETTINGS) SettingsContent(catalog, accountId, logout, auth)
            else InitialLoadArea(destination != ShellDestination.MENU || lobby == null || lobby.state.loaded,
                failed = lobby?.state?.error != null, retry = { lobby?.refresh() }, loading = lobby?.state?.busy == true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(16.dp),
                verticalArrangement = if (destination == ShellDestination.MENU) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (destination == ShellDestination.MENU) {
                    if (!hamburger) {
                        WizardLogo()
                        Spacer(Modifier.height(24.dp))
                    }
                     for (item in listOf(ShellDestination.GAMES, ShellDestination.PODS, ShellDestination.DECKS, ShellDestination.CONTACTS, ShellDestination.SETTINGS)) {
                          OutlinedButton(onClick = { if (item == ShellDestination.CONTACTS) lobby?.contacts(false); navigate(item) }, modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth()
                             .heightIn(min = if (hamburger) 78.dp else 60.dp).testTag("menu-${item.name}"),
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xffcdd3dc)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White,
                                contentColor = if (item == ShellDestination.GAMES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) {
                             Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ScreenBorder),
                                 color = if (item == ShellDestination.GAMES) MaterialTheme.colorScheme.primaryContainer else ScreenBackground) {
                                 Box(Modifier.size(if (hamburger) 44.dp else 36.dp), contentAlignment = Alignment.Center) { EntryIcon(null, item.icon()) }
                             }
                             Spacer(Modifier.width(16.dp))
                             Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                             EntryIcon(null, "chevron")
                        }
                         if (item == ShellDestination.GAMES && lobby != null) {
                             val games = quickContinueGames(lobby.state.games + lobby.state.invitations, accountId)
                             if (games.isNotEmpty()) MenuActivityPanel {
                                 games.forEachIndexed { index, game ->
                                     if (index > 0) HorizontalDivider(color = ScreenBorder)
                                     val incoming = game.lifecycle == org.asyncttwiz.protocol.GameLifecycle.LOBBY &&
                                         game.invitationState == org.asyncttwiz.protocol.InvitationState.PENDING && game.recipient.accountId == accountId
                                     GameRow(game, accountId, Modifier.fillMaxWidth().testTag("quick-continue-${game.id}")
                                         .gameRowActions(lobby, game) {
                                             navigate(ShellDestination.GAMES)
                                             if (!incoming) lobby.open(game.id)
                                         }, trailing = if (incoming) ({
                                             MenuInvitationActions(game.id in lobby.state.connectedGames && game.id !in lobby.state.pendingGames) {
                                                 lobby.respond(game, it)
                                             }
                                         }) else null)
                                 }
                             }
                         }
                         if (item == ShellDestination.PODS && pods != null) {
                             val entries = quickContinuePods(pods.state.pods, pods.state.invitations)
                             if (entries.isNotEmpty()) MenuActivityPanel {
                                 entries.forEachIndexed { index, entry ->
                                     if (index > 0) HorizontalDivider(color = ScreenBorder)
                                     MenuPodRow(entry, { accept ->
                                         if (accept) navigate(ShellDestination.PODS)
                                         pods.respond(entry.id, accept)
                                     }, pods.state.pending == 0 && !pods.state.uncertain) {
                                         navigate(ShellDestination.PODS)
                                         entry.pod?.let { pods.open(it.id) }
                                     }
                                 }
                             }
                         }
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    Text(when (destination) {
                        ShellDestination.GAMES -> "Games and invitations are not available in this build yet."
                        ShellDestination.DECKS -> "Deck import and management are not available in this build yet."
                        else -> "Contact search and invitations are not available in this build yet."
                    }, Modifier.widthIn(max = 540.dp).fillMaxWidth())
                }
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
    ShellDestination.PODS -> "pods"
    ShellDestination.DECKS -> "decks"
    ShellDestination.CONTACTS -> "contacts"
    ShellDestination.SETTINGS -> "settings"
    ShellDestination.MENU -> "menu"
}
