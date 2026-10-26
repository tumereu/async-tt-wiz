package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*

@Composable
internal fun PodsDestination(controller: PodController, accountId: String, navigation: (() -> Unit)?,
    lobby: LobbyController?, manageCubes: () -> Unit) {
    val state = controller.state
    val owner = LocalLifecycleOwner.current
    DisposableEffect(controller) { onDispose { controller.flushSetup() } }
    LaunchedEffect(controller, state.selected) {
        controller.shown()
        while (true) {
            delay(10_000)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.refresh()
        }
    }
    LaunchedEffect(state.selected, state.setup?.revision) {
        if (state.setup != null) { delay(600); controller.flushSetup() }
    }
    BackHandler(state.selected != null || state.creating != null) { controller.back() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (state.creating != null) "New pod" else state.detail?.name ?: "Pods", navigation) {
            if (state.selected == null && state.creating == null) {
                TextButton(onClick = { controller.newPod() }, enabled = state.pending == 0) { Text("+ New pod") }
            } else {
                TextButton(onClick = controller::back) { Text("Back") }
                state.detail?.let { PodOverflow(controller, it.creator.accountId == accountId, it.phase) }
            }
        }
        state.notice?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary) }
        if (state.error != null || state.uncertain) Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.error ?: "An earlier request needs to be checked.", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = controller::retry, enabled = !state.loading) { Text("Retry") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.loading && state.pending > 0 && (state.creating?.setSource != null || state.setup?.setSource != null))
            Text("Uploading set source and saving pod…", Modifier.padding(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.creating != null -> NewPodScreen(controller, manageCubes)
                state.selected != null -> InitialLoadArea(state.detail != null, failed = state.error != null,
                    retry = controller::refresh, loading = state.loading) {
                    state.detail?.let { detail ->
                        Column(Modifier.fillMaxSize()) {
                            PodMemberStrip(detail)
                            if (state.choosingMember && lobby != null) PodChooseMember(controller, lobby, detail)
                            else if (detail.phase == PodPhase.SETUP) PodSetupScreen(controller, detail, accountId, lobby)
                            else PodDraftScreen(controller, detail, accountId, lobby)
                        }
                    }
                }
                else -> PodsList(state, controller::open, controller::respond, controller::refresh)
            }
        }
    }
    if (controller.setPicker.state.open) PodSetPickerDialog(controller.setPicker)
    if (state.confirmation != null && state.detail != null) ScreenDialog(controller::dismissConfirmation) {
        val action = state.confirmation
        Text(when (action) {
            PodConfirmation.DESTROY -> "Destroy pod"
            PodConfirmation.LEAVE -> "Leave pod"
            PodConfirmation.CANCEL -> "Cancel draft"
            PodConfirmation.SUBMIT -> "Submit deck"
        }, style = MaterialTheme.typography.titleLarge)
        Text(podConfirmationText(action, state.detail))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenOutlinedButton(controller::dismissConfirmation, enabled = !state.uncertain && state.pending == 0) { Text("Back") }
            ScreenButton(if (state.uncertain) controller::retry else controller::confirm,
                enabled = !state.loading && (state.pending == 0 || state.uncertain)) { Text(if (state.uncertain) "Retry" else "Confirm") }
        }
    }
}

@Composable
private fun PodOverflow(controller: PodController, creator: Boolean, phase: PodPhase) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ScreenIconButton("Pod actions", "more", { expanded = true })
        DropdownMenu(expanded, { expanded = false }) {
            if (creator && phase == PodPhase.DRAFTING) DropdownMenuItem(text = { Text("Cancel draft") }, onClick = {
                expanded = false; controller.requestConfirmation(PodConfirmation.CANCEL)
            })
            DropdownMenuItem(text = { Text(if (creator) "Destroy" else "Leave") }, onClick = {
                expanded = false; controller.requestConfirmation(if (creator) PodConfirmation.DESTROY else PodConfirmation.LEAVE)
            })
        }
    }
}

