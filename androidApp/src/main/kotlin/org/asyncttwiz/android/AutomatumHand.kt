package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.input.Input
import org.asyncttwiz.automatum.CastExtraChip
import org.asyncttwiz.automatum.CastWayChip
import org.asyncttwiz.automatum.HandCastOptions
import org.asyncttwiz.automatum.HandCasts
import org.asyncttwiz.protocol.PlayZone

/**
 * The viewer's hand with Automatum on (integration spec I-D50, ZON-079, ZON-080): a tap raises a hand card and shows its
 * cost chips; the chips choose the way to cast it and its extras; tapping the raised card (or the chosen way's chip) casts
 * it that way. A card Automatum doesn't let the player cast or play now isn't raised: it only shakes (I-D46 (b); Inspect
 * says why). The raised card is personal UI ([TableLayoutState.raised]); its chips are worked out on [work] (the
 * Automatum worker in the app) for the board the player acts on ([current]).
 */
internal class HandController(
    private val viewer: String,
    private val current: () -> TableBoard?,
    private val layout: MutableState<TableLayoutState>,
    private val work: AutomatumWork,
    /** Casts [cardId] [way] with [extras] chosen ([CastController.start]); false when it can't be begun. */
    private val cast: (cardId: String, way: Input?, extras: List<Int>) -> Boolean,
    private val shake: (String) -> Unit,
    /** Whether [cardId] is known to have no cast or play Automatum allows now (null: not worked out yet). */
    private val unplayable: (TableBoard, String) -> Boolean?,
) {
    /** The chosen way (an index of [HandCastOptions.ways]) and how often each extra is paid. */
    data class Choice(val way: Int = 0, val times: List<Int> = emptyList())

    /** The raised card, null when none. */
    val raised: String? get() = layout.value.raised
    var choice by mutableStateOf(Choice()); private set
    /** The raised card's chips for [choice] on the board acted on, null while worked out or when it has none. */
    var options by mutableStateOf<HandCastOptions?>(null); private set
    /** The chip held for its details: a way's index, or [EXTRA] + an extra's index. */
    var held by mutableStateOf<Int?>(null)
    private var generation = 0
    /** The tap that raised the card still waits for its chips: none castable lowers it again with a shake. */
    private var answering = false
    private var board: Any? = null

    /** A tap on [cardId] in a hand (ZON-079): the viewer's own card is raised, or cast when it is the raised one; a card
     * in the other player's hand does nothing. */
    fun tap(cardId: String): Boolean {
        val play = current() ?: return false
        if (!CastController.ownHand(play, cardId, viewer)) { lower(); return true }
        if (raised == cardId) return castRaised()
        // I-D50, I-D46 (b): a card Automatum doesn't allow now only shakes: no raise, no chips (Inspect says why)
        if (unplayable(play, cardId) == true) { lower(); shake(cardId); return true }
        raise(play, cardId)
        return true
    }

    private fun raise(play: TableBoard, cardId: String) {
        layout.value = layout.value.copy(raised = cardId)
        choice = Choice(); options = null; held = null; answering = true
        compute(play)
    }

    /** Lowers the raised card (a tap elsewhere, a cast). */
    fun lower() {
        if (layout.value.raised != null) layout.value = layout.value.copy(raised = null)
        generation++
        options = null; choice = Choice(); held = null; board = null
    }

    /** The board acted on changed: the raised card's chips are worked out again, or it is lowered when it left the
     * viewer's hand or the hand isn't Automatum's any more. */
    fun follow() {
        val card = raised ?: return
        val play = current()
        if (play == null || !CastController.ownHand(play, card, viewer) ||
            card in castSpellSources(play.board)) { lower(); return }
        if (board !== play.board) compute(play)
    }

    /** A tap on way chip [index]: chooses it; tapping the chosen one casts the card that way. A way the player can't pay
     * for now does nothing. */
    fun way(index: Int): Boolean {
        val chip = options?.ways?.getOrNull(index) ?: return false
        if (!chip.affordable && index != choice.way) return false
        if (index == choice.way) return castRaised()
        choice = Choice(index)
        current()?.let(::compute)
        return true
    }

    /** A tap on extra chip [index]: turns it on or off, or counts a repeatable one up; never casts. One the player can't
     * pay for now is never turned on. */
    fun extra(index: Int): Boolean {
        val o = options ?: return false
        val chip = o.extras.getOrNull(index) ?: return false
        val now = o.times.getOrElse(index) { 0 }
        val next = if (chip.repeatable) now + 1 else if (now > 0) 0 else 1
        if (next > now && !chip.affordable) return false
        return choose(index, next)
    }

    /** The small "−" of a repeatable extra: once fewer. */
    fun less(index: Int): Boolean {
        val now = options?.times?.getOrElse(index) { 0 } ?: return false
        return now > 0 && choose(index, now - 1)
    }

    private fun choose(index: Int, times: Int): Boolean {
        val o = options ?: return false
        choice = choice.copy(times = o.times.toMutableList().also { it[index] = times })
        current()?.let(::compute)
        return true
    }

    /** What a cast of [cardId] begun now (a tap on the raised card, a double-tap or a drag, ZON-004, ZON-077) takes:
     * the raised card's chosen way and extras, or the regular cast without extras for any other card. */
    fun castChoice(cardId: String): Pair<Input?, List<Int>> {
        if (cardId != raised) return null to emptyList()
        val chosen = options?.takeIf { it.cardId == cardId && it.way == choice.way }?.chosen?.input
        return chosen to choice.times
    }

    private fun castRaised(): Boolean {
        val card = raised ?: return false
        // TBL-118: a choice the player can't pay for (or a card Automatum doesn't allow now) shakes and stays raised
        val known = options?.takeIf { it.cardId == card }
        val play = current()
        if (known?.affordable == false || (known == null && play != null && unplayable(play, card) == true)) { shake(card); return false }
        val (way, extras) = castChoice(card)
        lower()
        return cast(card, way, extras)
    }

    /** ZON-079: the sources Autotap would tap for the raised card on [play] (outlined on the table). */
    fun sources(play: TableBoard): Set<String> {
        val o = options ?: return emptySet()
        if (o.cardId != raised) return emptySet()
        return o.sources
    }

    private fun compute(play: TableBoard) {
        val card = raised ?: return
        val run = ++generation
        val chosen = choice
        board = play.board
        work.run({
            try {
                val definitions = AutomatumBoards.definitions(play)
                val offered = AutomatumBoards.handPlays(play, viewer).of(card)
                val seeds = org.asyncttwiz.automatum.CastFlow.Seeds { DeviceRandom.seed() }
                // R4a: the chips are worked out on the game's own engine state
                val engine = requireNotNull(play.engine) { "No board to cast on" }
                HandCasts.options(org.asyncttwiz.automatum.CastFlow.Source.engine(engine.state, play.board, play.gameId),
                    definitions, viewer, card, offered, chosen.way, chosen.times, seeds)
            } catch (problem: Exception) {
                AppLog.warn("automatum", "Couldn't work the cost chips out: ${problem::class.java.simpleName}")
                null
            }
        }) { result ->
            if (run != generation || raised != card) return@run
            // A card raised before Automatum knew it can't be cast or paid for now goes back down with a shake
            val tapped = answering
            answering = false
            if (tapped && (result == null || result.ways.none { it.affordable })) { lower(); shake(card); return@run }
            options = result
        }
    }

    companion object {
        /** [held] of an extra chip: this plus its index. */
        const val EXTRA = 1000
    }
}

