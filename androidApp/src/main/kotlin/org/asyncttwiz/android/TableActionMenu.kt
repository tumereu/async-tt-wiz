package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.screenWidthDp
import org.asyncttwiz.appui.screenHeightDp

/** The Table actions dialog (TRN-010): the timeline, the game's result and a problem report (I-D52: nothing that
 * changes the board by hand, no tokens, combat area, dice or coins). */
@Composable
fun TableActionMenu(play: TableBoard, controller: TableActionController, onDismiss: () -> Unit,
    pendingSuggestionId: String? = null, mayAccept: Boolean = false,
    perform: (TableActionIntent) -> Boolean = controller::perform,
    names: Map<String, String> = emptyMap(), onTimeline: (() -> Unit)? = null,
    reportToken: () -> String? = DebugCrashes::sessionToken,
    /** TRN-049: a waiting suggestion blocks every entry but Timeline (HIS-020). */
    blocked: Boolean = false,
    /** LOB-135: the game's settings (null: Game settings greyed, as in tests of the table alone). */
    settings: GameSettingsAccess? = null) {
    val scope = TableActionScope(play.gameId, play.playId)
    var ending by remember(scope) { mutableStateOf(false) }
    var outcome by remember(scope) { mutableStateOf<Pair<String, EndingTool>?>(null) }
    var conceding by remember(scope) { mutableStateOf(false) }
    var reporting by remember(scope) { mutableStateOf(false) }
    var setting by remember(scope) { mutableStateOf(false) }
    val landscape = screenWidthDp() > screenHeightDp()
    val enabled = controller.enabled(scope) && !blocked
    // TRN-064, CR 104.3a: also while it isn't the viewer's move
    val concedable = controller.concedeEnabled(scope) && !blocked
    val endingEntry = if (pendingSuggestionId == null) MenuControl("Suggest game end", enabled) { ending = true }
        else MenuControl("Accept result", enabled && mayAccept) {
            if (perform(TableActionIntent.Ending(scope, EndingTool.ACCEPT, pendingSuggestionId))) onDismiss()
        }
    // CR 104.3a (I-D52): conceding needs nobody's agreement, also while a suggestion waits
    val entries = listOf(MenuControl("Timeline", onTimeline != null) { onDismiss(); onTimeline?.invoke() }) +
        listOfNotNull(endingEntry.takeIf { pendingSuggestionId == null || mayAccept }) + MenuControl("Concede", concedable) { conceding = true } +
        // The same report as Settings' "Report a problem", without leaving the game (N7FU6Y)
        MenuControl("Send problem report", !blocked) { reporting = true } +
        // LOB-135: both players see the game's settings; only its host changes them
        MenuControl("Game settings", settings != null && !blocked) { setting = true }
    if (reporting) ProblemReportDialog(reportToken) { reporting = false; onDismiss() }
    if (setting && settings != null) GameSettingsPanel(settings.game, settings.accountId, settings.change) { setting = false; onDismiss() }
    if (!ending && !conceding && !reporting && !setting) TableMenuPanel(onDismiss, width = if (landscape) 776.dp else 400.dp, title = "Table actions") {
        for (row in entries.chunked(if (landscape) 3 else 2)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (entry in row) MenuButton(entry.name, Modifier.weight(1f), entry.enabled, entry.color, onClick = entry.click)
            repeat((if (landscape) 3 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (conceding) ScreenDialog({ conceding = false }, title = "Concede?", actions = {
        DialogCancelButton("Cancel", { conceding = false })
        DialogMainButton("Concede", { if (perform(TableActionIntent.Ending(scope, EndingTool.CONCEDE))) onDismiss() }, enabled = concedable,
            danger = true)
    }) { Text("You lose this game at once.") }
    if (ending && outcome == null) TableMenuPanel({ ending = false }, title = "Suggest game end") {
        for (choice in listOf("My win" to EndingTool.SUGGEST_WIN, "Draw game" to EndingTool.SUGGEST_DRAW))
            MenuButton(choice.first, Modifier.fillMaxWidth(), enabled) { outcome = choice }
    }
    outcome?.let { chosen ->
        var note by remember(chosen) { mutableStateOf("") }
        val confirm = { if (perform(TableActionIntent.Ending(scope, chosen.second, note = ResultNoteInput.clean(note)))) onDismiss() }
        ScreenDialog({ outcome = null }, title = "Suggest ${chosen.first.lowercase().removeSuffix(" game")}?", actions = {
            DialogCancelButton("Cancel", { outcome = null })
            DialogMainButton("Confirm", confirm, enabled = enabled)
        }) {
            OutlinedTextField(note, { note = ResultNoteInput.typed(it) }, Modifier.fillMaxWidth().testTag("resultNote"),
                label = { Text("Reason (optional)") }, placeholder = { Text("Infinite combo") }, singleLine = true,
                keyboardOptions = imeOptions(ImeAction.Done), keyboardActions = submitKeyboardActions(enabled, submit = confirm))
        }
    }
}
