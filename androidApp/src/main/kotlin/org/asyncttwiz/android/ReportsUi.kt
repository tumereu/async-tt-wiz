package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal const val REPORTS_PROMPT_TEXT = "When the app crashes, runs slowly, stutters or loses its connection, it can send a short " +
    "report so the problem gets fixed. Reports include your device model and what the app was doing, but no passwords " +
    "or email addresses. You can change this any time in Settings."

/** LOB-104: the one-time question. Closing the dialog answers "No thanks". */
@Composable
internal fun ReportsPromptDialog(answer: (Boolean) -> Unit) {
    ScreenDialog({ answer(false) }, Modifier.testTag("reports-prompt"), title = "Send error reports?", actions = {
        DialogCancelButton("No thanks", { answer(false) }, Modifier.testTag("reports-prompt-no"))
        DialogMainButton("Send reports", { answer(true) }, Modifier.testTag("reports-prompt-yes"))
    }) {
        Text(REPORTS_PROMPT_TEXT)
    }
}

/** LOB-104: Settings → About & account. Test builds always report, so the switch shows on and can't change. */
@Composable
internal fun ReportsSetting(alwaysOn: Boolean = Diagnostics.alwaysOn) {
    val context = LocalContext.current
    val store = remember(context) { ReportChoiceStore(context) }
    var choice by remember(store) { mutableStateOf(store.choice) }
    val on = Diagnostics.enabledFor(alwaysOn, choice)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reports-setting")
        .toggleable(on, enabled = !alwaysOn, role = Role.Switch, onValueChange = { value ->
            Diagnostics.choose(context, value)
            choice = store.choice
        }),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel("Send error reports",
            if (alwaysOn) "Always on in test builds"
            else "Crashes, slow actions, stutters and lost connections. No passwords or email addresses.",
            Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = on, onCheckedChange = null, enabled = !alwaysOn)
    }
}
