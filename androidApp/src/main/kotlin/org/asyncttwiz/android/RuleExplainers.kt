package org.asyncttwiz.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.api.RulesGlossary

/**
 * TBL-132 (owner gallery review 2026-11-06): rule explainers. Holding something shown as a chip that names a keyword or a
 * keyword action (a cost chip's "Forage", a combat keyword badge's "flying") shows what it means in plain words, from the
 * Arcanum glossary ([RulesGlossary], written in its own words, never the rules text); [text] names the keywords and its
 * [content] is the chip; [lead], when given, is what holding the chip said before (a cost's text), shown above the
 * explanations. Text naming none shows [lead] alone, or nothing without one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleTip(text: String, modifier: Modifier = Modifier, lead: String? = null, content: @Composable () -> Unit) {
    val entries = remember(text) { RulesGlossary.named(text) }
    if (entries.isEmpty() && lead == null) { Box(modifier) { content() }; return }
    Box(modifier, propagateMinConstraints = true) {
        // Kept on the screen: above the chip, or under it when there is no room above
        val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
        TooltipBox(positionProvider = remember(gap) { HoldTooltipPosition(gap) },
            tooltip = { RuleExplanation(entries, lead) }, state = rememberTooltipState(), content = content)
    }
}

/** The explanations of [entries] as a dark card-like popup: [lead] first when given, then each keyword's name in bold and
 * what it means. */
@Composable
internal fun RuleExplanation(entries: List<RulesGlossary.Entry>, lead: String? = null, modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(max = 280.dp).semantics { if (entries.isNotEmpty()) contentDescription = "Rule explanation" },
        color = MenuInk, contentColor = Color.White, shadowElevation = 6.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lead?.let { CardText(it, color = Color.White, style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)) }
            entries.forEach { entry -> RuleExplanationText(entry) }
        }
    }
}

/** One keyword's name in bold over its explanation, in [ink]. */
@Composable
internal fun RuleExplanationText(entry: RulesGlossary.Entry, ink: Color = Color.White) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(entry.name, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
        CardText(entry.text, color = ink, style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp))
    }
}
