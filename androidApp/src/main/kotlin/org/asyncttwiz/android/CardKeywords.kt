package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/** Keyword toggles edit an ordinary note as "Flying, Trample" (list order). Any other token makes
 * the note unparseable, so the toggles never rewrite or drop free-form text. */
internal object CardKeywords {
    val all = listOf("Flying", "First strike", "Double strike", "Deathtouch", "Lifelink", "Trample", "Haste", "Vigilance",
        "Reach", "Menace", "Hexproof", "Indestructible", "Defender", "Ward", "Can't block", "Goaded")

    private fun tokens(note: String) = note.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private fun keyword(token: String) = token.replace('’', '\'').replace(Regex("\\s+"), " ")
        .let { normalized -> all.firstOrNull { it.equals(normalized, ignoreCase = true) } }

    /** Null when any comma-separated token is not a known keyword. */
    fun parse(note: String): Set<String>? = tokens(note).map { keyword(it) ?: return null }.toSet()

    /** Known keywords only; other tokens are ignored. */
    fun granted(note: String): Set<String> = tokens(note).mapNotNull(::keyword).toSet()

    fun format(keywords: Set<String>): String = all.filter { it in keywords }.joinToString(", ")

    fun note(card: PlayCard, eot: Boolean) = if (eot) card.eotNote else card.note

    /** On only when every target has it; null when there is no target or any note is unparseable. */
    fun state(notes: List<String>): Set<String>? {
        if (notes.isEmpty()) return null
        val parsed = notes.map { parse(it) ?: return null }
        return parsed.reduce { a, b -> a intersect b }
    }

    /** Turning a partly present keyword on adds it where missing; turning it off removes it everywhere. */
    fun toggle(cards: List<PlayCard>, keyword: String, eot: Boolean): List<PlayAction> {
        val on = state(cards.map { note(it, eot) }) ?: return emptyList()
        return cards.mapNotNull { card ->
            val current = parse(note(card, eot)) ?: return emptyList()
            val next = format(if (keyword in on) current - keyword else current + keyword)
            if (next == note(card, eot)) null
            else if (eot) PlayAction.SetEotNote(card.id, next) else PlayAction.SetNote(card.id, next)
        }
    }
}

/** Floating 4×4 toggle grid; each toggle is sent at once and the popup stays open. */
@Composable
internal fun KeywordToggles(state: PlayState, scope: ManualScope, eot: Boolean, enabled: Boolean,
    dismiss: () -> Unit, submitAll: (List<PlayAction>) -> Unit) {
    val cards = scope.keywordCards(state, eot)
    val on = CardKeywords.state(cards.map { CardKeywords.note(it, eot) })
    InlinePopupPanel(dismiss, if (eot) "EOT keywords" else "Keywords", width = (4 * 86 + 3 * 2).dp,
        key = "keywords/${scope.contextId}/$eot") {
        val color = LocalInlinePopupColor.current
        val tint = if (eot) Color(EotPowerToughnessColor) else color
        Column(verticalArrangement = Arrangement.spacedBy(InlinePopupStyle.cellGap.dp)) {
            for (row in CardKeywords.all.chunked(4)) Row(horizontalArrangement = Arrangement.spacedBy(InlinePopupStyle.cellGap.dp)) {
                for (keyword in row) {
                    val selected = on?.contains(keyword) == true
                    RingPress(MenuControl(keyword, enabled && on != null, selected = selected) {
                        submitAll(CardKeywords.toggle(scope.keywordCards(state, eot), keyword, eot))
                    }, Modifier.size(86.dp, 44.dp), if (selected) tint else color,
                        fill = if (selected) tint.copy(alpha = .22f) else Color.White) {
                        // One long word (Indestructible) must shrink rather than break mid-word.
                        Text(keyword, Modifier.padding(horizontal = 4.dp), fontSize = if (' ' !in keyword && keyword.length > 12) 10.sp else 12.sp,
                            lineHeight = 13.sp,
                            maxLines = 2, textAlign = TextAlign.Center,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = MenuInk.copy(alpha = if (enabled && on != null) 1f else .38f))
                    }
                }
            }
        }
    }
}
