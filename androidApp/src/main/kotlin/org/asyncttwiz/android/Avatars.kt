package org.asyncttwiz.android

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.CatalogAvatar

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
    val state = controller.state
    var query by remember { mutableStateOf("") }
    var reroll by remember { mutableIntStateOf(0) }
    var choices by remember { mutableStateOf(emptyList<CatalogAvatar>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    val ready = catalog.state.installed != null
    LaunchedEffect(ready, query, reroll) {
        if (!ready) return@LaunchedEffect
        loading = true
        error = false
        try {
            choices = withContext(Dispatchers.IO) { catalog.repository.avatarChoices(query, previous = choices) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Exception) { error = true
        } finally { loading = false }
    }
    BackHandler(!state.profileBusy) { controller.chooseAvatar(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose your avatar", style = MaterialTheme.typography.headlineSmall)
        CatalogDownloadStatus(catalog)
        if (ready) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search by card name") }, singleLine = true)
            TextButton(onClick = { query = ""; reroll++ }, enabled = !state.profileBusy) { Text("Reroll") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error) Text("Could not read card records. Reroll to retry.")
            LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(choices, key = { it.name }) { choice ->
                    Column(Modifier.clickable(enabled = !state.profileBusy) { controller.saveAvatar(choice.avatar) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(AccountIdentity(choice.avatar.scryfallId, choice.name, choice.avatar), size = 80.dp)
                        Text(choice.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        } else Spacer(Modifier.weight(1f))
        state.profileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.profileBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { controller.chooseAvatar(false) }, enabled = !state.profileBusy) { Text("Skip") }
            if (state.account?.avatar != null) TextButton(onClick = { controller.saveAvatar(null) }, enabled = !state.profileBusy) { Text("Clear avatar") }
        }
    }
}

@Composable
internal fun ProfileSettings(controller: AuthController) {
    val state = controller.state
    val account = state.account ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Profile", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(account, size = 48.dp)
            Text(account.username, Modifier.weight(1f))
        }
        TextButton(onClick = { controller.editUsername() }) { Text("Change username") }
        TextButton(onClick = { controller.chooseAvatar() }) { Text("Change avatar") }
    }
    if (state.usernameDialog) {
        var name by remember(account.accountId) { mutableStateOf(account.username) }
        AlertDialog(onDismissRequest = { controller.editUsername(false) }, title = { Text("Change username") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Username") }, enabled = !state.profileBusy, singleLine = true)
                state.profileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { TextButton(onClick = { controller.saveUsername(name) }, enabled = !state.profileBusy) { Text("Save") } },
            dismissButton = { TextButton(onClick = { controller.editUsername(false) }, enabled = !state.profileBusy) { Text("Cancel") } })
    }
}
