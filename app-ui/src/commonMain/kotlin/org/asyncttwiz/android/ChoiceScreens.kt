package org.asyncttwiz.android

import org.asyncttwiz.protocol.Faults
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.ObjectId

/**
 * Who looks at a choice and how it is framed (I-D47, I-D48): [viewer] for the cards' outlines and Inspect; [headed]: the
 * panel's own title already says what the choice is ("Scry 2 · Carrot Cake") with its instruction, so its section
 * shows neither again.
 */
data class ChoiceFrame(val viewer: String = "", val headed: Boolean = false)

val LocalChoiceFrame = compositionLocalOf { ChoiceFrame() }

/**
 * What a choice of cards ([Decision.ChooseObjects]) is, from the engine's sentence for it (I-D47, I-D48): a choice between
 * two places ([Shape.Places]: scry, surveil, piles, discarding, a mulligan's bottom), putting cards in order
 * ([Shape.Order]) or picking some ([Shape.Pick]).
 */
data class ObjectsShape(
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
    /** A search (STK-056): the searched zones' other cards show in an unselectable "Other cards" section. */
    val search: Boolean = false,
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
                // STK-056: a search is titled by what is searched, up to the sentence's first comma ("Search your
                // library for a basic land card"), an activated ability's cost left out
                SEARCH.find(text) != null -> ObjectsShape(Shape.Pick, SEARCH.find(text)?.groupValues?.get(1)?.trim().orEmpty(),
                    "Tap ${pickCount(d.min, d.max, n)}, then OK.", "Cards", search = true)
                else -> ObjectsShape(Shape.Pick, pickTitle(text), "Tap ${d.total?.let(::reaching) ?: pickCount(d.min, d.max, n)}, then OK.", "Cards")
            }
        }

        /** A search instruction in the engine's sentence: "Search …" up to its first comma or full stop, at the start or
         * after an activated ability's cost ("{T}, Sacrifice this land: Search your library for …"). */
        private val SEARCH = Regex("""(?:^|:\s+)(Search\b[^,.;]*)""")

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

        /** A choice up to a total says the total: "at least 1" would read as if one card were enough (CR 701.59a, 702.122a). */
        private fun reaching(total: arcanum.api.Total): String? {
            val amount = (total.atLeast as? arcanum.api.Value.Fixed)?.value ?: return null
            val what = when (total.property) {
                arcanum.api.NumericProperty.ManaValue -> "mana value"
                // Crew and saddle count power by their own rule (CR 702.122a, 702.171a)
                arcanum.api.NumericProperty.Power, is arcanum.api.NumericProperty.PowerFor -> "power"
                arcanum.api.NumericProperty.Toughness -> "toughness"
                else -> return null
            }
            return "cards with total $what $amount or more"
        }

        private fun pickCount(min: Int, max: Int?, n: Int): String = when {
            max != null && min == max -> if (min == 1) "one card" else "$min cards"
            min == 0 && max == 1 -> "one card or none"
            else -> "${countText(min, max, n)} of the cards"
        }
    }
}

