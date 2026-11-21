package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import arcanum.api.RulesGlossary
import org.asyncttwiz.protocol.CardDefinition

/**
 * TBL-209 (owner 2026-11-19, playtest N8): a card's rules reminders: each keyword ability, keyword action, other rules word,
 * token kind (Treasure, Food, Monster Role, ...) and card state its printed text names but doesn't explain with reminder
 * text, from Automatum's own glossary ([RulesGlossary.reminders]); its names (Oracle and printed, each face and part) never
 * count. Owner 2026-11-21 (E6): what the shown printing prints: a face's [org.asyncttwiz.protocol.DefinitionFace.printedText]
 * where its printing leaves out or adds reminder text (a retro frame's "Disturb {1}{W}"), else its Oracle text.
 */
fun cardReminders(definition: CardDefinition): List<RulesGlossary.Entry> {
    val texts = definition.faces.flatMap { face -> listOf(face.printedText ?: face.rulesText) + face.parts.map { it.rulesText } }
        .filter { it.isNotBlank() }
    if (texts.isEmpty()) return emptyList()
    val names = listOf(definition.name) + definition.faces.flatMap { face -> listOfNotNull(face.name, face.printedName) + face.parts.map { it.name } }
    return RulesGlossary.reminders(texts.distinct().joinToString("\n"), names.distinct())
}

/** The test tag of one rules reminder chip (TBL-209). */
const val RULES_REMINDER_TAG = "rules-reminder"

/**
 * TBL-209, TBL-210: a card's rules reminders in Inspect, one chip each naming the word. [explained] (where nothing can tap
 * them: outside a game, an Inspect that can't be pinned, the desktop layout): each chip with its explanation under it, on a
 * dark panel. Otherwise the chips alone, and once [interactive] (pinned) a tap on one, or holding it, shows its explanation
 * in the dark rule popup (TBL-132) above it; a tap again or on another chip closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesReminders(entries: List<RulesGlossary.Entry>, explained: Boolean, interactive: Boolean, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    if (explained) {
        Column(modifier.semantics { contentDescription = "Rules reminders" }.clip(RoundedCornerShape(12.dp)).background(ReminderPanelColor)
            .padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReminderChip(entry)
                    CardText(entry.text, color = ReminderTextColor, style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp))
                }
            }
        }
        return
    }
    var open by remember(entries) { mutableStateOf<String?>(null) }
    FlowRow(modifier.semantics { contentDescription = "Rules reminders" }, horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { entry ->
            var pressed by remember(entry) { mutableStateOf(false) }
            Box {
                // WEB-058: a click target, so the mouse shows the hand over it
                ReminderChip(entry, if (!interactive) Modifier else Modifier.semantics {
                    onClick { open = if (open == entry.id) null else entry.id; true }
                }.hoverLook(HoverLook.Tint(Color.White, RoundedCornerShape(50))).pointerInput(entry) {
                    detectTapGestures(onPress = {
                        pressed = true
                        AppLog.info("table", "Rules reminder held: ${entry.name}")
                        tryAwaitRelease()
                        pressed = false
                    }, onTap = { open = if (open == entry.id) null else entry.id })
                })
                if (interactive && (pressed || open == entry.id)) {
                    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
                    Popup(popupPositionProvider = remember(gap) { HoldTooltipPosition(gap) }, properties = PopupProperties(focusable = false)) {
                        RuleExplanation(listOf(entry))
                    }
                }
            }
        }
    }
}

/** One reminder chip: the word's name on a dark pill with a faint outline. */
@Composable
private fun ReminderChip(entry: RulesGlossary.Entry, modifier: Modifier = Modifier) {
    Text(entry.name, modifier.testTag(RULES_REMINDER_TAG).semantics { contentDescription = "Rules reminder: ${entry.name}" }
        .clip(RoundedCornerShape(50)).background(ReminderChipColor).border(1.dp, ReminderChipOutline, RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
}

private val ReminderChipColor = Color(0xf2302838)
private val ReminderChipOutline = Color(0x66c9b8ff)
private val ReminderPanelColor = Color(0xf21d1f25)
private val ReminderTextColor = Color(0xffe2dcf5)
