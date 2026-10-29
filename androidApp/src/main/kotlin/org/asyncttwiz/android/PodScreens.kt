package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*

@Composable
internal fun PodsDestination(controller: PodController, accountId: String, navigation: (() -> Unit)?,
    lobby: LobbyController?, manageCubes: () -> Unit, mainMenu: (() -> Unit)? = null) {
    val state = controller.state
    val pickTab = state.detail?.phase == PodPhase.DRAFTING && state.tab == PodTab.DRAFT
    val playScreen = state.detail?.phase?.let { it != PodPhase.SETUP } == true
    val pickMessages = remember { SnackbarHostState() }
    LaunchedEffect(pickTab, state.error, state.notice, state.uncertain) {
        if (pickTab) (state.error ?: state.notice ?: "Request not confirmed".takeIf { state.uncertain })?.let {
            val result = pickMessages.showSnackbar(it, actionLabel = "Retry".takeIf { state.uncertain })
            if (result == SnackbarResult.ActionPerformed) controller.retry()
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(controller) { onDispose { controller.flushSetup() } }
    LaunchedEffect(controller, state.selected) {
        controller.shown()
        while (true) {
            delay(10_000)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.refresh()
        }
    }
    LaunchedEffect(state.selected, state.setup?.revision, state.uncertain) {
        if (state.setup != null) { delay(600); controller.flushSetup() }
    }
    BackHandler(state.selected != null || state.creating != null) { controller.back() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!playScreen) ScreenHeader(if (state.creating != null) "New pod" else state.detail?.name ?: "Pods", navigation) {
                if (state.selected == null && state.creating == null) {
                    ScreenCreateAction("New pod", "add", { controller.newPod() }, Modifier.testTag("new-pod"))
                } else {
                    state.detail?.let { PodOverflow(controller, it.creator.accountId == accountId, it.phase,
                        startNow = it.pendingInvitations.isNotEmpty() && podCanStart(it)) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.creating != null -> NewPodScreen(controller)
                    state.selected != null -> InitialLoadArea(state.detail != null, failed = state.error != null,
                        retry = { controller.refresh(requested = true) }, loading = state.loading) {
                        state.detail?.let { detail ->
                            Column(Modifier.fillMaxSize()) {
                                if (detail.phase == PodPhase.SETUP) PodMemberStrip(detail)
                                if (state.choosingMember && lobby != null) PodChooseMember(controller, lobby, detail)
                                else if (detail.phase == PodPhase.SETUP) PodSetupScreen(controller, detail, accountId, lobby)
                                else PodDraftScreen(controller, detail, accountId, lobby, navigation, mainMenu)
                            }
                        }
                    }
                    else -> PodsList(state, controller::open, controller::respond) { controller.refresh(requested = true) }
                }
                if (!pickTab) {
                    FloatingTransientError(state.error ?: state.notice, controller::dismissError, Modifier.align(Alignment.BottomCenter))
                    if (state.uncertain) ScreenIconButton("Retry pod request", "refresh", controller::retry,
                        Modifier.align(Alignment.BottomEnd), enabled = !state.loading)
                }
                lobby?.let { PodChallengeFeedback(it.state, it::retryPodChallenge, it::dismissSessionError, Modifier.matchParentSize()) }
            }
        }
        if (pickTab) SnackbarHost(pickMessages, Modifier.align(Alignment.BottomCenter))
    }
    if (controller.setPicker.state.open) PodSourcePickerDialog(controller, manageCubes)
    if (state.confirmation != null && state.detail != null) {
        val action = state.confirmation
        ScreenDialog(controller::dismissConfirmation, title = when (action) {
            PodConfirmation.DESTROY -> "Destroy pod"
            PodConfirmation.LEAVE -> "Leave pod"
            PodConfirmation.CANCEL -> "Cancel draft"
            PodConfirmation.SUBMIT -> "Submit deck"
            PodConfirmation.START -> "Start now"
        }, actions = {
            DialogCancelButton(when (action) {
                PodConfirmation.DESTROY, PodConfirmation.LEAVE -> "Keep pod"
                PodConfirmation.CANCEL -> "Keep draft"
                PodConfirmation.SUBMIT, PodConfirmation.START -> "Cancel"
            }, controller::dismissConfirmation, enabled = !state.uncertain && state.pending == 0)
            DialogMainButton(if (state.uncertain) "Retry" else when (action) {
                PodConfirmation.DESTROY -> "Destroy"
                PodConfirmation.LEAVE -> "Leave"
                PodConfirmation.CANCEL -> "Cancel draft"
                PodConfirmation.SUBMIT -> "Submit"
                PodConfirmation.START -> "Start now"
            }, if (state.uncertain) controller::retry else controller::confirm,
                enabled = !state.loading && (state.pending == 0 || state.uncertain), danger = action != PodConfirmation.SUBMIT && action != PodConfirmation.START)
        }) {
            Text(podConfirmationText(action, state.detail))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Available even when the original pod or opponent can no longer be opened. */
@Composable
internal fun PodChallengeFeedback(state: LobbyState, retry: () -> Unit, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (state.retryPodChallengeName != null) {
            FilledTonalIconButton(retry, Modifier.align(Alignment.TopEnd).padding(12.dp), enabled = !state.mutating) {
                EntryIcon("Retry challenge", "refresh")
            }
        }
        FloatingTransientError(state.error, dismiss, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PodOverflow(controller: PodController, creator: Boolean, phase: PodPhase, compact: Boolean = false,
    startNow: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (compact) IconButton({ expanded = true }, Modifier.size(32.dp)) { EntryIcon("Pod actions", "more") }
        else ScreenIconButton("Pod actions", "more", { expanded = true })
        DropdownMenu(expanded, { expanded = false }) {
            // Starting is otherwise "Start when everyone accepts" while invitations are pending.
            if (creator && phase == PodPhase.SETUP && startNow) DropdownMenuItem(text = { Text("Start now") }, onClick = {
                expanded = false; controller.requestConfirmation(PodConfirmation.START)
            })
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
                if (state.pods.isEmpty()) Text("No pods yet")
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
private fun NewPodScreen(controller: PodController) {
    val state = controller.state
    val draft = state.creating ?: return
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val source = state.sourcePreview ?: draft.setSource?.podSource() ?: state.cubes.find { it.id == draft.cubeId }?.let { PodSource(it.name, it.totalCopies) }
            PodSettingsForm(draft.name, draft.settings, source, 2, true, controller::newName, controller::chooseSet,
                controller::newSettings, controller::reroll)
        }
        Button(controller::create, Modifier.fillMaxWidth().padding(horizontal = 8.dp), enabled = (draft.cubeId != null || draft.setSource != null) && draft.name.isNotBlank() &&
            draft.settings.validate(2).isEmpty() && state.pending == 0 && !state.uncertain && state.sourcePreview == null) { Text("Create") }
    }
}

@Composable
private fun PodMemberStrip(detail: PodDetail) {
    Row(Modifier.fillMaxWidth().testTag("pod-member-strip").horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
    val enabled = creator
    val edit = state.setup
    val settings = edit?.settings ?: detail.settings
    val source = state.sourcePreview ?: detail.source
    LaunchedEffect(detail.id, creator) { if (creator) controller.loadCubes() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PodSettingsForm(edit?.name ?: detail.name, settings, source, detail.members.size, enabled,
            { controller.editSetup(name = it) }, controller::chooseSet, controller::editSettings)
        Text("Members (${detail.members.size}/${if (settings.mode == DraftMode.GRID) 4 else 8})", style = MaterialTheme.typography.titleMedium)
        detail.members.forEach { MemberLine(it.account, "accepted") }
        detail.pendingInvitations.forEach { MemberLine(it, "invitation pending") }
        if (creator) {
            ScreenOutlinedButton({ controller.chooseMember(true); lobby?.contacts(false) },
                enabled = lobby != null && detail.members.size + detail.pendingInvitations.size < 8) { EntryIcon("Invite player", "person") }
            detail.autoStartOff?.let { Text(podAutoStartOffText(it), color = MaterialTheme.colorScheme.error) }
            val idle = state.setup == null && state.sourcePreview == null && state.pending == 0 && !state.uncertain
            if (detail.pendingInvitations.isEmpty())
                ScreenButton(controller::start, enabled = idle && podCanStart(detail)) { Text("Start draft") }
            else if (detail.autoStart) ScreenOutlinedButton({ controller.autoStart(false) }, enabled = idle) { Text(podAutoStartLabel(detail)) }
            else ScreenButton({ controller.autoStart(true) }, enabled = idle) { Text(podAutoStartLabel(detail)) }
        } else if (detail.autoStart) {
            Text("Starts automatically when everyone accepts", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PodChooseMember(controller: PodController, lobby: LobbyController, detail: PodDetail) {
    PlayerPicker(lobby) { account ->
        val existing = detail.members.any { it.account.accountId == account.accountId } || detail.pendingInvitations.any { it.accountId == account.accountId }
        ScreenIconButton(if (existing) "Invited / member" else "Invite player", if (existing) "ready" else "send",
            { controller.invite(account) }, enabled = !existing)
    }
}

@Composable
private fun PodDraftScreen(controller: PodController, detail: PodDetail, accountId: String, lobby: LobbyController?,
    navigation: (() -> Unit)?, mainMenu: (() -> Unit)?) {
    val state = controller.state
    val tabs = podTabs(detail.phase)
    val selected = state.tab.takeIf { it in tabs } ?: podDefaultTab(detail.phase, detail.submitted)
    val build = detail.build ?: PodDeckBuild()
    val copies = (detail.grid?.mine?.pool ?: detail.draft?.pool).orEmpty().associateBy { it.id }
    val definitions = detail.definitions.associate { it.digest to it.definition }
    val pickColumns = rememberPodPickColumns()
    val buildColumns = rememberPodBuildColumns()
    val buildSideBySide = rememberPodBuildSideBySide()
    val waiting = podWaitingText(detail, accountId)
    Column(Modifier.fillMaxSize()) {
        PodPlayHeader(detail, navigation) {
            PodViewSettingsButton(pickColumns.takeIf { detail.settings.mode != DraftMode.GRID }, buildColumns, buildSideBySide)
        }
        PodDraftTabs(tabs, selected, controller::tab) {
            PodOverflow(controller, detail.creator.accountId == accountId, detail.phase, compact = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                PodTab.DRAFT -> if (detail.settings.mode == DraftMode.GRID) PodGridView(detail.grid, detail.settings.grids,
                    definitions, detail.members.map { it.account }, state.picking || state.uncertain,
                    controller::gridSelect, Modifier.fillMaxSize(), waitingText = waiting, onMainMenu = mainMenu)
                else PodPickView(detail.draft?.head, detail.settings, definitions,
                    state.uncertain, { keep, burn -> controller.pick(keep, burn) }, Modifier.fillMaxSize(),
                    librarians = detail.draft?.pool.orEmpty().filter { it.librarian }, onConfirmLibrarians = controller::pick,
                    nextPack = detail.draft?.queuedPacks?.firstOrNull(), columns = pickColumns,
                    waitingText = waiting, onMainMenu = mainMenu,
                    savedMarks = controller.marks(detail.draft?.head), onMarks = { controller.mark(detail.draft?.head, it) },
                    orientation = podPickOrientationText(detail))
                PodTab.BUILD -> PodBuildView(build, copies, definitions, detail.settings.sideboardLimit,
                    !detail.submitted && !state.uncertain, detail.phase == PodPhase.DRAFTED && !detail.submitted,
                    controller::move, { index, delta -> controller.adjustBasic(index, delta) }, controller::submit, Modifier.fillMaxSize(),
                    detail.deckName, controller::deckName, detail.submitted, buildColumns, buildSideBySide,
                    controller.canUndoBuild, controller::undoBuild, controller::revertMove,
                    onAdjustSideBasic = { index, delta -> controller.adjustBasic(index, delta, side = true) })
                PodTab.STATS -> PodStatsView(build, copies, definitions, Modifier.fillMaxSize(), controller.notes(), controller::editNotes)
                PodTab.GAMES -> PodGamesTab(detail, accountId, lobby)
            }
        }
    }
}

@Composable
internal fun PodDraftTabs(tabs: List<PodTab>, selected: PodTab, select: (PodTab) -> Unit, overflow: @Composable () -> Unit) {
    Surface {
        Row(Modifier.fillMaxWidth().testTag("pod-tabs"), verticalAlignment = Alignment.CenterVertically) {
            TabRow(tabs.indexOf(selected), Modifier.weight(1f)) {
                tabs.forEach { tab -> Tab(selected == tab, { select(tab) },
                    text = { Text(tab.name.lowercase().replaceFirstChar(Char::uppercase)) }) }
            }
            Box(Modifier.width(40.dp).height(48.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) { overflow() }
                HorizontalDivider(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

internal val PodPlayHeaderHeight = 48.dp

/** Hamburger (modal drawer), a very small pod title, the readiness avatars under it and a trailing view settings cog. */
@Composable
internal fun PodPlayHeader(detail: PodDetail, navigation: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Surface {
        Row(Modifier.fillMaxWidth().height(PodPlayHeaderHeight).testTag("pod-play-header"),
            verticalAlignment = Alignment.CenterVertically) {
            if (navigation != null) IconButton(navigation, Modifier.size(48.dp)) { EntryIcon("Open navigation", "menu") }
            else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(detail.name, Modifier.height(16.dp).testTag("pod-play-title"), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                PodDraftMemberRow(detail)
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) { trailing() }
        }
    }
}

/** Readiness badges only while drafting; afterwards the avatars just show who is in the pod. */
@Composable
internal fun PodDraftMemberRow(detail: PodDetail) {
    val drafting = detail.phase == PodPhase.DRAFTING
    Row(Modifier.fillMaxWidth().height(28.dp).horizontalScroll(rememberScrollState()).testTag("pod-draft-members"),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        detail.members.forEach { member ->
            // Grid: the server counts the member's pickable grids, including grids of rounds this viewer has not opened.
            val waiting = member.packsWaiting
            Box(Modifier.size(28.dp).semantics {
                if (drafting) stateDescription = if (waiting == 0) "Ready" else "$waiting packs waiting"
            }) {
                Avatar(member.account, size = 28.dp)
                if (drafting && waiting <= 9) Box(Modifier.align(Alignment.BottomEnd).size(12.dp)
                    .background(if (waiting == 0) Color(0xFF237A38) else MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text(if (waiting == 0) "✓" else "$waiting", color = Color.White, fontSize = 9.sp, lineHeight = 10.sp)
                }
            }
        }
    }
}

/** Who still holds packs (or grid turns), plus the caller's round. */
internal fun podWaitingText(detail: PodDetail, accountId: String): String {
    val grid = detail.grid?.publicView?.takeIf { detail.settings.mode == DraftMode.GRID }
    val busy = detail.members.filter { it.packsWaiting > 0 }.map { it.account.accountId }.toSet()
    val names = detail.members.map { it.account }.filter { it.accountId != accountId && it.accountId in busy }.map { it.username }
    val who = if (names.isEmpty()) "Waiting for other players" else "Waiting for ${names.joinToString(", ")}"
    val round = grid?.takeIf { !it.finished }?.let { (detail.grid?.mine?.round?.takeIf { it > 0 } ?: it.round) to it.rounds }
        ?: detail.draft?.takeIf { it.rounds > 0 && it.round in 1..it.rounds }?.let { it.round to it.rounds }
    val draft = detail.draft
    if (grid != null || draft == null) return who + (round?.let { " · Round ${it.first} of ${it.second}" } ?: "")
    // Booster and Glimpse say whether packs still come back to this player; each part goes on its own line.
    val status = when {
        draft.incoming > 0 -> "$who\n${draft.incoming} ${if (draft.incoming == 1) "pack will" else "packs will"} come back to you"
        names.isEmpty() -> "All your picks are done\nOther players are finishing"
        else -> "All your picks are done\n${names.joinToString(", ")} ${if (names.size == 1) "is" else "are"} finishing"
    }
    return status + (round?.let { "\nRound ${it.first} of ${it.second}" } ?: "")
}

/** The pick screen's orientation line: "Round 2/6 · Pick 2 · from Matti", or "… · your pack" before its first pick. */
internal fun podPickOrientationText(detail: PodDetail): String? {
    val draft = detail.draft ?: return null
    val head = draft.head ?: return null
    val from = head.passedBy?.let { id ->
        "from " + (detail.members.firstOrNull { it.account.accountId == id }?.account?.username ?: "another player")
    } ?: "your pack"
    val round = if (draft.rounds > 0 && draft.round in 1..draft.rounds) "Round ${draft.round}/${draft.rounds} · " else ""
    return "${round}Pick ${head.pick} · $from"
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
                if (podChallengeOffered(detail, member, accountId) && lobby != null)
                    FilledIconButton({ lobby.challengeFromPod(detail.id, member.account.accountId, gameNameSuggestion()) },
                        enabled = mine?.submitted == true && !lobby.state.mutating) {
                        Icon(painterResource(R.drawable.swords_outlined), contentDescription = "Challenge")
                    }
            }
        }
        if (games.isNotEmpty()) {
            Text("Games", style = MaterialTheme.typography.titleMedium)
            games.forEach { game ->
                val opponent = if (game.inviter.accountId == accountId) game.recipient else game.inviter
                // An invitation to answer is answered in place, like the Menu list; it is not opened as a lobby yet.
                val incoming = incomingInvitation(game, accountId)
                Row(Modifier.fillMaxWidth().testTag("pod-game-${game.id}")
                    .then(if (incoming) Modifier else Modifier.clickable { lobby?.open(game.id) }).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(opponent)
                    Column(Modifier.weight(1f)) {
                        Text(game.gameName)
                        if (game.invitationHeld || incoming) Text(gameStatus(game, accountId), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("vs ${opponent.username}", style = MaterialTheme.typography.bodySmall)
                    if (incoming && lobby != null) MenuInvitationActions(game.id in lobby.state.connectedGames && game.id !in lobby.state.pendingGames,
                        acceptEnabled = !game.invitationHeld) { lobby.respond(game, it) }
                }
            }
        }
    }
}

/** Once the draft is over any other member can be challenged; one still building gets the invitation on submitting. */
internal fun podChallengeOffered(detail: PodDetail, member: PodMember, accountId: String) =
    detail.phase == PodPhase.DRAFTED && member.account.accountId != accountId

@Composable
internal fun MemberLine(account: AccountIdentity, status: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(account)
        Column(Modifier.weight(1f)) { Text(account.username); Text(status, style = MaterialTheme.typography.bodySmall) }
        trailing()
    }
}