/** What an open decision is, in a few words, for a choice's title (I-D47): "Scry 2", "Choose a colour", "Divide 3". */
fun choiceWhat(decision: Decision): String = when (decision) {
    is Decision.Targets -> "Choose targets"
    is Decision.Modes -> when {
        decision.max == 1 -> "Choose one"
        decision.min == decision.max -> "Choose ${decision.min}"
        else -> "Choose ${decision.min}–${decision.max}"
    }
    is Decision.Announce -> "Choose ${decision.name}"
    is Decision.ManaPayment -> "Pay ${decision.cost}"
    is Decision.May -> if (decision.cost != null) "You may pay" else "You may"
    is Decision.ChooseObjects -> ObjectsShape.of(decision).title
    // I-D52: the engine's own question says what it is about (the loop choice: Continue / Draw)
    is Decision.ChooseOption -> if (decision.options == listOf(arcanum.engine.decision.LoopStop.CONTINUE, arcanum.engine.decision.LoopStop.DRAW)) "Loop"
        else "Choose one"
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
fun choiceHint(decision: Decision): String? = when (decision) {
    is Decision.Modes -> if (decision.max == 1) "Tap a mode." else "Tap the modes, then OK."
    is Decision.Announce -> "Set the value, then OK."
    is Decision.ManaPayment -> "Tap the sources to pay with, then Pay."
    is Decision.ChooseObjects -> ObjectsShape.of(decision).instruction
    is Decision.ChooseOption -> decision.text ?: "Tap one."
    is Decision.ChooseColor, is Decision.ChoosePlayer, is Decision.ChooseCardType -> "Tap one."
    is Decision.ChooseSubtype -> if (decision.among == null) "Find a type and tap it." else "Tap one."
    is Decision.ChooseCardName -> "Find the card and tap its name, then OK."
    is Decision.ChooseNumber -> "Set the number, then OK."
    is Decision.Divide -> "Give each its share; all ${decision.amount} must be divided."
    is Decision.OrderTriggers -> "Drag them by their handles into the order they resolve, the top one first, then OK."
    is Decision.ChooseReplacement -> "Drag them by their handles into the order they apply, the top one first, then OK."
    is Decision.LegendRule -> "Tap the ${decision.name} to keep; the others go to the graveyard."
    is Decision.Manual -> "Automatum can't do this part yet: it is skipped."
    is Decision.Targets, is Decision.May, is Decision.Random, is Decision.HostFill, is Decision.OtherView -> null
}

/**
 * The sentence of a spell's printed [text] an open [decision] asks about (STK-030, owner playtest 2026-11-06: every
 * question shows what it asks without holding): its sentences that name what is chosen ("target", the X announced, a
 * colour, a number, a player, …), else the whole text; null without text.
 */
fun askedSentence(text: String?, decision: Decision): String? {
    val plain = text?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(" ")?.takeIf { it.isNotEmpty() } ?: return null
    val words: List<String> = when (decision) {
        is Decision.Targets -> listOf("target")
        is Decision.Announce -> listOf(decision.name)
        is Decision.Divide -> listOf("divide", "distribute")
        is Decision.ChooseNumber -> listOf("number")
        is Decision.ChooseColor -> listOf("color", "colour")
        is Decision.ChooseSubtype -> listOf("type")
        is Decision.ChooseCardType -> listOf("card type")
        is Decision.ChooseCardName -> listOf("name")
        is Decision.ChoosePlayer -> listOf("player", "opponent")
        is Decision.ManaPayment -> listOf("pay")
        else -> listOf("choose")
    }
    val sentences = plain.split(Regex("""(?<=[.!?])\s+(?=[A-Z•])"""))
    val asked = sentences.filter { sentence -> words.any { Regex("""\b${Regex.escape(it)}""", RegexOption.IGNORE_CASE).containsMatchIn(sentence) } }
    return asked.joinToString(" ").ifEmpty { plain }
}

/** A cost in a few words with its mana and tap symbols ("{2}", "{T}", "Forage", "2 life"), for a question that asks to
 * pay it (STK-030); [CardText] draws the symbols. */
fun costText(cost: arcanum.api.Cost): String = cost.parts.joinToString(", ") { part ->
    when (part) {
        arcanum.api.CostPart.TapSelf -> "{T}"
        arcanum.api.CostPart.UntapSelf -> "{Q}"
        else -> org.asyncttwiz.automatum.CombatFlow.costLabel(arcanum.api.Cost(listOf(part)))
    }
}

/** The icon of a choice area by its label. */
fun areaIcon(label: String): ImageVector = when (label) {
    "Top" -> Icons.Outlined.VerticalAlignTop
    "Bottom" -> Icons.Outlined.VerticalAlignBottom
    "Graveyard", "Discard" -> Icons.Outlined.Inventory2
    "Hand", "Keep" -> HandCardsIcon
    "Face-down pile" -> Icons.Outlined.Flip
    else -> Icons.Outlined.Layers
}

/** The card of engine object [id] in a choice, as the viewer may see it. */
fun choiceCardOf(flow: FlowQuestions, play: TableBoard, viewer: String, id: ObjectId, tapped: Boolean = false): ChoiceCardData =
    choiceCard(play, viewer, id.toString(), flow.atwCard(id), flow.shown(id), objectName(flow, play, id), tapped)

/**
 * Choosing cards ([Decision.ChooseObjects], I-D48): the cards drawn like battlefield cards. A choice between two places is
 * the two-area screen ([TwoAreaChoice]); its OK also answers the orders the engine asks next of the same cards
 * ([FlowQuestions.answerWith]). Putting cards in order is one area; picking taps cards to choose them.
 */
@Composable
fun ObjectsSection(cast: FlowQuestions, flow: FlowQuestions, play: TableBoard, decision: Decision.ChooseObjects) {
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
                val chosen = (if (!places || shape.chosenFirst) state.first else state.second).let { objectsOf(ids, it) }
                val fits = chosen.size >= decision.min && decision.max.let { it == null || chosen.size <= it }
                OkRow(decision, fits) {
                    val then = listOfNotNull(state.first.takeIf { places && shape.orderFirst && it.size > 1 },
                        state.second.takeIf { places && shape.orderSecond && it.size > 1 })
                        .map { area -> Answer.Objects(area.let { objectsOf(ids, it) }) }
                    cast.answerWith(Answer.Objects(chosen), then)
                }
            }
            ObjectsShape.Shape.Pick -> {
                // Saved, so the picks survive hiding the panel with the eye and showing it again (STK-044, STK-056)
                var chosen by androidx.compose.runtime.saveable.rememberSaveable(decision.id.toString()) { mutableStateOf(listOf<String>()) }
                // Castability §3: in a cast or activation only what can be paid with the picks so far is offered
                var selection by remember(decision.id) { mutableStateOf<arcanum.engine.Selection?>(null) }
                androidx.compose.runtime.LaunchedEffect(decision.id, chosen) { cast.selectionOf(chosen.let { objectsOf(ids, it) }) { selection = it } }
                // What a report needs of a pick gone missing: the panel left the table with it
                androidx.compose.runtime.DisposableEffect(decision.id) {
                    onDispose { if (chosen.isNotEmpty()) AppLog.info("decision", "Choice ${decision.id.value}'s panel left the table with $chosen picked") }
                }
                val offered = selection?.candidates?.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id } ?: decision.options
                CardPicks(decision.options.filter { it in offered }.mapNotNull { cards[it.toString()] }, chosen, inspector) { key ->
                    chosen = when {
                        key in chosen -> chosen - key
                        decision.max == 1 -> listOf(key)
                        else -> chosen + key
                    }
                    // Report DH3QX6: each tap on a card of a choice
                    AppLog.info("decision", "Card tapped in ${decision.id.value}: ${cards[key]?.name} ($key); chosen now $chosen of ${decision.min}..${decision.max}")
                }
                if (shape.search) {
                    val engine = flow.engine
                    val others = remember(decision.id, engine) { engine?.let { searchedOthers(it, decision) }.orEmpty() }
                    if (others.isNotEmpty()) OtherCards(others.map { choiceCardOf(flow, play, viewer, it) }.sortedBy { it.name }, inspector)
                }
                OkRow(decision, chosen.size >= decision.min && decision.max.let { it == null || chosen.size <= it } && selection?.done != false) {
                    cast.answer(Answer.Objects(chosen.let { objectsOf(ids, it) }))
                }
            }
        }
    }
    ChoiceInspection(inspector, play, viewer)
}

