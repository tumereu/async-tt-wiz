package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.ObjectId
import org.asyncttwiz.protocol.PlayState

/**
 * Who looks at a choice and how it is framed (I-D47, I-D48): [viewer] for the cards' outlines and Inspect; [headed]: the
 * panel's own title already says what the choice is ("Scry 2 · Carrot Cake") with its instruction, so its section
 * shows neither again.
 */
internal data class ChoiceFrame(val viewer: String = "", val headed: Boolean = false)

internal val LocalChoiceFrame = compositionLocalOf { ChoiceFrame() }

/**
 * What a choice of cards ([Decision.ChooseObjects]) is, from the engine's sentence for it (I-D47, I-D48): a choice between
 * two places ([Shape.Places]: scry, surveil, piles, discarding, a mulligan's bottom), putting cards in order
 * ([Shape.Order]) or picking some ([Shape.Pick]).
 */
internal data class ObjectsShape(
    val shape: Shape,
    /** What the choice is: "Scry 2", "Surveil 1", "Discard 1", "Sacrifice". */
    val title: String,
    /** A short instruction. */
    val instruction: String,
    val first: String,
    val second: String? = null,
    /** The answer is the cards in the first area (piles) rather than those in the second. */
    val chosenFirst: Boolean = false,
    /** The order of the first or second area answers the engine's next question about them (CR 701.22, 701.25). */
    val orderFirst: Boolean = false,
    val orderSecond: Boolean = false,
) {
    enum class Shape { Places, Order, Pick }

    companion object {
        fun of(d: Decision.ChooseObjects): ObjectsShape {
            val text = plainText(d.text)
            val n = d.options.size
            val count = countText(d.min, d.max, n)
            return when {
                text == "Put on the bottom" -> ObjectsShape(Shape.Places, "Scry $n",
                    "Tap a card to move it between Top and Bottom; drag to order them (1 is nearest the top).",
                    "Top", "Bottom", orderFirst = true, orderSecond = true)
                text == "Put into your graveyard" -> ObjectsShape(Shape.Places, "Surveil $n",
                    "Tap a card to move it between Top and Graveyard; drag to order the top (1 on top).",
                    "Top", "Graveyard", orderFirst = true)
                text == "First pile" || text == "Face-up pile" -> {
                    val faceUp = text == "Face-up pile"
                    ObjectsShape(Shape.Places, "Separate into piles", "Tap a card to move it to the other pile.",
                        if (faceUp) "Face-up pile" else "Pile 1", if (faceUp) "Face-down pile" else "Pile 2", chosenFirst = true)
                }
                text.startsWith("Discard") -> ObjectsShape(Shape.Places,
                    if (text.startsWith("Discard down")) text else if (d.min == d.max) "Discard ${d.min}" else "Discard",
                    "Tap the cards to discard ($count).", "Hand", "Discard")
                text.startsWith("Put ") && text.endsWith("on the bottom of your library") -> ObjectsShape(Shape.Places, "Mulligan",
                    "Tap the cards to put on the bottom ($count).", "Keep", "Bottom")
                text.startsWith("Order") -> ObjectsShape(Shape.Order, "Order",
                    "Drag the cards into order, or tap them in order: 1 goes on top.",
                    if ("on the bottom" in text) "Bottom" else "Top")
                else -> ObjectsShape(Shape.Pick, pickTitle(text), "Tap ${pickCount(d.min, d.max, n)}, then OK.", "Cards")
            }
        }

        /** The engine's sentence without its rules references: "Discard down to 7 cards (CR 514.1)" → "Discard down to 7 cards". */
        fun plainText(text: String) = text.replace(Regex("""\s*\(CR [^)]*\)"""), "").trim()

        private fun pickTitle(text: String) = when {
            text.startsWith("Choose to ") -> text.removePrefix("Choose to ").replaceFirstChar { it.uppercase() }
            text.endsWith("?") -> text.removeSuffix("?")
            else -> text
        }.let { if (it.length > 40) "Choose cards" else it }

        private fun countText(min: Int, max: Int?, n: Int): String = when {
            max != null && min == max -> "exactly $min"
            min == 0 && (max == null || max >= n) -> "any number"
            min == 0 -> "up to $max"
            max == null || max >= n -> "at least $min"
            else -> "$min to $max"
        }

        private fun pickCount(min: Int, max: Int?, n: Int): String = when {
            max != null && min == max -> if (min == 1) "one card" else "$min cards"
            min == 0 && max == 1 -> "one card or none"
            else -> "${countText(min, max, n)} of the cards"
        }
    }
}

