package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The most digits a number's area holds at one width (STK-023, STK-030: owner gallery review, 2026-11-06). */
private const val FIXED_DIGITS = 4

/**
 * − value + of a choice (an X, "choose a number", a share of a division; I-D48: icon buttons, which say "One less" / "One
 * more" on hold), STK-023, STK-030 (owner gallery review, 2026-11-06): the value's area keeps one width for up to 4 digits,
 * so − and + never move; a tap on it opens the numeric input in its place ([NumberEntry]) to write any value from [min] to
 * [max] (null: no most). [set] takes the new value.
 */
@Composable
internal fun Stepper(value: Int, min: Int, max: Int?, set: (Int) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    if (typing) { NumberEntry(value, min, max, cancel = { typing = false }) { typing = false; set(it) }; return }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val ring = Modifier.size(36.dp).border(1.dp, AutomatumViolet.copy(alpha = .5f), CircleShape)
        TipIconButton(Icons.Outlined.Remove, "One less", ring, enabled = value > min) { set(value - 1) }
        val digits = value.toString().length
        Box(Modifier.padding(horizontal = 6.dp).widthIn(min = 56.dp).height(36.dp)
            .background(Color(0xfff6f3fb), RoundedCornerShape(8.dp))
            .border(1.dp, AutomatumViolet.copy(alpha = .35f), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "Type a number") { typing = true }
            .semantics { contentDescription = "Value $value" }
            .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(value.toString(), fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center,
                fontSize = if (digits <= FIXED_DIGITS) 16.sp else 14.sp, color = Color(0xff26343a))
        }
        TipIconButton(Icons.Outlined.Add, "One more", ring, enabled = max == null || value < max) { set(value + 1) }
    }
}

/** Why [text] can't be a value from [min] to [max] (null [max]: no most), or null when it can. */
internal fun numberProblem(text: String, min: Int, max: Int?): String? {
    val n = text.trim().toIntOrNull() ?: return if (text.isBlank()) "Write a number." else "Only whole numbers."
    return when {
        n < min -> "At least $min."
        max != null && n > max -> "At most $max."
        else -> null
    }
}

/** The range [min] to [max] in words: "From 0 to 5", "0 or more". */
internal fun numberRange(min: Int, max: Int?): String = if (max == null) "$min or more" else "From $min to $max"

/**
 * STK-023, STK-030 (owner gallery review, 2026-11-06): the numeric input for any value of a number, in − value +'s place:
 * the value, selected, in a field with the number keyboard, a check that takes it and a cross that keeps the value as it
 * was; under it the range, or in red why what is written can't be taken (the check greyed).
 */
@Composable
internal fun NumberEntry(value: Int, min: Int, max: Int?, cancel: () -> Unit, set: (Int) -> Unit) {
    val start = value.toString()
    var text by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val problem = numberProblem(text.text, min, max)
    val shown = problem?.takeIf { text.text.isNotBlank() }
    val focus = remember { FocusRequester() }
    fun done() { if (problem == null) set(text.text.trim().toInt()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(text, { text = it.copy(text = it.text.filter { c -> c.isDigit() || c == '-' }.take(9)) },
                Modifier.width(120.dp).focusRequester(focus).semantics { contentDescription = "Number" },
                singleLine = true, isError = shown != null,
                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { done() }))
            val ring = Modifier.size(40.dp).border(1.dp, AutomatumViolet.copy(alpha = .5f), CircleShape)
            TipIconButton(Icons.Outlined.Check, "Use this number", ring, enabled = problem == null) { done() }
            TipIconButton(Icons.Outlined.Close, "Keep the number as it was", ring) { cancel() }
        }
        Text(shown ?: numberRange(min, max), fontSize = 11.sp,
            color = if (shown != null) MaterialTheme.colorScheme.error else Color(0xff4b5a5f))
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