/** STK-056: the cards of the zones a search looks through (the options' zones) that it can't find: the rest of [decision]'s
 * searched library (and any other searched zone). */
fun searchedOthers(state: arcanum.engine.state.GameState, decision: Decision.ChooseObjects): List<ObjectId> {
    val zones = decision.options.mapNotNull { id -> state.obj(id)?.let { it.zone to it.owner } }.distinct()
    return zones.flatMap { (zone, owner) -> state.zone(zone, owner) }.distinct() - decision.options.toSet()
}

/** STK-056: a search's "Other cards": the searched zone's cards it can't find, drawn as cards, dimmed and not selectable
 * (a tap does nothing); holding one inspects it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OtherCards(cards: List<ChoiceCardData>, inspector: ChoiceInspector) {
    Text("Other cards", Modifier.semantics { heading() }, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        color = Color(0xff4b5a5f))
    FlowRow(Modifier.semantics { contentDescription = "Other cards, ${cards.size}" }, horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (card in cards) ChoiceCardFace(card, Modifier.alpha(.55f).choiceCardGestures(card.key, tap = {},
            hold = { inspector.hold(card, it) }, moved = inspector::moved))
    }
}

/** The legend rule (CR 704.5j) as cards: tap the one to keep, then Keep. */
@Composable
fun LegendRuleSection(cast: FlowQuestions, flow: FlowQuestions, play: TableBoard, decision: Decision.LegendRule) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(decision.id) { ChoiceInspector() }
    var kept by remember(decision.id) { mutableStateOf<String?>(null) }
    val ids = decision.among.associateBy { it.toString() }
    Section(choiceWhat(decision), choiceHint(decision)) {
        CardPicks(decision.among.map { choiceCardOf(flow, play, viewer, it) }, listOfNotNull(kept), inspector) { kept = it }
        // TRN-063 (owner playtest 2026-11-09): Keep is the main button
        OkRow(decision, kept != null, label = "Keep") { kept?.let { ids[it] }?.let { cast.answer(Answer.Keep(it)) } }
    }
    ChoiceInspection(inspector, play, viewer)
}