@Composable
internal fun PodsList(state: PodState, open: (String) -> Unit, respond: (String, Boolean) -> Unit, refresh: () -> Unit) {
    InitialLoadArea(state.loaded, failed = state.error != null, retry = refresh, loading = state.loading) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.invitations.isNotEmpty()) item { Text("Invitations", style = MaterialTheme.typography.titleMedium) }
            items(state.invitations, key = { "invitation:${it.podId}" }) { invitation ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(invitation.name, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Avatar(invitation.creator); Text(invitation.creator.username)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScreenButton({ respond(invitation.podId, true) }, enabled = state.pending == 0) { Text("Accept") }
                            ScreenOutlinedButton({ respond(invitation.podId, false) }, enabled = state.pending == 0) { Text("Decline") }
                        }
                    }
                }
            }
            item {
                Text("MY PODS (${state.pods.size})", style = MaterialTheme.typography.labelLarge)
                if (state.pods.isEmpty()) Text("No pods yet. Create a pod to draft a cube or MTG set with friends.")
            }
            items(state.pods, key = { it.id }) { pod -> PodRow(pod, { open(pod.id) }) }
        }
    }
}

@Composable
internal fun PodRow(pod: PodSummary, open: () -> Unit, modifier: Modifier = Modifier, quick: Boolean = false) {
    val highlighted = pod.attention == PodAttention.YOUR_PICK
    Surface(onClick = open, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (highlighted) MaterialTheme.colorScheme.primary else ScreenBorder)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.widthIn(max = 136.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pod.members.forEach { Avatar(it) }
            }
            Column(Modifier.weight(1f)) {
                Text(pod.name, fontWeight = FontWeight.SemiBold)
                Text(if (quick) "Your pick" else podStatus(pod), style = MaterialTheme.typography.bodySmall)
            }
            EntryIcon(null, "chevron")
        }
    }
}

@Composable
private fun NewPodScreen(controller: PodController, manageCubes: () -> Unit) {
    val state = controller.state
    val draft = state.creating ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(draft.name, controller::newName, Modifier.fillMaxWidth(), label = { Text("Pod name") }, singleLine = true,
            enabled = state.pending == 0 && !state.uncertain, trailingIcon = {
                IconButton(controller::reroll, enabled = state.pending == 0 && !state.uncertain) { EntryIcon("Suggest another pod name", "shuffle") }
            })
        Text("Source", style = MaterialTheme.typography.titleMedium)
        val enabled = state.pending == 0 && !state.uncertain
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CubeChoice(state.cubes, draft.cubeId, "Cube", enabled, selected = draft.cubeId != null,
                label = "Cube", choose = controller::newCube)
            FilterChip(draft.setSource != null, controller::chooseSet, enabled = enabled, label = { Text("MTG set") })
        }
        val source = draft.setSource?.podSource() ?: state.cubes.find { it.id == draft.cubeId }?.let { PodSource(it.name, it.totalCopies) }
        source?.let { PodSourceSummary(it); if (it.kind == PodSourceKind.SET) PodSourceSupply(it, 0) }
        if (state.cubesLoaded && state.cubes.isEmpty() && draft.setSource == null) {
            Text("Choose an MTG set, or save a cube in Manage decks → Cubes.")
            ScreenOutlinedButton(manageCubes) { Text("Manage decks → Cubes") }
        }
        PodModeButtons(draft.mode, state.pending == 0 && !state.uncertain, controller::newMode)
        ScreenButton(controller::create, enabled = (draft.cubeId != null || draft.setSource != null) && draft.name.isNotBlank() && enabled) { Text("Create pod") }
    }
}

@Composable
private fun CubeChoice(cubes: List<CubeSummary>, chosen: String?, fallback: String, enabled: Boolean,
    selected: Boolean = chosen != null, label: String? = null, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { expanded = true }, enabled = enabled,
            label = { Text(label ?: cubes.find { it.id == chosen }?.name ?: fallback) },
            leadingIcon = if (selected) ({ EntryIcon(null, "ready") }) else null)
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 360.dp)) {
            cubes.forEach { cube -> DropdownMenuItem(text = { Text("${cube.name} · ${cube.totalCopies} cards") },
                onClick = { expanded = false; choose(cube.id) }) }
        }
    }
}

@Composable
private fun PodModeButtons(mode: DraftMode, enabled: Boolean, choose: (DraftMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DraftMode.entries.forEach { value -> FilterChip(mode == value, { choose(value) }, enabled = enabled,
            label = { Text(when (value) { DraftMode.BOOSTER -> "Booster"; DraftMode.GLIMPSE -> "Glimpse"; DraftMode.GRID -> "Grid" }) }) }
    }
}

