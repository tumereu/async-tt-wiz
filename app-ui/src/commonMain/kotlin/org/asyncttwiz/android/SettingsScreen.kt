package org.asyncttwiz.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.DeckVisibility
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.AppPlatform
import androidx.compose.ui.text.style.TextAlign

fun appVersionText(versionName: String = AppBuild.VERSION_NAME, versionCode: Int = AppBuild.VERSION_CODE,
    buildType: String = AppBuild.BUILD_TYPE, commit: String = AppBuild.GIT_COMMIT): String =
    "Version $versionName ($versionCode) $buildType · commit $commit"

@Composable
fun SettingsContent(catalog: CatalogViewModel?, accountId: String, logout: () -> Unit, auth: AuthController?,
    accountSettings: AccountSettingsSource? = rememberAccountSettingsSource(accountId, auth),
    versionText: String = appVersionText()) {
    val preferences = rememberHistoryPlaybackPreferences(accountId)
    val scope = rememberCoroutineScope()
    val account = remember(accountSettings) { accountSettings?.let { AccountSettingsState(it, scope) } }
    LaunchedEffect(account) { account?.load() }
    Box(Modifier.fillMaxSize()) {
    // WEB-043 (D10): the desktop layout's Settings, its groups in two columns
    if (desktopLayout()) DesktopSettings(catalog, accountId, logout, auth, account, preferences, versionText)
    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // Wide landscape screens keep sliders and rows at a readable width.
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            if (auth?.state?.account != null) {
                SettingsSection("Profile") { ProfileSettings(auth, catalog) }
                HorizontalDivider()
            }
            if (account != null) {
                SettingsSection("Games") {
                    DeckVisibilitySetting(account)
                    MoveSoundSetting(account)
                }
                HorizontalDivider()
            }
            // WEB-050, WEB-054: this browser's notifications and install (in a browser only)
            LocalWebNotifications.current?.let { web ->
                SettingsSection("This browser") { BrowserSettings(web) }
                HorizontalDivider()
            }
            SettingsSection("Table") {
                // WEB-014: the browser's Layout (none on Android)
                LayoutSetting()
                CardMoveSpeedSetting(accountId)
                EventTextSetting(accountId)
                EdgeToEdgeSetting()
                CardSettings()
            }
            HorizontalDivider()
            // WEB-035: the desktop layout's hotkeys, in a browser only (none on Android)
            if (account != null && (org.asyncttwiz.appui.desktopLayoutOffered || LocalAppLayout.current == AppLayout.Desktop)) {
                SettingsSection("Keyboard") { KeyboardShortcutsSetting(account, LocalKeyBindings.current) }
                HorizontalDivider()
            }
            SettingsSection("Timeline playback") { PlaybackSettings(preferences, accountId, heading = false, moveSpeed = false, plays = true) }
            HorizontalDivider()
            SettingsSection("Automatum") { AutomatumPauseSettings(accountId, account) }
            HorizontalDivider()
            if (catalog != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) { CatalogControls(catalog.state, catalog::retry) }
                HorizontalDivider()
            }
            SettingsSection("About & account") {
                WhatsNewEntry()
                ProblemReportEntry { auth?.sessionToken() }
                ReportsSetting()
                SelectionContainer { Text(versionText, Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("app-version"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                SettingsActionRow("Log out", "logout", logout, Modifier.testTag("logout"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    val accountError = account?.error
    if (accountError != null) FloatingTransientError(accountError, account::dismissError, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * WEB-043 (wireframe D10): Settings on the desktop layout, the same groups as the phone's as cards in two columns: Profile,
 * Games, This browser, Table, Timeline playback and Automatum on the left; About & account, Card records and Keyboard on the right (one
 * column where two don't fit).
 */
@Composable
private fun DesktopSettings(catalog: CatalogViewModel?, accountId: String, logout: () -> Unit, auth: AuthController?,
    account: AccountSettingsState?, preferences: HistoryPlaybackPreferences, versionText: String) {
    val left: @Composable ColumnScope.() -> Unit = {
        if (auth?.state?.account != null) SettingsGroup("Profile") { ProfileSettings(auth, catalog) }
        if (account != null) SettingsGroup("Games") {
            DeckVisibilitySetting(account)
            MoveSoundSetting(account)
        }
        LocalWebNotifications.current?.let { web -> SettingsGroup("This browser") { BrowserSettings(web) } }
        SettingsGroup("Table") {
            LayoutSetting()
            CardMoveSpeedSetting(accountId)
            EventTextSetting(accountId)
            EdgeToEdgeSetting()
            CardSettings()
        }
        SettingsGroup("Timeline playback") { PlaybackSettings(preferences, accountId, heading = false, moveSpeed = false, plays = true) }
        SettingsGroup("Automatum") { AutomatumPauseSettings(accountId, account) }
    }
    val right: @Composable ColumnScope.() -> Unit = {
        SettingsGroup("About & account") {
            WhatsNewEntry()
            ProblemReportEntry { auth?.sessionToken() }
            ReportsSetting()
            SelectionContainer { Text(versionText, Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("app-version"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SettingsActionRow("Log out", "logout", logout, Modifier.testTag("logout"), color = MaterialTheme.colorScheme.error)
        }
        if (catalog != null) SettingsGroup(null) { CatalogControls(catalog.state, catalog::retry) }
        if (account != null) SettingsGroup("Keyboard") { KeyboardShortcutsSetting(account, LocalKeyBindings.current) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("desktop-settings")) {
        val gap = 16.dp
        if (maxWidth >= 720.dp) Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap), content = left)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap), content = right)
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(gap)) { left(); right() }
    }
}

/** WEB-043: one Settings group as a card, its heading ([title], none for a group that heads itself) over its rows. */
@Composable
private fun SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, ScreenBorder)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            if (title != null) SettingsSectionHeader(title) else Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SettingsContent(catalog: CatalogViewModel?, accountId: String = "", logout: () -> Unit) =
    SettingsContent(catalog, accountId, logout, null)

/** Tap-to-type values end on the same gutter as switches. */
val SettingsValuePadding = PaddingValues(horizontal = 4.dp)

/** One consistent heading style for every Settings group. */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        SettingsSectionHeader(title)
        content()
    }
}

/** Full-width list row for an action: icon + label, at least 48dp tall. */
@Composable
fun SettingsActionRow(label: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, color: Color? = null) {
    val alpha = if (enabled) 1f else .38f
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(screenIcon(icon), null, Modifier.size(22.dp), tint = (color ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = alpha))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = (color ?: MaterialTheme.colorScheme.onSurface).copy(alpha = alpha))
    }
}

/** Label with an optional one-line explanation, the shared text style of Settings rows. */
@Composable
fun SettingsLabel(label: String, supporting: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** LOB-146, TRN-075 (owner, 2026-11-11): the "Your move" sound: Off or one of the app's own sounds, each with a play button
 * to listen to it; kept on the account. */
@Composable
fun MoveSoundSetting(state: AccountSettingsState) {
    val current = state.settings?.moveSound ?: org.asyncttwiz.protocol.MoveSound.DEFAULT
    Column(Modifier.fillMaxWidth().selectableGroup().testTag("move-sound")) {
        SettingsLabel("Your move sound", "Plays when the move passes to you while a game is open",
            Modifier.padding(top = 12.dp, bottom = 4.dp))
        for (sound in org.asyncttwiz.protocol.MoveSound.all) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("move-sound-${sound.id}")
                .selectable(sound == current, role = Role.RadioButton) { state.setMoveSound(sound) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(sound == current, onClick = null)
                SettingsLabel(sound.label, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                if (sound != org.asyncttwiz.protocol.MoveSound.Off)
                    IconButton({ MoveSounds.play(sound, "Settings: listening") }, Modifier.semantics { contentDescription = "Listen to ${sound.label}" }) {
                        Icon(androidx.compose.material.icons.Icons.Outlined.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                    }
            }
        }
    }
}

@Composable
fun DeckVisibilitySetting(state: AccountSettingsState) {
    val current = state.settings?.deckVisibility
    Column(Modifier.fillMaxWidth().selectableGroup().testTag("deck-visibility")) {
        SettingsLabel("Deck visibility", "Default for \"Show deck name & art\" in new games. Each game's setup can still change it.",
            Modifier.padding(vertical = 4.dp))
        for (option in DeckVisibility.entries) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("deck-visibility-${option.name}")
                .selectable(option == current, role = Role.RadioButton) { state.setDeckVisibility(option) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(option == current, onClick = null)
                SettingsLabel(option.label, option.explanation, Modifier.weight(1f).padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun WhatsNewEntry() {
    val platform = AppPlatform.current
    var open by remember { mutableStateOf(false) }
    SettingsActionRow("What's new", "news", { open = true }, Modifier.testTag("whats-new"))
    if (open) WhatsNewDialog(remember { ReleaseNotes.load(platform) }) { open = false }
}

@Composable
private fun ProblemReportEntry(token: () -> String?) {
    var open by remember { mutableStateOf(false) }
    SettingsActionRow("Report a problem", "report", { open = true }, Modifier.testTag("report-problem"))
    if (open) ProblemReportDialog(token) { open = false }
}

@Composable
fun ProblemReportDialog(token: () -> String?, dismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val reports = remember { ProblemReportSender(scope) }
    var description by remember { mutableStateOf("") }
    val state = reports.state
    ScreenDialog(dismiss, title = "Report a problem", actions = {
        if (state is ProblemReportState.Sent) DialogMainButton("Close", dismiss, icon = "close")
        else {
            DialogCancelButton("Cancel", dismiss)
            DialogMainButton(if (state is ProblemReportState.Failed) "Try again" else "Send",
                { reports.send(description, token = token()) }, Modifier.testTag("send-report"),
                enabled = state != ProblemReportState.Sending, icon = if (state is ProblemReportState.Failed) "refresh" else "send")
        }
    }) {
        Text("Describe what went wrong, if you like. The report includes the app's recent log, your last game's Automatum " +
            "log and your device model, but no passwords or email addresses.", style = MaterialTheme.typography.bodySmall)
        ScreenTextField(description, { description = it.take(org.asyncttwiz.protocol.ProblemReport.MAX_DESCRIPTION) },
            Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("report-description"), singleLine = false,
            enabled = state == ProblemReportState.Idle || state is ProblemReportState.Failed, placeholder = "What happened?")
        ProblemReportStatus(state)
    }
}

@Composable
fun ProblemReportStatus(state: ProblemReportState) {
    when (state) {
        ProblemReportState.Idle -> Unit
        ProblemReportState.Sending -> Text("Sending report…", style = MaterialTheme.typography.bodySmall)
        is ProblemReportState.Sent -> SelectionContainer {
            Text("Report sent — code ${state.code}", Modifier.widthIn(max = 360.dp), textAlign = TextAlign.Center)
        }
        is ProblemReportState.Failed -> Text(state.message, Modifier.widthIn(max = 360.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
