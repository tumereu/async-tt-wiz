package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File

/** The entry point replaces account/game UI with this screen while blocked. */
@Composable
fun AppUpdateScreen(updates: AppUpdateViewModel, install: (File) -> Unit) {
    val uris = LocalUriHandler.current
    LaunchedEffect(updates.installation) {
        updates.installation?.let { updates.consumeInstallation(); install(it) }
    }
    if (updates.downloading) {
        // A large download over mobile data must not be cut off by the screen turning off.
        val view = LocalView.current
        DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    }
    when {
        updates.required -> {
            BackHandler { }
            Surface(Modifier.fillMaxSize()) {
                // Scrolls when the failure help doesn't fit, e.g. on a landscape phone.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                        Text("Update required", style = MaterialTheme.typography.headlineMedium)
                        Text(updates.release?.versionName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Button(onClick = updates::update, enabled = !updates.downloading) { Text("Update") }
                        Column(Modifier.width(240.dp).heightIn(min = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (updates.downloading) {
                                LinearProgressIndicator(progress = { updates.progress }, modifier = Modifier.fillMaxWidth())
                                updates.release?.let { release ->
                                    val total = release.size / 1_000_000
                                    Text("${(updates.progress * total).toInt()} / $total MB", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        updates.problem?.let { problem ->
                            Text(problem.message, Modifier.widthIn(max = 360.dp), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            problem.detail?.let {
                                Text(it, Modifier.widthIn(max = 360.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (problem.browserFallback) {
                                listOfNotNull("Download from server" to updates.browserDownload,
                                    updates.githubDownload?.let { "Download from GitHub" to it }).forEach { (label, url) ->
                                    OutlinedButton(onClick = {
                                        runCatching { uris.openUri(url) }.onFailure { updates.browserFailed(it) }
                                    }, enabled = !updates.downloading) { Text(label) }
                                }
                                Text("Then open the downloaded file and install it. No need to uninstall this app first.",
                                    Modifier.widthIn(max = 360.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            }
                            ProblemReportAction(updates.reports, updates::sendReport)
                        }
                    }
                }
            }
        }
        updates.initialCheck -> Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        else -> Unit
    }
}

/** Sending never blocks the other update actions; the code stays readable and selectable. */
@Composable
internal fun ProblemReportAction(reports: ProblemReportSender, send: () -> Unit) {
    val state = reports.state
    if (state !is ProblemReportState.Sent)
        TextButton(onClick = send, enabled = state != ProblemReportState.Sending) { Text("Send problem report") }
    ProblemReportStatus(state)
}

@Composable
internal fun ProblemReportStatus(state: ProblemReportState) {
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
