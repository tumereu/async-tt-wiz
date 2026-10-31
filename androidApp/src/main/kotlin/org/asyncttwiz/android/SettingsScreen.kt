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
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.DeckVisibility

internal fun appVersionText(versionName: String = BuildConfig.VERSION_NAME, versionCode: Int = BuildConfig.VERSION_CODE,
    buildType: String = BuildConfig.BUILD_TYPE, commit: String = BuildConfig.GIT_COMMIT): String =
    "Version $versionName ($versionCode) $buildType · commit $commit"

@Composable
internal fun SettingsContent(catalog: CatalogViewModel?, accountId: String, logout: () -> Unit, auth: AuthController?,
    accountSettings: AccountSettingsSource? = rememberAccountSettingsSource(accountId, auth),
    versionText: String = appVersionText()) {
    val preferences = rememberHistoryPlaybackPreferences(accountId)
    val scope = rememberCoroutineScope()
    val account = remember(accountSettings) { accountSettings?.let { AccountSettingsState(it, scope) } }
    LaunchedEffect(account) { account?.load() }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // Wide landscape screens keep sliders and rows at a readable width.
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            if (auth?.state?.account != null) {
                SettingsSection("Profile") { ProfileSettings(auth, catalog) }
                HorizontalDivider()
            }
            if (account != null) {
                SettingsSection("Games") { DeckVisibilitySetting(account) }
                HorizontalDivider()
            }
            SettingsSection("Table") {
                CardMoveSpeedSetting(accountId)
                EventTextSetting(accountId)
                CardSettings()
            }
            HorizontalDivider()
            SettingsSection("Timeline playback") { PlaybackSettings(preferences, accountId, heading = false, moveSpeed = false) }
            HorizontalDivider()
            if (catalog != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) { CatalogControls(catalog) }
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
    else if (catalog != null) FloatingTransientError(catalog.state.status?.takeIf { catalog.state.error },
        catalog::dismissError, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun SettingsContent(catalog: CatalogViewModel?, accountId: String = "", logout: () -> Unit) =
    SettingsContent(catalog, accountId, logout, null)

/** Tap-to-type values end on the same gutter as switches. */
internal val SettingsValuePadding = PaddingValues(horizontal = 4.dp)

/** One consistent heading style for every Settings group. */
@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        SettingsSectionHeader(title)
        content()
    }
}

/** Full-width list row for an action: icon + label, at least 48dp tall. */
@Composable
internal fun SettingsActionRow(label: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier,
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
internal fun SettingsLabel(label: String, supporting: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DeckVisibilitySetting(state: AccountSettingsState) {
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    SettingsActionRow("What's new", "news", { open = true }, Modifier.testTag("whats-new"))
    if (open) WhatsNewDialog(remember { ReleaseNotes.load(context) }) { open = false }
}

@Composable
private fun ProblemReportEntry(token: () -> String?) {
    var open by remember { mutableStateOf(false) }
    SettingsActionRow("Report a problem", "report", { open = true }, Modifier.testTag("report-problem"))
    if (open) ProblemReportDialog(token) { open = false }
}

@Composable
internal fun ProblemReportDialog(token: () -> String?, dismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val reports = remember { ProblemReportSender(scope) }
    var description by remember { mutableStateOf("") }
    val state = reports.state
    ScreenDialog(dismiss, title = "Report a problem", actions = {
        if (state is ProblemReportState.Sent) DialogMainButton("Close", dismiss)
        else {
            DialogCancelButton("Cancel", dismiss)
            DialogMainButton(if (state is ProblemReportState.Failed) "Try again" else "Send",
                { reports.send(description, token = token()) }, Modifier.testTag("send-report"),
                enabled = state != ProblemReportState.Sending)
        }
    }) {
        Text("Describe what went wrong, if you like. The report includes the app's recent log and your device model, " +
            "but no passwords or email addresses.", style = MaterialTheme.typography.bodySmall)
        ScreenTextField(description, { description = it.take(org.asyncttwiz.protocol.ProblemReport.MAX_DESCRIPTION) },
            Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("report-description"), singleLine = false,
            enabled = state == ProblemReportState.Idle || state is ProblemReportState.Failed, placeholder = "What happened?")
        ProblemReportStatus(state)
    }
}
