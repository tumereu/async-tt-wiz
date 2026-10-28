package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal fun appVersionText(versionName: String = BuildConfig.VERSION_NAME, versionCode: Int = BuildConfig.VERSION_CODE,
    buildType: String = BuildConfig.BUILD_TYPE, commit: String = BuildConfig.GIT_COMMIT): String =
    "Version $versionName ($versionCode) $buildType · commit $commit"

@Composable
internal fun SettingsContent(catalog: CatalogViewModel?, accountId: String, logout: () -> Unit, auth: AuthController?) {
    val preferences = rememberHistoryPlaybackPreferences(accountId)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (auth != null) ProfileSettings(auth, catalog)
        PlaybackSettings(preferences, accountId)
        CardSettings()
        WhatsNewEntry()
        ProblemReportEntry { auth?.sessionToken() }
        TextButton(onClick = logout, modifier = Modifier.heightIn(min = 48.dp).testTag("logout"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            EntryIcon(null, "logout"); Spacer(Modifier.width(8.dp)); Text("Log out")
        }
        SelectionContainer { Text(appVersionText(), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("app-version")) }
        // Catalog growth cannot displace preferences, account actions or version information.
        if (catalog != null) CatalogControls(catalog)
    }
    if (catalog != null) FloatingTransientError(catalog.state.status?.takeIf { catalog.state.error },
        catalog::dismissError, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun SettingsContent(catalog: CatalogViewModel?, accountId: String = "", logout: () -> Unit) =
    SettingsContent(catalog, accountId, logout, null)

@Composable
private fun WhatsNewEntry() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("whats-new")) { Text("What's new") }
    if (open) WhatsNewDialog(remember { ReleaseNotes.load(context) }) { open = false }
}

@Composable
private fun ProblemReportEntry(token: () -> String?) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("report-problem")) { Text("Report a problem") }
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