@Composable
private fun PodMemberStrip(detail: PodDetail) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        detail.members.forEach { member ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(member.account)
                Text(member.account.username, style = MaterialTheme.typography.labelMedium)
                Text(podMemberStatus(member, detail.phase, detail.settings.mode), style = MaterialTheme.typography.labelSmall,
                    color = if (member.packsWaiting > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun PodSetupScreen(controller: PodController, detail: PodDetail, accountId: String, lobby: LobbyController?) {
    val state = controller.state
    val creator = detail.creator.accountId == accountId
    val enabled = creator && !state.uncertain && state.pending == 0
    val edit = state.setup
    val settings = edit?.settings ?: detail.settings
    val source = edit?.setSource?.podSource() ?: state.cubes.find { it.id == edit?.cubeId }?.let { PodSource(it.name, it.totalCopies) } ?: detail.source
    val needed = settings.supply(detail.members.size, emptyList()).needed
    val issues = settings.validate(detail.members.size.coerceAtLeast(2))
    LaunchedEffect(detail.id, creator) { if (creator) controller.loadCubes() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(edit?.name ?: detail.name, { controller.editSetup(name = it) }, Modifier.fillMaxWidth(), label = { Text("Pod name") }, readOnly = !enabled)
        Text("Source", style = MaterialTheme.typography.titleMedium)
        if (creator) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CubeChoice(state.cubes, edit?.cubeId, "Cube", enabled, selected = source.kind == PodSourceKind.CUBE,
                label = "Cube") { controller.editSetup(cubeId = it) }
            FilterChip(source.kind == PodSourceKind.SET, controller::chooseSet, enabled = enabled, label = { Text("MTG set") })
        }
        PodSourceSummary(source)
        PodSettingsFields(settings, detail.members.size, enabled, source) { controller.editSetup(settings = it) }
        Text("Sideboard limit", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(settings.sideboardLimit == null, { unlimited -> controller.editSetup(settings = settings.copy(sideboardLimit = if (unlimited) null else 0)) }, enabled = enabled)
            Spacer(Modifier.width(8.dp)); Text("Unlimited")
        }
        settings.sideboardLimit?.let { limit -> PodNumber("Maximum sideboard cards", limit, enabled) { controller.editSetup(settings = settings.copy(sideboardLimit = it)) } }
        PodSourceSupply(source, needed)
        if (settings.mode == DraftMode.GRID && detail.members.size !in 2..4)
            Text("Grid requires 2–4 accepted members to start.", color = MaterialTheme.colorScheme.error)
        if (issues.isNotEmpty()) Text("Check settings: ${issues.joinToString { it.name.lowercase().replace('_', ' ') }}", color = MaterialTheme.colorScheme.error)
        if (edit != null) Text("Settings changes pending…", style = MaterialTheme.typography.bodySmall)
        Text("Members (${detail.members.size}/${if (settings.mode == DraftMode.GRID) 4 else 8})", style = MaterialTheme.typography.titleMedium)
        detail.members.forEach { MemberLine(it.account, "accepted") }
        detail.pendingInvitations.forEach { MemberLine(it, "invitation pending") }
        if (creator) {
            ScreenOutlinedButton({ controller.chooseMember(true); lobby?.contacts(false) },
                enabled = lobby != null && detail.members.size + detail.pendingInvitations.size < 8 && state.pending == 0 && !state.uncertain) { Text("Invite player") }
            ScreenButton(controller::start, enabled = state.setup == null && state.pending == 0 && !state.uncertain && podCanStart(detail)) { Text("Start draft") }
            if (detail.members.size < 2) Text("At least two accepted members are needed to start.")
        }
    }
}

@Composable
internal fun PodSettingsFields(settings: DraftSettings, members: Int, enabled: Boolean, source: PodSource = PodSource(), change: (DraftSettings) -> Unit) {
    var advanced by remember { mutableStateOf(false) }
    PodModeButtons(settings.mode, enabled) { mode -> change(when (mode) {
        DraftMode.BOOSTER -> DraftSettings.booster()
        DraftMode.GLIMPSE -> DraftSettings.glimpse()
        DraftMode.GRID -> DraftSettings.grid()
    }.copy(sideboardLimit = settings.sideboardLimit)) }
    if (settings.mode == DraftMode.GRID) {
        PodNumber("Grids", settings.grids, enabled) { change(settings.copy(grids = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(settings.twoPicks, { change(settings.copy(twoPicks = it)) }, enabled = enabled && members == 2)
            Text("Two picks each")
        }
        if (members != 2) Text("Two picks each requires exactly 2 members.", style = MaterialTheme.typography.bodySmall)
    } else {
        PodNumber("Packs per player", settings.packsPerPlayer, enabled) { change(settings.copy(packsPerPlayer = it)) }
        if (source.kind == PodSourceKind.CUBE || source.boosterKind == SetBoosterKind.APPROXIMATE)
            PodNumber("Cards per pack", settings.cardsPerPack, enabled) { change(settings.copy(cardsPerPack = it)) }
    }
    TextButton({ advanced = !advanced }) { Text(if (advanced) "Advanced ▴" else "Advanced ▾") }
    if (advanced) {
        if (settings.mode == DraftMode.GRID) {
            PodNumber("Grids open at once", settings.gridsOpenAtOnce, enabled) { change(settings.copy(gridsOpenAtOnce = it)) }
        } else {
            PodNumber("Keep per pick", settings.keep, enabled) { change(settings.copy(keep = it)) }
            PodNumber("Burn per pick", settings.burn, enabled) { change(settings.copy(burn = it)) }
            PodNumber("Discard remaining at", settings.discardRemainingAt, enabled) { change(settings.copy(discardRemainingAt = it)) }
            PodNumber("Packs open at once", settings.packsOpenAtOnce, enabled) { change(settings.copy(packsOpenAtOnce = it)) }
            if (source.kind == PodSourceKind.CUBE) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(settings.colorBalance, { change(settings.copy(colorBalance = it)) }, enabled = enabled)
                Text("Color balancing")
            }
        }
    }
}

@Composable
internal fun PodNumber(label: String, value: Int, enabled: Boolean, change: (Int) -> Unit) {
    var text by remember(label) { mutableStateOf(TextFieldValue(value.toString())) }
    var focused by remember(label) { mutableStateOf(false) }
    var lastValid by remember(label) { mutableIntStateOf(value) }
    fun restore(number: Int) {
        val restored = number.toString()
        text = TextFieldValue(restored, TextRange(restored.length))
    }
    LaunchedEffect(value) {
        lastValid = value
        // Settings acknowledgements must not replace in-progress text or its selection.
        if (!focused && text.text.toIntOrNull() != value) restore(value)
    }
    OutlinedTextField(text, { input ->
        if (input.text.isEmpty() || input.text.all(Char::isDigit)) {
            val changed = input.text != text.text
            text = input
            if (changed) input.text.toIntOrNull()?.let { lastValid = it; change(it) }
        }
    }, Modifier.fillMaxWidth().onFocusChanged {
        if (focused && !it.isFocused && text.text.toIntOrNull() == null) restore(lastValid)
        focused = it.isFocused
    }, label = { Text(label) }, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun PodChooseMember(controller: PodController, lobby: LobbyController, detail: PodDetail) {
    val state = lobby.state
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Choose player", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton({ controller.chooseMember(false) }) { Text("Back") }
        }
        TabRow(state.contactsTab.ordinal) {
            ContactsTab.entries.forEach { tab -> Tab(state.contactsTab == tab, { lobby.contactsTab(tab) },
                text = { Text(if (tab == ContactsTab.CONTACTS) "Contacts" else "Search") }) }
        }
        if (state.contactsTab == ContactsTab.CONTACTS)
            ScreenTextField(state.contactFilter, lobby::filterContacts, Modifier.fillMaxWidth(), placeholder = "Filter contacts")
        else Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTextField(state.query, lobby::query, Modifier.weight(1f), placeholder = "Search usernames")
            ScreenIconButton("Search players", "search", lobby::search, enabled = !state.searching)
        }
        state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f)) {
            val accounts = if (state.contactsTab == ContactsTab.CONTACTS) state.filteredContacts else state.results
            if (accounts.isEmpty()) item { Text(if (state.searching || state.busy) "Loading players…" else "No players found.") }
            items(accounts, key = { it.accountId }) { account ->
                val existing = detail.members.any { it.account.accountId == account.accountId } || detail.pendingInvitations.any { it.accountId == account.accountId }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(account); Text(account.username, Modifier.weight(1f))
                    TextButton({ controller.invite(account) }, enabled = !existing && controller.state.pending == 0) { Text(if (existing) "Invited / member" else "Invite") }
                }
            }
        }
    }
}

