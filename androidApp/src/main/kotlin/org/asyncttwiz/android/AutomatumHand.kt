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
import org.asyncttwiz.automatum.CastChip
import org.asyncttwiz.automatum.CastExtraChip
import org.asyncttwiz.automatum.CastWayChip
import org.asyncttwiz.automatum.HandCastChips
import org.asyncttwiz.automatum.HandCasts
import org.asyncttwiz.protocol.PlayZone

/**
 * The viewer's hand with Automatum on (integration spec I-D50, ZON-079, ZON-080, owner 2026-11-07): a tap raises a hand
 * card and shows its chips, one per complete way to cast it (up to [HandCasts.MAX_CHIPS], else one "Cast…" chip); a chip's
 * tap casts it that way at once; a tap on the raised card casts a card without any choice and otherwise opens the cast
 * panel ([CastController.startFromHand]), the chips flashing as a hint that they cast directly. Holding a chip outlines the
 * sources Autotap would tap for it. A card Automatum doesn't let the player cast or play now isn't raised: it only shakes
 * (I-D46 (b); Inspect says why). The raised card is personal UI ([TableLayoutState.raised]); its chips are worked out on
 * [work] (the Automatum worker in the app) for the board the player acts on ([current]).
 */
internal class HandController(
    private val viewer: String,
    private val current: () -> TableBoard?,
    private val layout: MutableState<TableLayoutState>,
    private val work: AutomatumWork,
    /** Casts [cardId] the complete way [chip] says ([CastController.start]); false when it can't be begun. */
    private val cast: (cardId: String, chip: CastChip) -> Boolean,
    /** Casts [cardId] through the cast panel, or at once when it has no choice ([CastController.startFromHand]); [known]:
     * its chips when worked out. */
    private val panel: (cardId: String, known: HandCastChips?) -> Boolean,
    private val shake: (String) -> Unit,
    /** Whether [cardId] is known to have no cast or play Automatum allows now (null: not worked out yet). */
    private val unplayable: (TableBoard, String) -> Boolean?,
) {
    /** The raised card, null when none. */
    val raised: String? get() = layout.value.raised
    /** The raised card's chips on the board acted on, null while worked out or when it has none. */
    var chips by mutableStateOf<HandCastChips?>(null); private set
    /** The chip held (its details shown, its sources outlined): an index of [HandCastChips.chips], or [MORE]. */
    var held by mutableStateOf<Int?>(null)
    /** Bumped when a tap on the raised card opens the cast panel while its chips show: they flash (ZON-080). */
    var flash by mutableIntStateOf(0); private set
    private var generation = 0
    /** The tap that raised the card still waits for its chips: none castable lowers it again with a shake. */
    private var answering = false
    private var board: Any? = null

    /** A tap on [cardId] in a hand (ZON-079): the viewer's own card is raised, or cast (or its cast panel opened) when it
     * is the raised one; a card in the other player's hand does nothing. */
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
        chips = null; held = null; answering = true
        compute(play)
    }

    /** Lowers the raised card (a tap elsewhere, a cast). */
    fun lower() {
        if (layout.value.raised != null) layout.value = layout.value.copy(raised = null)
        generation++
        chips = null; held = null; board = null
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

    /** A tap on chip [index] (ZON-080): casts the raised card that complete way at once (Autotap pays; targets, X and
     * modes are asked as needed). A chip the player can't pay for now does nothing. */
    fun chip(index: Int): Boolean {
        val card = raised ?: return false
        val chip = chips?.takeIf { it.cardId == card }?.chips?.getOrNull(index) ?: return false
        if (!chip.affordable) return false
        lower()
        return cast(card, chip)
    }

    /** A tap on the "Cast…" chip (more complete ways than chips): the cast panel with every choice. */
    fun more(): Boolean {
        val card = raised ?: return false
        val known = chips?.takeIf { it.cardId == card && it.more } ?: return false
        if (!known.affordable) return false
        lower()
        return panel(card, known)
    }

    /** The raised card's chips when worked out, for a cast of [cardId] begun by a double-tap or a drag (ZON-004, ZON-077). */
    fun known(cardId: String): HandCastChips? = chips?.takeIf { it.cardId == cardId && cardId == raised }

    /** A tap on the raised card (ZON-079): a card without any choice is cast; otherwise the cast panel opens (it never
     * picks a chip), the chips flashing as a hint that they cast directly. */
    private fun castRaised(): Boolean {
        val card = raised ?: return false
        // TBL-118: a card the player can't pay for (or that Automatum doesn't allow now) shakes and stays raised
        val known = chips?.takeIf { it.cardId == card }
        val play = current()
        if (known?.affordable == false || (known == null && play != null && unplayable(play, card) == true)) { shake(card); return false }
        if (known != null && !known.choice) {
            val chip = known.chips.first()
            lower()
            return cast(card, chip)
        }
        if (known != null && !known.more) flash++
        lower()
        return panel(card, known)
    }

    /** ZON-079: the sources Autotap would tap for the raised card on [play] (outlined on the table): for the held chip
     * while one is held, else for the regular way. */
    fun sources(play: TableBoard): Set<String> {
        val o = chips ?: return emptySet()
        if (o.cardId != raised) return emptySet()
        val sources = held?.let { o.chips.getOrNull(it)?.sources } ?: o.sources
        // TBL-130 (owner playtest 2026-11-07): in a stack of identical sources the topmost ones, as Autotap takes them
        return TopmostSources.cards(sources, BattlefieldLayout.groups(play, viewer)) { id -> play.board.cards.firstOrNull { it.id == id }?.tapped == true }
    }

    private fun compute(play: TableBoard) {
        val card = raised ?: return
        val run = ++generation
        board = play.board
        work.run({
            try {
                val definitions = AutomatumBoards.definitions(play)
                val offered = AutomatumBoards.handPlays(play, viewer).of(card)
                val seeds = org.asyncttwiz.automatum.CastFlow.Seeds { DeviceRandom.seed() }
                // R4a: the chips are worked out on the game's own engine state
                val engine = requireNotNull(play.engine) { "No board to cast on" }
                HandCasts.chips(org.asyncttwiz.automatum.CastFlow.Source.engine(engine.state, play.board, play.gameId),
                    definitions, viewer, card, offered, seeds)
            } catch (problem: Exception) {
                AppLog.warn("automatum", "Couldn't work the cost chips out: ${problem::class.java.simpleName}")
                null
            }
        }) { result ->
            if (run != generation || raised != card) return@run
            // A card raised before Automatum knew it can't be cast or paid for now goes back down with a shake
            val tapped = answering
            answering = false
            if (tapped && (result == null || !result.affordable)) { lower(); shake(card); return@run }
            chips = result
        }
    }

    companion object {
        /** [held] of the "Cast…" chip. */
        const val MORE = -1
    }
}

