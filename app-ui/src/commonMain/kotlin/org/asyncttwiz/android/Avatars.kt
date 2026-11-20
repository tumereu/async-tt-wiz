package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.PodSummary
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.CatalogAvatar
import org.asyncttwiz.storage.CatalogPhase
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.BackHandler
import org.asyncttwiz.appui.firstCodePoint
import org.asyncttwiz.appui.ioDispatcher

fun avatarInitial(username: String): String = username.trim().let {
    if (it.isEmpty()) "?" else firstCodePoint(it).uppercase()
}
fun avatarColor(accountId: String): Color = Color.hsv(accountId.hashCode().mod(360).toFloat(), .5f, .65f)

@Composable
fun Avatar(account: AccountIdentity, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val initial = avatarInitial(account.username)
    val reference = account.avatar?.takeIf { it.face in 0..15 }?.let { ArtworkReference(it.scryfallId, it.face, "art_crop") }
    Box(modifier.size(size).clip(CircleShape).background(avatarColor(account.accountId))
        .semantics { contentDescription = "${account.username}'s avatar" }, contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            CardArtwork(reference, initial, 192, 192, Modifier.fillMaxSize(), crop = true)
        }
    }
}

/** Who a pod row shows: up to two other members plus how many more; only yourself when nobody else is there. */
data class AvatarStackContent(val shown: List<AccountIdentity>, val more: Int) {
    /** One 32dp avatar, each further avatar adds 20dp, a "+N" badge ends at 60dp. */
    val width: Dp get() = if (more > 0) 60.dp else 32.dp + 20.dp * (shown.size - 1).coerceAtLeast(0)
}

fun podNavigationAvatars(members: List<AccountIdentity>, selfId: String?): AvatarStackContent {
    val others = members.filter { it.accountId != selfId }
    if (others.isEmpty()) return AvatarStackContent(members.take(1), 0)
    return AvatarStackContent(others.take(2), (others.size - 2).coerceAtLeast(0))
}

/** One stack width for every row of a pod list (DRF-012, LOB-017, LOB-018), so the names line up. */
fun podStackWidth(pods: List<PodSummary>, selfId: String?): Dp =
    pods.maxOfOrNull { podNavigationAvatars(it.members, selfId).width } ?: 32.dp

