package org.asyncttwiz.android

import org.asyncttwiz.appui.BackHandler
import org.asyncttwiz.appui.ScreenBackHandler
import org.asyncttwiz.protocol.Faults
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
fun AuthenticatedShell(controller: AuthController, landscapeWindow: Boolean, catalog: CatalogViewModel? = null,
    deckImport: DeckImportController? = null, decks: DeckLibraryController? = null, lobby: LobbyController? = null,
    cubes: CubeController? = null, pods: PodController? = null) {
    val state = controller.state
    // WEB-037: the desktop layout draws the landscape shell (its permanent side menu) whatever the window's shape
    val desktop = desktopLayout()
    val landscape = landscapeWindow || desktop
    // Shown only while signed in.
    val account = state.account ?: run {
        Faults.report("AuthenticatedShell", "shown without an account", mapOf("destination" to state.destination.toString()))
        return
    }
    var collectionCubes by remember { mutableStateOf(false) }
    if (catalog != null && state.avatarChooser) { AvatarChooser(controller, catalog); return }
    val tabletop = state.destination == ShellDestination.GAMES && lobby?.state?.selected != null &&
        lobby.state.detail?.lifecycle == org.asyncttwiz.protocol.GameLifecycle.ACTIVE
    // A started pod is a play screen: the modal hamburger replaces the side area in both orientations.
    val podPlay = state.destination == ShellDestination.PODS && pods?.state?.let {
        it.selected != null && it.creating == null && it.detail?.phase.let { phase -> phase != null && phase != org.asyncttwiz.protocol.PodPhase.SETUP }
    } == true
    ScreenBackHandler(state.drawerOpen || state.destination != ShellDestination.MENU) { controller.back() }
    LaunchedEffect(state.destination) {
        if (state.destination != ShellDestination.DECKS && !(state.destination == ShellDestination.GAMES && lobby?.state?.picker?.importing == true)) deckImport?.exit()
        if (state.destination == ShellDestination.DECKS && decks?.state?.selected == null) decks?.refresh()
        if (state.destination != ShellDestination.GAMES) lobby?.closePicker()
        lobby?.refresh()
        pods?.refresh()
    }
    LaunchedEffect(state.drawerOpen) {
        if (state.drawerOpen) pods?.refresh()
    }
    // Section 3.6: while the card records install, controls that read them are greyed and content needing them waits
    val records = catalog?.state?.records ?: CardRecords.Ready
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalCardRecords provides records, LocalCardRecordsRetry provides catalog?.let { it::retry },
                LocalPodChat provides pods?.chat) {
                if (state.destination == ShellDestination.GAMES && lobby?.state?.picker?.importing == true && deckImport?.state?.active == true) {
                    DeckImportScreen(deckImport, landscape, if (!landscape) ({ controller.drawer(true) }) else null, catalog)
                } else if (state.destination == ShellDestination.GAMES && lobby?.state?.selected != null && records != CardRecords.Ready) {
                    // A game's table needs card records: a game opened from elsewhere (a notification) waits for them
                    Column(Modifier.fillMaxSize()) {
                        ScreenHeader(lobby.state.games.firstOrNull { it.id == lobby.state.selected }?.gameName ?: "Game",
                            if (!landscape) ({ controller.drawer(true) }) else null)
                        CardRecordsPlaceholder(Modifier.weight(1f).fillMaxWidth().padding(16.dp))
                    }
                } else if (state.destination == ShellDestination.GAMES && lobby?.state?.selected != null) {
                    LaunchedEffect(lobby.state.picker?.importing, deckImport?.state?.active) {
                        if (lobby.state.picker?.importing == true && deckImport?.state?.active != true) lobby.importedForPicker()
                    }
                    WaitingLobby(lobby, account.accountId, if (!landscape || tabletop) ({ controller.drawer(true) }) else null, landscape,
                        importDeck = deckImport?.let { importer -> { lobby.importForPicker(); importer.open() } })
                } else if (state.destination == ShellDestination.PODS && pods != null) {
                    PodsDestination(pods, account.accountId, if (!landscape || podPlay) ({ controller.drawer(true) }) else null, lobby,
                        manageCubes = {
                            collectionCubes = true
                            controller.push(ShellDestination.DECKS)
                        }, mainMenu = { controller.navigate(ShellDestination.MENU) })
                } else if (state.destination == ShellDestination.DECKS && cubes != null) {
                    ManageCollectionScreen(cubes, decks, deckImport, landscape, if (!landscape) ({ controller.drawer(true) }) else null, catalog,
                        draftCube = pods?.let { podController -> { id -> podController.newPod(id); controller.push(ShellDestination.PODS) } },
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
                }, catalog, deckImport, decks, lobby, account.accountId, controller::logout, controller, pods,
                    push = controller::push)
            }
        }
    }
    Box(Modifier.fillMaxSize().testTag("authenticated-identity")
        .semantics { contentDescription = "Signed in as ${account.username}" }) {
        if (landscape && !tabletop && !podPlay) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(if (desktop) DesktopScreens.SideMenuWidth else 220.dp).fillMaxHeight(), color = Color.White,
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
            lobby.state.error?.takeIf { lobby.state.draft == null && !(state.destination == ShellDestination.PODS && pods != null) }
                ?: lobby.state.searchError ?: lobby.state.connectionMessage,
            lobby::dismissSessionError, Modifier.align(Alignment.BottomCenter))
        FloatingTransientError(state.profileError, controller::dismissProfileError, Modifier.align(Alignment.BottomCenter))
        val messageShown = state.profileError != null || lobby?.state?.let { it.error != null || it.searchError != null || it.connectionMessage != null } == true ||
            pods?.state?.let { it.error != null || it.notice != null || it.uncertain } == true
        ServerUnreachableNote(!tabletop && !messageShown && (lobby?.state?.unreachable == true || pods?.state?.unreachable == true),
            Modifier.align(Alignment.BottomCenter))
    }
    // Own the single dialog host here, outside route/orientation branches, not in MainActivity.
    // This also keeps dialogs available when the authenticated shell is composed on its own.
    if (lobby != null) {
        SetupCancellationDialogs(lobby)
        GameDeletionDialogs(lobby)
    }
}