/** What an open decision is, in a few words, for a choice's title (I-D47): "Scry 2", "Choose a colour", "Divide 3". */
internal fun choiceWhat(decision: Decision): String = when (decision) {
    is Decision.Targets -> "Choose targets"
    is Decision.Modes -> when {
        decision.max == 1 -> "Choose one"
        decision.min == decision.max -> "Choose ${decision.min}"
        else -> "Choose ${decision.min}–${decision.max}"
    }
    is Decision.Announce -> "Choose ${decision.name}"
    is Decision.ManaPayment -> "Pay ${decision.cost}"
    is Decision.May -> if (decision.cost != null) "Optional cost" else "You may"
    is Decision.ChooseObjects -> ObjectsShape.of(decision).title
    // I-D52: the engine's own question says what it is about (the loop choice: Continue / Draw)
    is Decision.ChooseOption -> if (decision.text != null) "Loop" else "Choose one"
    is Decision.ChooseColor -> "Choose a colour"
    is Decision.ChooseSubtype -> if (decision.among == null) "Choose a creature type" else "Choose a type"
    is Decision.ChooseCardType -> "Choose a card type"
    is Decision.ChooseCardName -> "Name a card"
    is Decision.ChooseNumber -> "Choose a number"
    is Decision.ChoosePlayer -> "Choose a player"
    is Decision.Divide -> "Divide ${decision.amount}"
    is Decision.OrderTriggers -> "Order triggers"
    is Decision.ChooseReplacement -> "Which applies first"
    is Decision.LegendRule -> "Legend rule"
    is Decision.Manual -> "Skipped"
    is Decision.Random, is Decision.HostFill, is Decision.OtherView -> "Choice"
}

/** A short instruction for an open decision (I-D47); null when its own sentence says it all. */
internal fun choiceHint(decision: Decision): String? = when (decision) {
    is Decision.Modes -> if (decision.max == 1) "Tap a mode." else "Tap the modes, then OK."
    is Decision.Announce -> "Set the value, then OK."
    is Decision.ManaPayment -> "Tap the sources to pay with, then Pay."
    is Decision.ChooseObjects -> ObjectsShape.of(decision).instruction
    is Decision.ChooseOption -> decision.text ?: "Tap one."
    is Decision.ChooseColor, is Decision.ChoosePlayer, is Decision.ChooseCardType -> "Tap one."
    is Decision.ChooseSubtype -> if (decision.among == null) "Find a type and tap it." else "Tap one."
    is Decision.ChooseCardName -> "Write any card name, then OK."
    is Decision.ChooseNumber -> "Set the number, then OK."
    is Decision.Divide -> "Give each its share; all ${decision.amount} must be divided."
    is Decision.OrderTriggers -> "Tap them in the order they go on the Stack (the first goes on first and resolves last), then OK."
    is Decision.ChooseReplacement -> "Tap the one that applies first, then OK."
    is Decision.LegendRule -> "Tap the ${decision.name} to keep; the others go to the graveyard."
    is Decision.Manual -> "Automatum can't do this part yet: it is skipped."
    is Decision.Targets, is Decision.May, is Decision.Random, is Decision.HostFill, is Decision.OtherView -> null
}

/** The icon of a choice area by its label. */
internal fun areaIcon(label: String): ImageVector = when (label) {
    "Top" -> Icons.Outlined.VerticalAlignTop
    "Bottom" -> Icons.Outlined.VerticalAlignBottom
    "Graveyard", "Discard" -> Icons.Outlined.Inventory2
    "Hand", "Keep" -> HandCardsIcon
    "Face-down pile" -> Icons.Outlined.Flip
    else -> Icons.Outlined.Layers
}

/** The card of engine object [id] in a choice, as the viewer may see it. */
internal fun choiceCardOf(flow: FlowQuestions, play: PlayState, viewer: String, id: ObjectId, tapped: Boolean = false): ChoiceCardData =
    choiceCard(play, viewer, id.toString(), flow.atwCard(id), flow.shown(id), objectName(flow, play, id), tapped)

/**
 * Choosing cards ([Decision.ChooseObjects], I-D48): the cards drawn like battlefield cards. A choice between two places is
 * the two-area screen ([TwoAreaChoice]); its OK also answers the orders the engine asks next of the same cards
 * ([FlowQuestions.answerWith]). Putting cards in order is one area; picking taps cards to choose them.
 */