/** Cards to pick from (I-D48): a tap chooses or unchooses one ([toggle]), the chosen ones ringed; holding inspects. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardPicks(cards: List<ChoiceCardData>, chosen: List<String>, inspector: ChoiceInspector, toggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (card in cards) ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = { toggle(card.key) },
            hold = { inspector.hold(card, it) }, moved = inspector::moved), selected = card.key in chosen,
            badge = chosen.indexOf(card.key).takeIf { it >= 0 && chosen.size > 1 }?.let { "${it + 1}" })
    }
}

/** A row of a choice about a card (a trigger, a replacement effect, a share of a division): its card, small, then
 * [content]; holding the card inspects it. */
@Composable
fun CardRow(card: ChoiceCardData?, inspector: ChoiceInspector, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (card != null) ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }, moved = inspector::moved), small = true)
        content()
    }
}

/** A colour's swatch for "Choose a colour". */
fun colorSwatch(symbol: Char): Color = when (symbol) {
    'W' -> Color(0xfff3edd2); 'U' -> Color(0xff2f6fb3); 'B' -> Color(0xff3a3335); 'R' -> Color(0xffc7412f)
    'G' -> Color(0xff2f7d4a); else -> Color(0xffa8a39a)
}

/** The hint under a section's title, when the panel's own title doesn't carry it ([ChoiceFrame.headed]). */
@Composable
fun ChoiceHintText(text: String) {
    CardText(text, Modifier.semantics { contentDescription = text }, color = Color(0xff4b5a5f),
        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp))
}

/** [keys]' objects among [ids] (a choice's cards by key); a key that isn't one is left out and reported. */
private fun objectsOf(ids: Map<String, ObjectId>, keys: List<String>): List<ObjectId> = keys.mapNotNull { key ->
    ids[key] ?: null.also { Faults.report("ChoiceScreens", "chosen card not among the options", mapOf("key" to key, "options" to ids.keys.joinToString())) }
}