/**
 * ZON-080 (I-D50): the raised hand card's cost chips in a row just above it, the held chip's details above them. Ways are
 * white with a violet outline (the chosen one filled violet), extras have an amber outline, "+" and a check box; a chip
 * the player can't pay for is greyed. Mana costs are drawn as mana symbols ([CardText]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RaisedCardChips(toolkit: TableToolkitController, play: TableBoard, scene: TableScene?) {
    val hand = toolkit.hand
    LaunchedEffect(IdentityKey(play.board)) { hand.follow() }
    // Owner playtest 2026-11-04 #9: the chips come and go with a short fade and rise, as long as the card's own rise at
    // the card move speed (HIS-028); the last ones shown stay drawn while they go
    val raised = hand.raised
    val card = raised?.let { id -> scene?.items?.firstOrNull { it.cardId == id && it.zone == PlayZone.HAND && it.raised } }
    val options = hand.options?.takeIf { it.cardId == raised }
    val current = if (raised != null && card != null && options != null && !toolkit.cast.open) Triple(raised, card, options) else null
    val last = remember { arrayOfNulls<Triple<String, TableItem, HandCastOptions>>(1) }
    if (current != null) last[0] = current
    val (shownCard, shownItem, shownOptions) = last[0] ?: return
    val multiplier by rememberCardMovePreferences(toolkit.viewer).multiplierState()
    val millis = (HAND_REFLOW_DURATION_MILLIS / cardMoveMultiplier(multiplier)).toInt().coerceAtLeast(1)
    val rise = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.roundToPx() }
    val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(millis)
    androidx.compose.animation.AnimatedVisibility(current != null, Modifier.fillMaxSize(),
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(millis)) +
            androidx.compose.animation.slideInVertically(spec) { rise },
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(millis)) +
            androidx.compose.animation.slideOutVertically(spec) { rise }) {
        RaisedChipsRow(hand, play, shownCard, shownItem, shownOptions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaisedChipsRow(hand: HandController, play: TableBoard, raised: String, card: TableItem, options: HandCastOptions) {
    val held = hand.held
    Layout(content = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            held?.let { ChipDetails(play, raised, options, it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.ways.forEachIndexed { index, chip ->
                    WayChip(chip, index == options.way, { hand.way(index) }, { hold -> hand.held = if (hold) index else null })
                }
                options.extras.forEachIndexed { index, chip ->
                    ExtraChip(chip, options.times.getOrElse(index) { 0 }, { hand.extra(index) }, { hand.less(index) },
                        { hold -> hand.held = if (hold) HandController.EXTRA + index else null })
                }
            }
        }
    }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 8.dp.roundToPx()
        val placeable = measurables.first().measure(Constraints(maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0),
            maxHeight = constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (card.bounds.center.x.dp.roundToPx() - placeable.width / 2)
                .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val y = (card.bounds.y.dp.roundToPx() - 6.dp.roundToPx() - placeable.height).coerceAtLeast(0)
            placeable.place(x, y)
        }
    }
}

/** A chip's tap and hold: [hold] true while it is held (its details shown), false on release. */
internal fun Modifier.chipGestures(tap: () -> Unit, hold: (Boolean) -> Unit) = pointerInput(tap, hold) {
    detectTapGestures(onTap = { tap() }, onLongPress = { hold(true) }, onPress = {
        tryAwaitRelease()
        hold(false)
    })
}