@Composable
private fun PodDraftScreen(controller: PodController, detail: PodDetail, accountId: String, lobby: LobbyController?) {
    val state = controller.state
    val tabs = if (detail.phase == PodPhase.DRAFTING) listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS)
        else listOf(PodTab.BUILD, PodTab.STATS, PodTab.GAMES)
    val selected = state.tab.takeIf { it in tabs } ?: tabs.first()
    val build = detail.build ?: PodDeckBuild()
    val copies = (detail.grid?.mine?.pool ?: detail.draft?.pool).orEmpty().associateBy { it.id }
    val definitions = detail.definitions.associate { it.digest to it.definition }
    Column(Modifier.fillMaxSize()) {
        TabRow(tabs.indexOf(selected)) {
            tabs.forEach { tab -> Tab(selected == tab, { controller.tab(tab) }, text = { Text(tab.name.lowercase().replaceFirstChar(Char::uppercase)) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                PodTab.DRAFT -> if (detail.settings.mode == DraftMode.GRID) PodGridView(detail.grid, detail.settings.grids,
                    definitions, detail.members.map { it.account }, state.picking || state.uncertain,
                    controller::gridSelect, { controller.tab(PodTab.BUILD) }, Modifier.fillMaxSize())
                else PodPickView(detail.draft?.head, detail.draft?.packsBehind ?: 0, detail.settings,
                    definitions, detail.members.filter { it.packsWaiting > 0 && it.account.accountId != detail.draft?.player }.map { it.account },
                    state.picking || state.uncertain, controller::pick, { controller.tab(PodTab.BUILD) }, Modifier.fillMaxSize())
                PodTab.BUILD -> PodBuildView(build, copies, definitions, detail.settings.sideboardLimit,
                    !detail.submitted && !state.uncertain, detail.phase == PodPhase.DRAFTED && !detail.submitted && state.pending == 0,
                    controller::move, controller::adjustBasic, controller::submit, Modifier.fillMaxSize())
                PodTab.STATS -> PodStatsView(build, copies, definitions, Modifier.fillMaxSize())
                PodTab.GAMES -> PodGamesTab(detail, accountId, lobby)
            }
        }
    }
}

/** Members with a submitted deck can challenge each other; the new game opens in the normal lobby. */
@Composable
internal fun PodGamesTab(detail: PodDetail, accountId: String, lobby: LobbyController?) {
    val mine = detail.members.firstOrNull { it.account.accountId == accountId }
    val games = lobby?.state?.let { it.games + it.invitations }.orEmpty().filter { it.podId == detail.id }.distinctBy { it.id }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Members", style = MaterialTheme.typography.titleMedium)
        detail.members.forEach { member ->
            MemberLine(member.account, if (member.submitted) "deck submitted" else "building") {
                if (member.account.accountId != accountId && member.submitted && lobby != null)
                    TextButton({ lobby.challengeFromPod(detail.id, member.account.accountId, gameNameSuggestion()) },
                        enabled = mine?.submitted == true && !lobby.state.mutating) { Text("Challenge") }
            }
        }
        if (mine?.submitted != true) Text("Submit your deck to challenge other members.", style = MaterialTheme.typography.bodySmall)
        lobby?.state?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (games.isNotEmpty()) {
            Text("Games", style = MaterialTheme.typography.titleMedium)
            games.forEach { game ->
                val opponent = if (game.inviter.accountId == accountId) game.recipient else game.inviter
                Row(Modifier.fillMaxWidth().clickable { lobby?.open(game.id) }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(opponent)
                    Text(game.gameName, Modifier.weight(1f))
                    Text("vs ${opponent.username}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun MemberLine(account: AccountIdentity, status: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(account)
        Column(Modifier.weight(1f)) { Text(account.username); Text(status, style = MaterialTheme.typography.bodySmall) }
        trailing()
    }
}