/** [width] is shared by every row so names line up; later circles overlap earlier ones by 12dp with a [ring] edge. */
@Composable
fun AvatarStack(content: AvatarStackContent, ring: Color, modifier: Modifier = Modifier, width: Dp = content.width) {
    val step = 20.dp
    Box(modifier.width(width).height(32.dp), contentAlignment = Alignment.CenterStart) {
        content.shown.forEachIndexed { index, account ->
            Avatar(account, Modifier.offset(x = step * index)
                .then(if (index > 0) Modifier.border(2.dp, ring, CircleShape) else Modifier))
        }
        if (content.more > 0) {
            Box(Modifier.offset(x = 36.dp).size(24.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant).border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center) {
                Text("+${content.more}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

class AvatarPromptMemory(platform: AppPlatform) {
    private val preferences = platform.preferences("avatar-chooser")
    fun shouldShow(account: AccountIdentity) = account.avatar == null && !preferences.getBoolean(account.accountId, false)
    fun shown(accountId: String) { preferences.edit().putBoolean(accountId, true).apply() }
}

/** LOB-091: before sign-in, how far the card records' installation is (sign-in works meanwhile). */
@Composable
fun CatalogDownloadStatus(catalog: CatalogViewModel, modifier: Modifier = Modifier) {
    val state = catalog.state
    if (!state.ready) Surface(modifier.testTag("catalog-download-status"), color = MaterialTheme.colorScheme.surfaceVariant) {
        CompositionLocalProvider(LocalCardRecords provides state.records, LocalCardRecordsRetry provides catalog::retry) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardRecordsNote(Modifier.fillMaxWidth())
                if (!state.failed) LinearProgressIndicator(progress = { (state.progress?.percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun AvatarChooser(controller: AuthController, catalog: CatalogViewModel) {
    val account = controller.state.account ?: return
    BackHandler { controller.chooseAvatar(false) }
    // LOB-149: on the desktop the chooser keeps a readable width in the middle of the window
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.fillMaxHeight().widthIn(max = if (desktopLayout()) 960.dp else Dp.Unspecified).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose your avatar", style = MaterialTheme.typography.headlineSmall)
        AvatarPicker(account, catalog, { controller.saveAvatar(it) }, Modifier.weight(1f))
        OutlinedButton(onClick = { controller.chooseAvatar(false) }) { ButtonLabel("chevron", "Skip") }
    }
    }
}

@Composable
private fun AvatarPicker(account: AccountIdentity, catalog: CatalogViewModel?,
    choose: (org.asyncttwiz.protocol.Avatar) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    var query by remember { mutableStateOf("") }
    var reroll by remember { mutableIntStateOf(0) }
    var choices by remember { mutableStateOf(emptyList<CatalogAvatar>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    val ready = catalog?.state?.ready == true
    LaunchedEffect(ready, query, reroll) {
        if (!ready) return@LaunchedEffect
        loading = true
        error = false
        try {
            choices = withContext(ioDispatcher) { requireNotNull(catalog).repository.avatarChoices(query, previous = choices) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Exception) { error = true
        } finally { loading = false }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Avatar(account, size = if (compact) 48.dp else 80.dp)
            HoldTip("Reroll avatars") {
                FilledTonalIconButton(onClick = { query = ""; reroll++ }, enabled = ready) {
                    Icon(Icons.Outlined.Casino, "Reroll avatars")
                }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("avatar-search"), label = { Text("Search by card name") }, singleLine = true,
            keyboardOptions = imeOptions(ImeAction.Search), keyboardActions = hideKeyboardActions())
        if (!ready) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Avatars are card pictures: the card records' placeholder until they are installed (LOB-096)
            val state = catalog?.state ?: CatalogUiState()
            CompositionLocalProvider(LocalCardRecords provides state.records, LocalCardRecordsRetry provides catalog?.let { it::retry }) {
                CardRecordsPlaceholder(Modifier.fillMaxWidth())
            }
        } else AvatarChoices(choices, account.avatar, query, loading, error, compact, choose, Modifier.weight(1f).fillMaxWidth())
    }
}

/** LOB-149: what the avatar choices are: six random legendary cards, or the cards matching the search. */
fun avatarChoicesTitle(query: String, count: Int): String =
    if (query.isBlank()) "Random legendary creatures and planeswalkers" else "Cards matching “${query.trim()}”: $count"

/**
 * LOB-011, LOB-149 (owner, 2026-11-14): the avatar choices, each its card's art in a circle with the card's name under it, in a
 * framed area under a line saying what they are; the area scrolls inside its frame, and a shade at its top or bottom edge
 * shows there is more that way. On the desktop the choices are larger and as many to a row as fit.
 */
@Composable
fun AvatarChoices(choices: List<CatalogAvatar>, selected: org.asyncttwiz.protocol.Avatar?, query: String, loading: Boolean,
    error: Boolean, compact: Boolean, choose: (org.asyncttwiz.protocol.Avatar) -> Unit, modifier: Modifier = Modifier) {
    val desktop = desktopLayout()
    val size = if (desktop) 96.dp else if (compact) 48.dp else 72.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(avatarChoicesTitle(query, choices.size), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val grid = rememberLazyGridState()
        val shape = RoundedCornerShape(12.dp)
        Box(Modifier.weight(1f).fillMaxWidth().clip(shape).border(BorderStroke(1.dp, ScreenBorder), shape)
            .background(ScreenBackground).testTag("avatar-choices"), contentAlignment = Alignment.Center) {
            LazyVerticalGrid(if (desktop) GridCells.Adaptive(120.dp) else GridCells.Fixed(3), Modifier.fillMaxSize(), grid,
                contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                items(choices, key = { it.name }) { choice ->
                    val chosen = selected == choice.avatar
                    Column(Modifier.clip(RoundedCornerShape(8.dp)).selectable(chosen, role = Role.Button, onClick = { choose(choice.avatar) })
                        .testTag("avatar-choice").padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Avatar(AccountIdentity(choice.avatar.scryfallId, choice.name, choice.avatar), size = size,
                            modifier = if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                        Text(choice.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // The edges that hide more choices are shaded, so the area reads as scrollable
            val shade = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
            if (grid.canScrollBackward) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(16.dp)
                .background(Brush.verticalGradient(listOf(shade, Color.Transparent))).testTag("avatar-choices-more-above"))
            if (grid.canScrollForward) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, shade))).testTag("avatar-choices-more-below"))
            if (loading && choices.isEmpty()) CircularProgressIndicator()
            if (error) Text("Could not load avatars", color = MaterialTheme.colorScheme.error)
            else if (!loading && choices.isEmpty()) Text("No avatars found")
        }
    }
}

@Composable
fun ProfileSettings(controller: AuthController, catalog: CatalogViewModel?) {
    val state = controller.state
    val account = state.account ?: return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(account, size = 48.dp)
        Text(account.username, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        HoldTip("Edit profile") { FilledTonalIconButton(onClick = { controller.editProfile() }) { EntryIcon("Edit profile", "edit") } }
    }
    if (state.profileDialog) {
        val nameField = rememberDialogField(account.username, account.accountId, focusOnOpen = false)
        val name = nameField.text
        var avatar by remember(account.accountId) { mutableStateOf(account.avatar) }
        // LOB-149: the desktop's dialog is wider and taller, its choices area with it
        val desktop = desktopLayout()
        Dialog(onDismissRequest = { controller.editProfile(false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                val room = maxHeight
                Surface(Modifier.widthIn(max = if (desktop) 960.dp else 560.dp).fillMaxWidth()
                    .heightIn(max = minOf(maxHeight, if (desktop) 900.dp else 640.dp)),
                    shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Edit profile", style = MaterialTheme.typography.headlineSmall)
                        AvatarPicker(account.copy(username = name, avatar = avatar), catalog, { avatar = it },
                            Modifier.height(if (desktop) (room - 300.dp).coerceIn(256.dp, 560.dp) else 296.dp), compact = !desktop)
                        OutlinedTextField(nameField.value, { nameField.value = it }, Modifier.fillMaxWidth().dialogField(nameField),
                            label = { Text("Username") }, singleLine = true, keyboardOptions = imeOptions(ImeAction.Done),
                            keyboardActions = submitKeyboardActions(!state.profileBusy) { controller.saveProfile(name, avatar) })
                        DialogActionsRow {
                            DialogCancelButton("Cancel", { controller.editProfile(false) })
                            DialogMainButton("Save", { controller.saveProfile(name, avatar) }, enabled = !state.profileBusy, icon = "save")
                        }
                    }
                }
            }
        }
    }
}