@Composable
internal fun ObjectsSection(cast: FlowQuestions, flow: FlowQuestions, play: PlayState, decision: Decision.ChooseObjects) {
    val shape = remember(decision) { ObjectsShape.of(decision) }
    val viewer = LocalChoiceFrame.current.viewer
    val cards = decision.options.associate { it.toString() to choiceCardOf(flow, play, viewer, it) }
    val ids = decision.options.associateBy { it.toString() }
    val inspector = remember(decision.id) { ChoiceInspector() }
    Section(shape.title, shape.instruction) {
        when (shape.shape) {
            ObjectsShape.Shape.Places, ObjectsShape.Shape.Order -> {
                val state = remember(decision.id) { TwoAreas(decision.options.map { it.toString() }) }
                val places = shape.shape == ObjectsShape.Shape.Places
                TwoAreaChoice(state, cards, ChoiceArea(shape.first, areaIcon(shape.first), ordered = !places || shape.orderFirst),
                    shape.second?.takeIf { places }?.let { ChoiceArea(it, areaIcon(it), ordered = shape.orderSecond) }, inspector)
                val chosen = (if (!places || shape.chosenFirst) state.first else state.second).map(ids::getValue)
                val fits = chosen.size >= decision.min && (decision.max == null || chosen.size <= decision.max!!)
                DockButtons {
                    ChoiceButton("OK", Icons.Outlined.Check, enabled = fits, primary = true) {
                        val then = listOfNotNull(state.first.takeIf { places && shape.orderFirst && it.size > 1 },
                            state.second.takeIf { places && shape.orderSecond && it.size > 1 })
                            .map { area -> Answer.Objects(area.map(ids::getValue)) }
                        cast.answerWith(Answer.Objects(chosen), then)
                    }
                }
            }
            ObjectsShape.Shape.Pick -> {
                var chosen by remember(decision.id) { mutableStateOf(listOf<String>()) }
                CardPicks(decision.options.map { cards.getValue(it.toString()) }, chosen, inspector) { key ->
                    chosen = when {
                        key in chosen -> chosen - key
                        decision.max == 1 -> listOf(key)
                        else -> chosen + key
                    }
                }
                DockButtons {
                    ChoiceButton("OK", Icons.Outlined.Check, enabled = chosen.size >= decision.min &&
                        (decision.max == null || chosen.size <= decision.max!!), primary = true) {
                        cast.answer(Answer.Objects(chosen.map(ids::getValue)))
                    }
                }
            }
        }
    }
    ChoiceInspection(inspector, play, viewer)
}

/** The legend rule (CR 704.5j) as cards: tap the one to keep, then Keep. */
@Composable
internal fun LegendRuleSection(cast: FlowQuestions, flow: FlowQuestions, play: PlayState, decision: Decision.LegendRule) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    var kept by remember(decision.id) { mutableStateOf<String?>(null) }
    val ids = decision.among.associateBy { it.toString() }
    Section(choiceWhat(decision), choiceHint(decision)) {
        CardPicks(decision.among.map { choiceCardOf(flow, play, viewer, it) }, listOfNotNull(kept), inspector) { kept = it }
        DockButtons {
            ChoiceButton("Keep", Icons.Outlined.Check, enabled = kept != null, primary = true) {
                kept?.let { cast.answer(Answer.Keep(ids.getValue(it))) }
            }
        }
    }
    ChoiceInspection(inspector, play, viewer)
}

/** Cards to pick from (I-D48): a tap chooses or unchooses one ([toggle]), the chosen ones ringed; holding inspects. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardPicks(cards: List<ChoiceCardData>, chosen: List<String>, inspector: ChoiceInspector, toggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (card in cards) ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = { toggle(card.key) },
            hold = { inspector.hold(card, it) }), selected = card.key in chosen,
            badge = chosen.indexOf(card.key).takeIf { it >= 0 && chosen.size > 1 }?.let { "${it + 1}" })
    }
}

/** A row of a choice about a card (a trigger, a replacement effect, a share of a division): its card, small, then
 * [content]; holding the card inspects it. */
@Composable
internal fun CardRow(card: ChoiceCardData?, inspector: ChoiceInspector, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (card != null) ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }), small = true)
        content()
    }
}

/** A card of a choice with its own short [line] under it (a trigger's text, a replacement effect's), I-D48: a tap
 * ([tap]) chooses it, holding inspects it; [card] null: a card-shaped [name] (an ability whose source isn't a card). */
@Composable
internal fun LabelledChoiceCard(card: ChoiceCardData?, key: String, name: String, line: String, inspector: ChoiceInspector,
    selected: Boolean, badge: String? = null, tap: () -> Unit) {
    val face = (card ?: ChoiceCardData(key, null, name, null, shown = true)).copy(key = key)
    val (width, _) = choiceCardSize()
    // The card and its line are one target
    Column(Modifier.width(maxOf(width * 1.5f, 84.dp)).choiceCardGestures(key, tap = tap, hold = { inspector.hold(face, it) }),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChoiceCardFace(face, selected = selected, badge = badge)
        Text(line, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color(0xff4b5a5f))
    }
}

/** A colour's swatch for "Choose a colour". */
internal fun colorSwatch(symbol: Char): Color = when (symbol) {
    'W' -> Color(0xfff3edd2); 'U' -> Color(0xff2f6fb3); 'B' -> Color(0xff3a3335); 'R' -> Color(0xffc7412f)
    'G' -> Color(0xff2f7d4a); else -> Color(0xffa8a39a)
}

/** The hint under a section's title, when the panel's own title doesn't carry it ([ChoiceFrame.headed]). */
@Composable
internal fun ChoiceHintText(text: String) {
    Text(text, Modifier.semantics { contentDescription = text }, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f))
}
