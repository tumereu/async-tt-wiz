package org.asyncttwiz.android

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.ManaUse
import arcanum.engine.state.GameState
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.Faults

/**
 * STK-228, STK-049 (owner problem report 2026-11-22, prod 0.3.1): one mana of the payer's pool as a payment shows it: its
 * symbol in its colour ([symbol]: "R", "G", "C"), its name for the payment line and screen readers ([words]: "Red mana from your
 * pool") and what else it carries ([notes]: the source that made it, a restriction on what it may be spent on), said on hold.
 */
class PoolManaLook(val symbol: String, val words: String, val notes: List<String>) {
    /** What holding it says: its name and its notes. */
    val hold: String get() = (listOf(words) + notes).joinToString(". ")

    companion object {
        /** Mana [use] of [payer]'s pool in [state] (the payment's), [viewer] looking; [name]: a source's name. Owner rule
         * (2026-11-11): a pool mana whose colour can't be known is a fault, never a plain "Pool mana". */
        fun of(state: GameState?, payer: PlayerId?, viewer: String, use: ManaUse.Pool, name: (arcanum.engine.state.ObjectId) -> String): PoolManaLook {
            val unit: ManaUnit? = payer?.let { p -> state?.players?.firstOrNull { it.id == p }?.manaPool?.getOrNull(use.index) }
            val whose = if (payer == null || payer.value == viewer) "your pool" else "their pool"
            if (unit == null) {
                val words = Faults.placeholder("PoolMana", "pool mana of a payment not in the payer's pool", "Mana from $whose",
                    mapOf("index" to "${use.index}", "payer" to "$payer", "pool" to "${payer?.let { p -> state?.players?.firstOrNull { it.id == p }?.manaPool?.size }}"))
                return PoolManaLook("?", words, emptyList())
            }
            val symbol = unit.color?.symbol?.toString() ?: "C"
            val notes = buildList {
                if (unit.snow) add("Snow mana")
                unit.source?.let { add("Made by ${name(it)}") }
                if (unit.restriction != null) add("It can be spent only on some spells or abilities")
                if (unit.onlyCostsWithX) add("It can be spent only on a cost with {X}")
                if (unit.spellGets.isNotEmpty()) add("The spell it pays for gets more")
            }
            return PoolManaLook(symbol, "${colorWords(symbol)} mana from $whose", notes)
        }

        private fun colorWords(symbol: String) = when (symbol) {
            "W" -> "White"; "U" -> "Blue"; "B" -> "Black"; "R" -> "Red"; "G" -> "Green"; else -> "Colorless"
        }
    }
}

/** The look of pool mana [use] of the open payment ([payer]'s) as [flow] shows it (the payment's engine state). */
fun poolManaLook(flow: FlowQuestions, play: TableBoard, payer: PlayerId?, viewer: String, use: ManaUse.Pool): PoolManaLook =
    PoolManaLook.of(flow.engine, payer, viewer, use) { objectName(flow, play, it) }

/**
 * STK-228, STK-049 (owner problem report 2026-11-22): a pool mana in a payment, drawn as the sources beside it are, a small card:
 * a card's frame holding its mana symbol in its colour, "Pool" above it; chosen, a violet ring and a check in its corner, the frame
 * dimmed while it isn't. A tap chooses it or takes it out ([toggle]); holding it says what it is ([PoolManaLook.hold]); screen
 * readers hear "Red mana from your pool, chosen".
 */
@Composable
fun PoolManaTile(look: PoolManaLook, chosen: Boolean, toggle: () -> Unit, modifier: Modifier = Modifier) {
    val (width, height) = choiceCardSize()
    // Logging rule (owner problem report 2026-11-22): which pool mana a payment showed, and each tap on it
    androidx.compose.runtime.LaunchedEffect(look.hold, look.symbol) {
        AppLog.info("payment", "Pool mana shown in the payment as {${look.symbol}}: ${look.hold}")
    }
    HoldTip(look.hold, modifier) {
        Box(Modifier.size(width, height).clickable {
            AppLog.info("payment", "Pool mana {${look.symbol}} (${look.words}) tapped: ${if (chosen) "taken out" else "chosen"}")
            toggle()
        }
            .clearAndSetSemantics {
                contentDescription = look.words + if (chosen) ", chosen" else ""
                selected = chosen
                role = Role.Checkbox
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(width, height).cardFrame(false, if (chosen) OwnTableColor else Color(0xffb7bcbf))) {
                Column(Modifier.fillMaxSize().padding(horizontal = width * .07f, vertical = height * .04f),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val name = (width.value * .26f).sp
                    Text("Pool", Modifier.fillMaxWidth(), fontSize = name, lineHeight = name * 1.15f, fontWeight = FontWeight.Bold,
                        maxLines = 1, color = if (chosen) MenuInk else Color(0xff7a868b), textAlign = TextAlign.Center)
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width * .72f)) { CardSymbol(look.symbol) }
                    }
                }
            }
            if (chosen) {
                Box(Modifier.fillMaxSize().border(3.dp, AutomatumViolet, RoundedCornerShape(4.dp)))
                Surface(Modifier.align(Alignment.BottomStart).padding(1.dp), shape = CircleShape, color = AutomatumViolet, contentColor = Color.White) {
                    Icon(Icons.Outlined.Check, null, Modifier.padding(1.dp).size(10.dp))
                }
            }
        }
    }
}