@Composable
internal fun WayChip(chip: CastWayChip, chosen: Boolean, tap: () -> Unit, hold: (Boolean) -> Unit) {
    val ink = if (chosen) Color.White else AutomatumViolet
    Surface(shape = RoundedCornerShape(14.dp), color = if (chosen) AutomatumViolet else Color.White,
        border = BorderStroke(1.5.dp, if (chip.affordable || chosen) AutomatumViolet else Color(0xffb7bcbf)), shadowElevation = 2.dp,
        modifier = Modifier.alpha(if (chip.affordable) 1f else .45f).semantics {
            contentDescription = "Way ${chip.name}${chip.cost?.let { " $it" }.orEmpty()}"
            stateDescription = listOfNotNull("chosen".takeIf { chosen }, "can't be paid now".takeIf { !chip.affordable }).joinToString()
            role = Role.Button
            onClick { tap(); true }
            onLongClick { hold(true); true }
        }.chipGestures(tap, hold)) {
        // Owner playtest 2026-11-05 evening: a long name wraps onto more rows instead of being cut
        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CardText(chip.name, Modifier.weight(1f, fill = false).widthIn(max = CHIP_TEXT_WIDTH), color = if (chip.affordable || chosen) ink else Color(0xff59656a),
                style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold))
            chip.cost?.let { Spacer(Modifier.width(4.dp)); CardText(it, Modifier.widthIn(max = CHIP_COST_WIDTH), style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp)) }
        }
    }
}