/**
 * ZON-080 (I-D50, owner 2026-11-07): the raised hand card's chips in a row just above it, the held chip's details above
 * them: one per complete way to cast it (white with a violet outline, its name and whole cost), or a single "Cast…" chip
 * with the cheapest cost when it has more than [HandCasts.MAX_CHIPS]; a chip the player can't pay for is greyed. Mana costs
 * are drawn as mana symbols ([CardText]). When a tap on the raised card opens the cast panel the chips flash once over it
 * before they go ([HandController.flash]).
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
    val options = hand.chips?.takeIf { it.cardId == raised }
    val current = if (raised != null && card != null && options != null && !toolkit.cast.open) Triple(raised, card, options) else null
    val last = remember { arrayOfNulls<Triple<String, TableItem, HandCastChips>>(1) }
    if (current != null) last[0] = current
    // ZON-080: a tap on the raised card that opens the cast panel flashes the chips once (they stay over the panel meanwhile)
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    var flashing by remember { mutableStateOf(false) }
    val flashes = hand.flash
    LaunchedEffect(flashes) {
        if (flashes == 0) return@LaunchedEffect
        flashing = true
        flash.snapTo(0f)
        flash.animateTo(1f, androidx.compose.animation.core.tween(CHIP_FLASH_MILLIS))
        flashing = false
    }
    val (shownCard, shownItem, shownOptions) = last[0] ?: return
    val multiplier by rememberCardMovePreferences(toolkit.viewer).multiplierState()
    val millis = (HAND_REFLOW_DURATION_MILLIS / cardMoveMultiplier(multiplier)).toInt().coerceAtLeast(1)
    val rise = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.roundToPx() }
    val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(millis)
    androidx.compose.animation.AnimatedVisibility(current != null || flashing, Modifier.fillMaxSize(),
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(millis)) +
            androidx.compose.animation.slideInVertically(spec) { rise },
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(millis)) +
            androidx.compose.animation.slideOutVertically(spec) { rise }) {
        // One bright pulse: up and back down over the flash
        val level = if (flashing) kotlin.math.sin(flash.value * Math.PI).toFloat() else 0f
        RaisedChipsRow(hand, play, shownCard, shownItem, shownOptions, level)
    }
}

/** How long the chips' flash lasts (ZON-080). */
internal const val CHIP_FLASH_MILLIS = 450

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaisedChipsRow(hand: HandController, play: TableBoard, raised: String, card: TableItem, options: HandCastChips, flash: Float) {
    val held = hand.held
    Layout(content = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            held?.let { ChipDetails(play, raised, options, it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (options.more) {
                    MoreChip(options.cheapest, options.affordable, { hand.more() }, { hold -> hand.held = if (hold) HandController.MORE else null }, flash)
                } else options.chips.forEachIndexed { index, chip ->
                    WayChip(CastWayChip(chip.input, chip.name, chip.cost, chip.text, chip.affordable), false,
                        { hand.chip(index) }, { hold -> hand.held = if (hold) index else null }, flash)
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

/** A way chip (ZON-080): its name and cost, violet-outlined (filled when [chosen], the cast panel's way), greyed when it
 * can't be paid now; [flash] (0-1) tints it violet for the raised card's flash. */
@Composable
internal fun WayChip(chip: CastWayChip, chosen: Boolean, tap: () -> Unit, hold: (Boolean) -> Unit, flash: Float = 0f) {
    val ink = if (chosen) Color.White else AutomatumViolet
    Surface(shape = RoundedCornerShape(14.dp), color = if (chosen) AutomatumViolet else
            androidx.compose.ui.graphics.lerp(Color.White, Color(0xffd9ccff), flash.coerceIn(0f, 1f)),
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
            val text = chip.text
            // A plain "cost: effect" ability from the hand reads as on the card, cost first (owner playtest 2026-11-06)
            if (chip.input is arcanum.engine.input.Input.Activate && chip.name == "Activate" && text != null && ": " in text) {
                val (effect, cost) = abilityParts(text)
                AbilityChipText(effect, cost, if (chip.affordable || chosen) ink else Color(0xff59656a))
            } else {
                CardText(chip.name, Modifier.weight(1f, fill = false).widthIn(max = CHIP_TEXT_WIDTH), color = if (chip.affordable || chosen) ink else Color(0xff59656a),
                    style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold))
                chip.cost?.let { Spacer(Modifier.width(4.dp)); CardText(it, Modifier.widthIn(max = CHIP_COST_WIDTH), style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp)) }
            }
        }
    }
}

/** The "Cast…" chip (ZON-080): a card with more complete ways than chips; its cheapest [cost]; a tap opens the cast panel. */
@Composable
private fun MoreChip(cost: String?, affordable: Boolean, tap: () -> Unit, hold: (Boolean) -> Unit, flash: Float) {
    Surface(shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.lerp(Color.White, Color(0xffd9ccff), flash.coerceIn(0f, 1f)),
        border = BorderStroke(1.5.dp, if (affordable) AutomatumViolet else Color(0xffb7bcbf)), shadowElevation = 2.dp,
        modifier = Modifier.alpha(if (affordable) 1f else .45f).semantics {
            contentDescription = "Way $MORE_NAME${cost?.let { " $it" }.orEmpty()}"
            if (!affordable) stateDescription = "can't be paid now"
            role = Role.Button
            onClick { tap(); true }
            onLongClick { hold(true); true }
        }.chipGestures(tap, hold)) {
        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(MORE_NAME, color = if (affordable) AutomatumViolet else Color(0xff59656a), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            cost?.let { Spacer(Modifier.width(4.dp)); CardText(it, Modifier.widthIn(max = CHIP_COST_WIDTH), style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp)) }
        }
    }
}

/** The "Cast…" chip's name (ZON-080). */
internal const val MORE_NAME = "Cast…"

/** An extra chip of the cast panel (ZON-082): amber-outlined, "+" and a check box; a repeatable one counts "×N" with "−". */
@Composable
internal fun ExtraChip(chip: CastExtraChip, times: Int, tap: () -> Unit, less: () -> Unit, hold: (Boolean) -> Unit) {
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

/** The held chip's details, card-like (ZON-080): its name and whole cost, its printed text (the card's, then its
 * alternative cost's and extras' lines); the "Cast…" chip says how many ways there are; a chip the player can't pay for
 * now says so. */
@Composable
private fun ChipDetails(play: TableBoard, cardId: String, options: HandCastChips, held: Int) {
    val chip = options.chips.getOrNull(held)
    if (chip == null && held != HandController.MORE) return
    val title = chip?.let { "${it.name}${it.cost?.let { c -> " $c" }.orEmpty()}" } ?: "$MORE_NAME${options.cheapest?.let { " $it" }.orEmpty()}"
    val text = if (chip == null) "${options.count} ways to cast it: tap to choose in the cast panel."
        else listOfNotNull(wayText(play, cardId, CastWayChip(chip.input, chip.name, chip.cost, null, chip.affordable)), chip.text)
            .distinct().joinToString("\n").ifEmpty { null }
    val affordable = chip?.affordable ?: options.affordable
    Surface(shape = RoundedCornerShape(6.dp), color = Color.White, shadowElevation = 6.dp,
        border = BorderStroke(1.dp, AutomatumViolet),
        modifier = Modifier.widthIn(max = 300.dp).semantics { contentDescription = "Details $title" }) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardText(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            text?.let { CardText(it, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)) }
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
