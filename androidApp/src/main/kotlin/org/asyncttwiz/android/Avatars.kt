package org.asyncttwiz.android

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.CatalogAvatar
import org.asyncttwiz.storage.CatalogPhase

internal fun avatarInitial(username: String): String = username.trim().let {
    if (it.isEmpty()) "?" else String(Character.toChars(it.codePointAt(0))).uppercase(java.util.Locale.ROOT)
}
internal fun avatarColor(accountId: String): Color = Color.hsv(Math.floorMod(accountId.hashCode(), 360).toFloat(), .5f, .65f)

@Composable
fun Avatar(account: AccountIdentity, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val initial = avatarInitial(account.username)
    val reference = account.avatar?.takeIf { it.face in 0..15 }?.let { ArtworkReference(it.scryfallId, it.face, "avatar-v1", "art_crop") }
    Box(modifier.size(size).clip(CircleShape).background(avatarColor(account.accountId))
        .semantics { contentDescription = "${account.username}'s avatar" }, contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            CardArtwork(reference, initial, 192, 192, Modifier.fillMaxSize(), crop = true)
        }
    }
}

internal class AvatarPromptMemory(context: Context) {
    private val preferences = context.getSharedPreferences("avatar-chooser", Context.MODE_PRIVATE)
    fun shouldShow(account: AccountIdentity) = account.avatar == null && !preferences.getBoolean(account.accountId, false)
    fun shown(accountId: String) { preferences.edit().putBoolean(accountId, true).apply() }
}

@Composable
internal fun CatalogDownloadStatus(catalog: CatalogViewModel) {
    val state = catalog.state
    if (state.installed == null || state.progress != null) Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.status ?: "Preparing card records…", style = MaterialTheme.typography.bodySmall)
            state.progress?.let { CatalogProgressBar(it, Modifier.fillMaxWidth()) }
            if (state.error) TextButton(onClick = { catalog.foreground(force = true) }) { Text("Retry download") }
        }
    }
}

@Composable
internal fun AvatarChooser(controller: AuthController, catalog: CatalogViewModel) {
    val account = controller.state.account ?: return
    BackHandler { controller.chooseAvatar(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose your avatar", style = MaterialTheme.typography.headlineSmall)
        AvatarPicker(account, catalog, { controller.saveAvatar(it) }, Modifier.weight(1f))
        OutlinedButton(onClick = { controller.chooseAvatar(false) }) { Text("Skip") }
    }
}

internal fun avatarCatalogStatus(state: CatalogUiState): String {
    val progress = state.progress
    val phase = when {
        state.error -> "Download failed"
        progress?.phase == CatalogPhase.INDEXING -> "Indexing…"
        progress?.phase == CatalogPhase.FINISHING -> "Finishing…"
        else -> "Downloading…"
    }
    val percentage = progress?.total?.takeIf { it > 0 && progress.phase != CatalogPhase.FINISHING }
        ?.let { " ${(progress.done.toDouble() / it * 100).toInt().coerceIn(0, 100)}%" }.orEmpty()
    return "Avatars need the card catalog. $phase$percentage"
}

@Composable
private fun AvatarPicker(account: AccountIdentity, catalog: CatalogViewModel?,
    choose: (org.asyncttwiz.protocol.Avatar) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    var query by remember { mutableStateOf("") }
    var reroll by remember { mutableIntStateOf(0) }
    var choices by remember { mutableStateOf(emptyList<CatalogAvatar>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    val ready = catalog?.state?.installed != null
    LaunchedEffect(ready, query, reroll) {
        if (!ready) return@LaunchedEffect
        loading = true
        error = false
        try {
            choices = withContext(Dispatchers.IO) { requireNotNull(catalog).repository.avatarChoices(query, previous = choices) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Exception) { error = true
        } finally { loading = false }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Avatar(account, size = if (compact) 48.dp else 80.dp)
            FilledTonalIconButton(onClick = { query = ""; reroll++ }, enabled = ready) {
                Icon(Icons.Outlined.Casino, "Reroll avatars")
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search by card name") }, singleLine = true)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!ready) {
                val state = catalog?.state ?: CatalogUiState()
                if (compact) {
                    if (state.error && catalog != null) FilledTonalIconButton(onClick = catalog::consent) {
                        EntryIcon("Retry catalog download", "refresh")
                    } else CircularProgressIndicator()
                } else Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(avatarCatalogStatus(state))
                        state.progress?.let { CatalogProgressBar(it, Modifier.fillMaxWidth()) }
                            ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.error && catalog != null) FilledTonalIconButton(onClick = catalog::consent) {
                            EntryIcon("Retry catalog download", "refresh")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
                    items(choices, key = { it.name }) { choice ->
                        Box(Modifier.selectable(account.avatar == choice.avatar, role = Role.Button, onClick = { choose(choice.avatar) })
                            .padding(4.dp), contentAlignment = Alignment.Center) {
                            Avatar(AccountIdentity(choice.avatar.scryfallId, choice.name, choice.avatar), size = if (compact) 48.dp else 72.dp,
                                modifier = if (account.avatar == choice.avatar) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                        }
                    }
                }
                if (loading && choices.isEmpty()) CircularProgressIndicator()
                if (error) Text("Could not load avatars", color = MaterialTheme.colorScheme.error)
                else if (!loading && choices.isEmpty()) Text("No avatars found")
            }
        }
    }
}

@Composable
internal fun ProfileSettings(controller: AuthController, catalog: CatalogViewModel?) {
    val state = controller.state
    val account = state.account ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Profile", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(account, size = 48.dp)
            Text(account.username, Modifier.weight(1f))
            FilledTonalIconButton(onClick = { controller.editProfile() }) { EntryIcon("Edit profile", "edit") }
        }
    }
    if (state.profileDialog) {
        var name by remember(account.accountId) { mutableStateOf(account.username) }
        var avatar by remember(account.accountId) { mutableStateOf(account.avatar) }
        Dialog(onDismissRequest = { controller.editProfile(false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 640.dp)),
                    shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Edit profile", style = MaterialTheme.typography.headlineSmall)
                        AvatarPicker(account.copy(username = name, avatar = avatar), catalog, { avatar = it }, Modifier.height(256.dp), compact = true)
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedIconButton(onClick = { controller.editProfile(false) }) { Icon(menuIcon("Close"), "Cancel profile") }
                            FilledIconButton(onClick = { controller.saveProfile(name, avatar) }, enabled = !state.profileBusy) { Icon(menuIcon("Set"), "Save profile") }
                        }
                    }
                }
            }
        }
    }
}