@Composable
private fun ExtraChip(chip: CastExtraChip, times: Int, tap: () -> Unit, less: () -> Unit, hold: (Boolean) -> Unit) {
    val on = times > 0
    val ink = if (chip.affordable || on) Color(0xff7a4a00) else Color(0xff59656a)
    Surface(shape = RoundedCornerShape(14.dp), color = if (on) Color(0xfffff3dc) else Color.White,
        border = BorderStroke(1.5.dp, if (chip.affordable || on) AutotapAmber else Color(0xffb7bcbf)), shadowElevation = 2.dp,
        modifier = Modifier.alpha(if (chip.affordable || on) 1f else .45f).semantics {
            contentDescription = "Extra ${chip.name}${chip.cost?.let { " $it" }.orEmpty()}"
            stateDescription = listOfNotNull(if (on) (if (chip.repeatable) "×$times" else "on") else "off",
                "can't be paid now".takeIf { !chip.affordable }).joinToString()
            role = Role.Checkbox
            onClick { tap(); true }
            onLongClick { hold(true); true }
        }.chipGestures(tap, hold)) {
        Row(Modifier.heightIn(min = 32.dp).padding(start = 6.dp, end = if (chip.repeatable && on) 2.dp else 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.size(16.dp),
                tint = if (chip.affordable || on) AutotapAmber else Color(0xff8a9296))
            Spacer(Modifier.width(3.dp))
            CardText("+ ${chip.name}", Modifier.weight(1f, fill = false).widthIn(max = CHIP_TEXT_WIDTH), color = ink,
                style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold))
            chip.cost?.let { Spacer(Modifier.width(4.dp)); CardText(it, Modifier.widthIn(max = CHIP_COST_WIDTH), style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp)) }
            if (chip.repeatable && on) {
                Spacer(Modifier.width(4.dp))
                Text("×$times", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Surface(shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, AutotapAmber),
                    modifier = Modifier.padding(start = 4.dp).size(24.dp).semantics {
                        contentDescription = "One ${chip.name} fewer"; role = Role.Button; onClick { less(); true }
                    }.pointerInput(less) { detectTapGestures(onTap = { less() }) }) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Remove, null, Modifier.size(14.dp), tint = ink) }
                }
            }
        }
    }
}

/** The held chip's details, card-like (ZON-080): its name and cost, its printed text, and for the chosen way the total
 * with its extras; a chip the player can't pay for now says so. */
@Composable
private fun ChipDetails(play: TableBoard, cardId: String, options: HandCastOptions, held: Int) {
    val extra = held >= HandController.EXTRA
    val way = if (extra) null else options.ways.getOrNull(held)
    val chip = if (extra) options.extras.getOrNull(held - HandController.EXTRA) else null
    if (way == null && chip == null) return
    val title = way?.let { "${it.name}${it.cost?.let { c -> " $c" }.orEmpty()}" }
        ?: chip!!.let { "+ ${it.name}${it.cost?.let { c -> " $c" }.orEmpty()}" }
    val text = way?.let { wayText(play, cardId, it) } ?: chip?.let { printedLine(play, cardId, it.name) ?: it.text }
    val total = options.total?.takeIf { way != null && held == options.way && options.times.any { it > 0 } }
    val affordable = way?.affordable ?: chip!!.affordable
    Surface(shape = RoundedCornerShape(6.dp), color = Color.White, shadowElevation = 6.dp,
        border = BorderStroke(1.dp, if (extra) AutotapAmber else AutomatumViolet),
        modifier = Modifier.widthIn(max = 300.dp).semantics { contentDescription = "Details $title" }) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardText(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            text?.let { CardText(it, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)) }
            total?.let { CardText("In all: $it", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)) }
            if (!affordable) Text("Your mana can't pay this now.", fontSize = 12.sp, color = Color(0xffb3261e))
        }
    }
}

/** The line of [cardId]'s printed rules text that begins with the keyword [name] ("Kicker {4} (You may pay …)"). */
private fun printedLine(play: TableBoard, cardId: String, name: String): String? = cardText(play, cardId)?.lines()
    ?.firstOrNull { it.trim().startsWith(name, ignoreCase = true) }?.trim()

/** A way's printed text: its alternative cost's (its printed line when there is one), the face's it casts, or the card's
 * own. */
private fun wayText(play: TableBoard, cardId: String, way: CastWayChip): String? {
    way.text?.let { return printedLine(play, cardId, way.name) ?: it }
    val face = (way.input as? Input.Cast)?.way?.takeIf { !it.fused }?.face ?: (way.input as? Input.PlayLand)?.face ?: 0
    val card = play.board.cards.singleOrNull { it.id == cardId } ?: return null
    val faces = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces ?: return null
    return if (face == 0 && (way.input as? Input.Cast)?.way?.fused != true) cardText(play, cardId)
        else faces.getOrNull(face)?.rulesText?.trim()?.ifEmpty { null }
}