@Composable
fun Navigation(controller: AuthController, close: Boolean, lobby: LobbyController?, pods: PodController? = null) {
    Column(Modifier.fillMaxSize()) {
        if (close) HoldTip("Close navigation", Modifier.align(Alignment.End)) {
            IconButton(onClick = { controller.drawer(false) }) { EntryIcon("Close navigation", "close") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            NavigationItem("Games", null, controller.state.destination == ShellDestination.GAMES) {
                lobby?.back()
                controller.navigate(ShellDestination.GAMES)
            }
            InitialLoadArea(lobby == null || lobby.state.loaded, Modifier.fillMaxWidth()
                .heightIn(min = if (lobby != null && !lobby.state.loaded) 80.dp else 0.dp),
                failed = lobby?.state?.error != null, retry = { lobby?.refresh() }, loading = lobby?.state?.busy == true) {
            Column {
            val accountId = controller.state.account?.accountId.orEmpty()
            if (lobby != null) for (game in navigationGameRows(lobby.state.games, lobby.state.invitations, accountId)) {
                val incoming = incomingInvitation(game, accountId)
                val selectedGame = !incoming && controller.state.destination == ShellDestination.GAMES && lobby?.state?.selected == game.id
                // One combined handler owns tap/hold; nesting another clickable drawer item would
                // allow a recognized context hold to also open the game on release.
                Surface(Modifier.padding(start = 20.dp).fillMaxWidth().heightIn(min = 72.dp).testTag("navigation-game-${game.id}")
                    .gameRowActions(lobby, game) {
                        // Like the Menu list: an invitation is answered on the Games screen, not opened as a lobby.
                        if (incoming) { lobby.back(); controller.navigate(ShellDestination.GAMES) }
                        else { controller.navigate(ShellDestination.GAMES); lobby.open(game.id) }
                    }
                    .semantics { selected = selectedGame }, shape = MaterialTheme.shapes.extraLarge,
                    color = if (selectedGame) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                    Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(game.opponent(accountId))
                        Column(Modifier.weight(1f)) {
                            Text(game.gameName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            // Its own line: beside the chevron the narrow drawer squeezed the name to one letter.
                            if (incoming) Text("Invitation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("${game.opponent(accountId).username} · ${gameStatus(game, accountId)} · ${gameLastActivity(game)}",
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
            if (!pods?.state?.pods.isNullOrEmpty()) {
                Column {
                    // LOB-018: the shared pod order (DRF-013); invitations are answered on the Pods screen.
                    val podRows = orderedPods(pods?.state?.pods.orEmpty(), emptyList()).mapNotNull { it.pod }
                    val stackWidth = podStackWidth(pods?.state?.pods.orEmpty(), controller.state.account?.accountId)
                    for (pod in podRows) {
                        val selectedPod = controller.state.destination == ShellDestination.PODS && pods?.state?.selected == pod.id
                        Surface(Modifier.padding(start = 20.dp).fillMaxWidth().heightIn(min = 72.dp)
                            .testTag("navigation-pod-${pod.id}")
                            .clickable { pods?.open(pod.id); controller.navigate(ShellDestination.PODS) }
                            .semantics { selected = selectedPod }, shape = MaterialTheme.shapes.extraLarge,
                            color = if (selectedPod) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                            Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AvatarStack(podNavigationAvatars(pod.members, controller.state.account?.accountId),
                                    ring = if (selectedPod) MaterialTheme.colorScheme.secondaryContainer else Color.White, width = stackWidth)
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
            // LOB-018: Games and Pods first, then the menu section
            NavigationItem("Menu", null, controller.state.destination == ShellDestination.MENU) {
                controller.navigate(ShellDestination.MENU)
            }
            for (destination in listOf(ShellDestination.DECKS, ShellDestination.CONTACTS, ShellDestination.STATS, ShellDestination.SETTINGS)) {
                NavigationItem(destination.title, destination.icon(), controller.state.destination == destination, indented = true) {
                    if (destination == ShellDestination.CONTACTS) lobby?.contacts(false)
                    controller.navigate(destination)
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
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("navigation-$label").clickable(onClick = action).semantics { this.selected = selected }
        .padding(start = if (indented) 20.dp else 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) EntryIcon(null, icon)
        Text(label, fontWeight = if (icon == null) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ShellContent(destination: ShellDestination, hamburger: Boolean, open: () -> Unit, navigate: (ShellDestination) -> Unit,
    catalog: CatalogViewModel?, deckImport: DeckImportController?, decks: DeckLibraryController?, lobby: LobbyController?, accountId: String,
    logout: () -> Unit = {}, auth: AuthController? = null, pods: PodController? = null,
    push: (ShellDestination) -> Unit = navigate) {
    // WEB-038, WEB-043: on the desktop layout lists and Settings keep a readable width at the left of their room
    val readable = when (destination) {
        ShellDestination.GAMES, ShellDestination.CONTACTS -> Modifier.desktopReadable()
        ShellDestination.SETTINGS -> Modifier.desktopReadable(DesktopScreens.SettingsMaxWidth)
        else -> Modifier
    }
    Column(readable.fillMaxSize()) {
        if (destination == ShellDestination.MENU) {
            if (hamburger) ScreenHeader("", open) { WizardLogo(Modifier.padding(end = 8.dp), compact = true) }
        } else ScreenHeader(if (destination == ShellDestination.CONTACTS && lobby?.state?.choosingOpponent == true) "Choose opponent" else destination.title,
            if (hamburger) open else null, Modifier.testTag("destination-title")) {
            if (destination == ShellDestination.GAMES)
                ScreenCreateAction("New game", "add", { lobby?.contacts(true); push(ShellDestination.CONTACTS) },
                    Modifier.testTag("new-game"), enabled = cardRecordsReady())
            if (destination == ShellDestination.DECKS && deckImport != null)
                ScreenCreateAction("Import deck", "import", deckImport::open, Modifier.testTag("import-deck"), enabled = cardRecordsReady())
        }
        if (destination == ShellDestination.GAMES || destination == ShellDestination.DECKS)
            CardRecordsNote(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (destination == ShellDestination.CONTACTS && lobby != null) ContactsContent(lobby)
            else if (destination == ShellDestination.GAMES && lobby != null) GamesContent(lobby, accountId)
            else if (destination == ShellDestination.DECKS && decks != null) SavedDeckCollection(decks)
            else if (destination == ShellDestination.SETTINGS) SettingsContent(catalog, accountId, logout, auth,
                // The account's server: the lobby's (the same as the build's in the app)
                accountSettings = rememberAccountSettingsSource(accountId, auth, lobby?.accountOrigin ?: org.asyncttwiz.appui.AppBuild.ACCOUNT_ORIGIN))
            else if (destination == ShellDestination.STATS && lobby != null) StatsContent(lobby.history, accountId,
                decks?.let { library -> { id: String -> navigate(ShellDestination.DECKS); library.open(id) } })
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
                     for (item in listOf(ShellDestination.GAMES, ShellDestination.PODS, ShellDestination.DECKS, ShellDestination.CONTACTS, ShellDestination.STATS, ShellDestination.SETTINGS)) {
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
                                     val incoming = incomingInvitation(game, accountId)
                                     val ready = cardRecordsReady()
                                     GameRow(game, accountId, Modifier.fillMaxWidth().testTag("quick-continue-${game.id}")
                                         .gameRowActions(lobby, game, enabled = ready || incoming) {
                                             navigate(ShellDestination.GAMES)
                                             if (!incoming) lobby.open(game.id)
                                         }, trailing = if (incoming) ({
                                             MenuInvitationActions(game.id in lobby.state.connectedGames && game.id !in lobby.state.pendingGames,
                                                 acceptEnabled = !game.invitationHeld && ready) {
                                                 lobby.respond(game, it)
                                             }
                                         }) else null)
                                 }
                             }
                         }
                         if (item == ShellDestination.PODS && pods != null) {
                             val entries = quickContinuePods(pods.state.pods, pods.state.invitations)
                             val stackWidth = podStackWidth(entries.mapNotNull { it.pod }, accountId)
                             if (entries.isNotEmpty()) MenuActivityPanel {
                                 entries.forEachIndexed { index, entry ->
                                     if (index > 0) HorizontalDivider(color = ScreenBorder)
                                     MenuPodRow(entry, accountId, stackWidth, { accept ->
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
    }
}

private fun ShellDestination.icon(): String = when (this) {
    ShellDestination.GAMES -> "games"
    ShellDestination.PODS -> "pods"
    ShellDestination.DECKS -> "decks"
    ShellDestination.CONTACTS -> "contacts"
    ShellDestination.SETTINGS -> "settings"
    ShellDestination.STATS -> "stats"
    ShellDestination.MENU -> "menu"
}
