package org.asyncttwiz.android

import org.asyncttwiz.protocol.Faults
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.storage.ArtworkReference
import kotlin.concurrent.Volatile
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The test tag of the play shown in the middle of the table (HIS-112). */
const val PLAY_SHOW_TAG = "play-show"

/**
 * HIS-112: what the table shows of a play: [description] says it in words (its content description), [caption] the line
 * under it (as the Stack line names it, STK-057); a spell as its card [face] ([covered]: a face-down spell, a card back),
 * an ability as its token ([ability], with its source's [art] or [typeIcon]); [own] colours its outline.
 */
data class PlayShown(val description: String, val caption: List<StackTextPiece>, val own: Boolean,
    val face: InspectTile.Face? = null, val covered: Boolean = false, val ability: StackAbilityShown? = null,
    val art: ArtworkReference? = null, val typeIcon: String = "")

object PlayShows {
    /** What [viewer]'s table shows of [show] on [play] (the board at its event); [names]: the players' names by account. */
    fun of(play: TableBoard, show: GameDevice.PlayShow, viewer: String, names: Map<String, String>): PlayShown {
        val entry = "entry:${show.entry}"
        val effect = play.board.stackEffects.firstOrNull { it.id == entry }
        val own = show.by.value == viewer
        val who = if (own) "You" else names[show.by.value] ?: "The other player"
        val line = effect?.let { StackLine.entryPieces(play, entry, viewer, names) }
        val cardId = show.card?.value
        val card = (if (effect != null) CombatStackRules.inspection(play.board, entry) else null)
            ?: cardId?.let { id -> play.board.cards.firstOrNull { it.id == id } }
        val visible = card != null && effect?.faceDown != true &&
            (effect?.frozen != null || CardVisibility.isVisibleFaceUp(play.board, card, viewer))
        val definition = card?.let { c -> play.definitions.firstOrNull { it.digest == c.definition }?.definition }
        val faceIndex = definition?.faces?.indexOfFirst { it.faceId == card.faceId }?.takeIf { it >= 0 } ?: 0
        val name = definition?.faces?.getOrNull(faceIndex)?.name?.takeIf { visible && it.isNotBlank() }
        return when (show.kind) {
            GameDevice.PlayKind.Cast -> {
                val caption = line ?: listOf(StackTextPiece("$who cast ${name ?: unnamed(visible, "a card", show)}"))
                val face = if (visible && definition != null) inspectFaces(InspectedCard.retained(definition), faceIndex,
                    flippedOnTable(definition, card.faceId), "play-show/${card.id}",
                    if (own) OwnTableColor else OpponentTableColor, card.token).firstOrNull() else null
                PlayShown(caption.plain(), caption, own, face = face, covered = face == null)
            }
            GameDevice.PlayKind.Activated, GameDevice.PlayKind.Triggered -> {
                val triggered = show.kind == GameDevice.PlayKind.Triggered
                val ability = effect?.let { StackAbilities.of(play, entry, viewer) }
                    ?: StackAbilityShown(triggered, name ?: unnamed(visible, "Ability", show), "")
                val source = choiceCard(play, viewer, "play-show/source", effect?.sourceId ?: cardId, visible, ability.source)
                val caption = line ?: listOf(StackTextPiece(ability.name))
                PlayShown(caption.plain(), caption, own, ability = ability, art = source.art, typeIcon = source.typeIcon)
            }
        }
    }
}

/** HIS-112: the shown card's height: about half the board's, at most this. */
private const val PLAY_SHOW_MAX_HEIGHT = 300f

/** HIS-112 (owner playtest 2026-11-07): the flight to the Stack at Card move speed 1×. */
const val PLAY_SHOW_FLIGHT_MILLIS = 250

/** HIS-112: how long the flight to the Stack takes at the Card move speed [multiplier] (TBL-038), for a play shown again
 * [repeats] times before in its chain ([playShowRepeatFactor], down to [PLAY_SHOW_FLIGHT_FLOOR_MILLIS]). */
fun playShowFlightMillis(multiplier: Float, repeats: Int = 0): Int {
    val full = (PLAY_SHOW_FLIGHT_MILLIS / cardMoveMultiplier(multiplier)).roundToInt().coerceAtLeast(1)
    if (repeats <= 0) return full
    val floor = minOf(full, (PLAY_SHOW_FLIGHT_FLOOR_MILLIS / cardMoveMultiplier(multiplier)).roundToInt().coerceAtLeast(1))
    return maxOf(floor, (full * playShowRepeatFactor(repeats)).roundToInt())
}

/** HIS-112 (owner playtest 2026-11-11): each repeat of the same ability in one chain shows this much shorter than the one
 * before (35% shorter). */
const val PLAY_SHOW_REPEAT_FACTOR = .65

/** HIS-112: the shortest a repeated play shows ([PLAY_SHOW_REPEAT_FACTOR]); never longer than the setting itself. */
const val PLAY_SHOW_REPEAT_FLOOR_MILLIS = 150L

/** HIS-112: the shortest flight of a repeated play at Card move speed 1×. */
const val PLAY_SHOW_FLIGHT_FLOOR_MILLIS = 100

/** HIS-112: a play following the one before within this long after it landed continues its chain ([PlayShowPace]). */
const val PLAY_SHOW_CHAIN_GAP_MILLIS = 1_000L

/** HIS-112: the share of the full time a play shown again [repeats] times before in its chain takes. */
fun playShowRepeatFactor(repeats: Int): Double = PLAY_SHOW_REPEAT_FACTOR.pow(repeats.coerceAtLeast(0))

/**
 * HIS-112 (owner playtest 2026-11-11: a board wipe's 30 "whenever a creature dies" triggers took half a minute): how long a
 * play shows for the "Show plays and combat" time [setting] when the same ability was shown [repeats] times before in its
 * chain: the first the whole setting, each repeat 35% shorter, down to [PLAY_SHOW_REPEAT_FLOOR_MILLIS].
 */
fun playShowMillis(setting: Long, repeats: Int): Long {
    if (repeats <= 0) return setting
    val floor = minOf(setting, PLAY_SHOW_REPEAT_FLOOR_MILLIS)
    return maxOf(floor, (setting * playShowRepeatFactor(repeats)).roundToLong())
}

/**
 * HIS-112 (owner playtest 2026-11-11): the plays shown one after another on this table, for their pace. A chain is the
 * plays that follow one another, each showing within [PLAY_SHOW_CHAIN_GAP_MILLIS] after the one before landed; in it, the
 * same ability from the same kind of source ([key]: the source's name and the ability's text, whoever controls it) shows
 * faster each time ([playShowMillis]). Main thread; times are the frames' clock.
 */
class PlayShowPace {
    private val seen = HashMap<Any, Int>()
    private var landedAt: Long? = null

    /** A play shown begins at [now]: how many times its [key] showed before in this chain (0 for a play without one), and
     * how long after the one before landed it begins (null: it begins a chain). */
    fun begin(key: Any?, now: Long): Pair<Int, Long?> {
        val gap = landedAt?.let { now - it }?.takeIf { it <= PLAY_SHOW_CHAIN_GAP_MILLIS }
        if (gap == null) seen.clear()
        landedAt = null
        if (key == null) return 0 to gap
        val repeats = seen[key] ?: 0
        seen[key] = repeats + 1
        return repeats to gap
    }

    /** The play shown landed at [now] (a play taken back or ended by timeline mode never lands: the next begins a chain). */
    fun landed(now: Long) { landedAt = now }

    /** What makes plays repeats of one another ([PlayShown.ability]: an ability's source name and text); null for a spell. */
    fun key(shown: PlayShown): Any? = shown.ability
}

/** Tests: each play shown as its time starts on [me]'s table, with the repeats before it in its chain and its times. */
object PlayShowProbe {
    @Volatile var listener: ((me: String, show: GameDevice.PlayShow, repeats: Int, millis: Long, flightMillis: Int) -> Unit)? = null
}

/** HIS-112: the shown card's opacity at [progress] of its flight: whole over the first half, fading out over the second. */
fun playShowFlightAlpha(progress: Float): Float = (2f - 2f * progress).coerceIn(0f, 1f)

/** HIS-112 (owner 2026-11-12): plays put on the Stack in one go start flying in from their sources this long apart, in the
 * order they went on the Stack. */
const val PLAY_SHOW_STAGGER_MILLIS = 50L

/** HIS-112 (owner 2026-11-12): how far apart (dp, right and down) the plays waiting large in the middle lie, so the pile
 * shows each; [PLAY_SHOW_PILE_SPREAD] at most for the whole pile. */
private const val PLAY_SHOW_PILE_STEP = 8f
private const val PLAY_SHOW_PILE_SPREAD = 48f

/** The test tag of a play waiting large in the middle under the one shown (HIS-112). */
const val PLAY_SHOW_WAITING_TAG = "play-show-waiting"

/**
 * HIS-112 (owner 2026-11-12): the plays that show together now, oldest first (the order they went on the Stack): the oldest
 * moment not [ended] when it is a play, and the plays right after it put on the Stack in the same event ("in one go");
 * empty when the oldest is a combat's hold (HIS-113) or nothing waits.
 */
fun playShowGroup(moments: List<GameDevice.ViewMoment>, ended: Collection<GameDevice.ViewMoment>): List<GameDevice.PlayShow> {
    val open = moments.filter { it !in ended }
    val first = open.firstOrNull() as? GameDevice.PlayShow ?: return emptyList()
    val group = ArrayList<GameDevice.PlayShow>()
    for (m in open) { if (m is GameDevice.PlayShow && m.event == first.event) group += m else break }
    return group
}

/**
 * HIS-112 (owner 2026-11-12): where on [scene] (the table at the play's event, screen dp) [show] flies in from: an
 * ability's source card on the battlefield, in a pile or in a hand (the other player's: its card in their hand's fan), or
 * the emblem's chip by its owner's life square; a spell from the pile or hand it was cast from. Null when the table shows
 * no such place: the play then grows where it shows.
 */
fun playShowSource(scene: TableScene, play: TableBoard, show: GameDevice.PlayShow): TableBounds? {
    val card = show.card?.value
    if (show.kind == GameDevice.PlayKind.Cast) {
        val owner = card?.let { id -> play.board.cards.firstOrNull { it.id == id }?.owner } ?: show.by.value
        return when (show.from) {
            arcanum.api.Zone.Hand -> scene.items.firstOrNull { it.kind == TableItemKind.HAND && it.owner == owner }?.bounds
            arcanum.api.Zone.Graveyard -> pileOf(scene, owner, PlayZone.GRAVEYARD)
            arcanum.api.Zone.Exile -> pileOf(scene, owner, PlayZone.EXILE)
            arcanum.api.Zone.Library -> pileOf(scene, owner, PlayZone.LIBRARY)
            else -> null
        }
    }
    if (card == null) return null
    // A battlefield card (the one in sight, when its combat copy shows too), a card in a hand, a pile holding it
    scene.items.filter { it.kind == TableItemKind.CARD && it.cardId == card && !it.projection && !it.leaving &&
        (it.zone == PlayZone.BATTLEFIELD || it.zone == PlayZone.HAND) }
        .let { cards -> cards.firstOrNull { it.visible.isNotEmpty() } ?: cards.firstOrNull() }?.let { return it.bounds }
    scene.items.firstOrNull { it.kind == TableItemKind.PILE && it.zone != PlayZone.STACK && card in it.cards }?.let { return it.bounds }
    // An emblem (its record's id is "emblem:<card>"): its chip by its owner's life square (the "+N" chip for one past the chips)
    val record = play.board.engineRecords.firstOrNull { it.kind == org.asyncttwiz.protocol.EngineRecordKind.Emblem &&
        it.id.substringAfter(':') == card } ?: return null
    val owner = record.player ?: return null
    val index = play.board.engineRecords.filter { it.kind == org.asyncttwiz.protocol.EngineRecordKind.Emblem && it.player == owner }
        .sortedWith(compareBy({ it.timestamp ?: 0 }, { it.id })).indexOf(record)
    val chips = scene.items.firstOrNull { it.kind == TableItemKind.LIFE && it.owner == owner }?.emblemChips.orEmpty()
    return chips.getOrNull(minOf(index, chips.lastIndex))
}

private fun pileOf(scene: TableScene, owner: String, zone: PlayZone): TableBounds? =
    scene.items.firstOrNull { it.kind == TableItemKind.PILE && it.owner == owner && it.zone == zone && !it.temp }?.bounds

/**
 * HIS-112 (owner 2026-11-12): where a play flying in from [source] starts on the [board] (the table's visible part): the
 * source itself, or for one out of sight (a battlefield card panned away) the board's edge in its direction, at its size.
 */
fun playShowStart(board: TableBounds, source: TableBounds?): TableBounds? {
    source ?: return null
    val c = source.center
    if (board.contains(c)) return source
    val o = board.center
    val dx = c.x - o.x
    val dy = c.y - o.y
    val t = minOf(if (dx == 0f) Float.MAX_VALUE else board.width / 2 / kotlin.math.abs(dx),
        if (dy == 0f) Float.MAX_VALUE else board.height / 2 / kotlin.math.abs(dy))
    val x = o.x + dx * t
    val y = o.y + dy * t
    return TableBounds(x - source.width / 2, y - source.height / 2, source.width, source.height)
}

/**
 * HIS-112 (owner 2026-11-07, 2026-11-12; MTG Arena as the reference): the plays [shows] put on the Stack in one go
 * ([playShowGroup]) fly in from their sources on the table ([playShowSource]; one out of sight from the board's edge in its
 * direction), growing from small to large in the middle, one after another [PLAY_SHOW_STAGGER_MILLIS] apart in Stack order,
 * so they pile up there, the last on top. Then one at a time, top first: it stays large for [millis] (shorter for a repeat
 * of the same ability in its chain, [pace]: [playShowMillis]) with its line under it, then shrink-flies to its Stack card
 * (else the Stack's top card or the Stack), with an accelerating curve ([playShowFlightMillis]), its line gone as the flight
 * starts and the card fading out over the flight's second half; the next is already waiting large under it. When it lands
 * it is gone from this screen at once, and [done] (the device's dismissal, on the engine thread) runs for it. A double tap
 * on the one shown ends it at once, a single tap does nothing, and holding it inspects it ([inspect], [moved]: TBL-104,
 * TBL-131). A finger held on it or on the table pauses its time ([pause]) and holds the pile once flown in. The view holds
 * meanwhile (the device holds the later events). Only the card shown and its line take touches. Cheap per frame: the
 * flights are read while drawing only, nothing composes again.
 */
@Composable
fun PlayShowOverlay(play: TableBoard, scene: TableScene, viewer: String, shows: List<GameDevice.PlayShow>,
    names: Map<String, String>, millis: Long, pause: ViewPause = remember { ViewPause() },
    inspect: (GameDevice.PlayShow, Boolean) -> Unit = { _, _ -> },
    moved: (dx: Float, dy: Float, step: Float) -> Unit = { _, _, _ -> },
    /** Outlives these plays: a tap's double-tap time goes on after they have gone. */
    tapScope: CoroutineScope = rememberCoroutineScope(),
    /** Outlives these plays: the chain they are part of, for their pace. */
    pace: PlayShowPace = remember { PlayShowPace() }, done: (GameDevice.PlayShow) -> Unit) {
    val board = scene.world?.visibleViewport ?: return
    val first = shows.firstOrNull() ?: return
    // Fixed for the group's life: what shows, where each flies from and to (the table as it is when they begin)
    val group = remember(first) { shows.toList() }
    val shown = remember(first) { group.map { PlayShows.of(play, it, viewer, names) } }
    val sources = remember(first) { group.map { playShowStart(board, playShowSource(scene, play, it)) } }
    val targets = remember(first) { group.map { playShowTarget(scene, it) } }
    val multiplier by rememberCardMovePreferences(viewer).multiplierState()
    val height = minOf(board.height * .46f, PLAY_SHOW_MAX_HEIGHT)
    val width = height / 1.4f
    val arrive = remember(first) { group.map { Animatable(0f) } }
    val leave = remember(first) { group.map { Animatable(0f) } }
    // The play shown now (-1 while they fly in), whether its flight to the Stack began, which are gone (landed or tapped)
    var current by remember(first) { mutableIntStateOf(-1) }
    var flying by remember(first) { mutableStateOf(false) }
    val gone = remember(first) { mutableStateListOf<Int>() }
    val ended = remember(first) { BooleanArray(group.size) }
    val showing = remember(first) { arrayOfNulls<Job>(1) }
    fun end(i: Int) { if (!ended[i]) { ended[i] = true; gone += i; done(group[i]) } }
    // A double tap on the play shown: off this screen at once, the next under it shows
    fun skip(i: Int) { showing[0]?.cancel(); end(i) }
    LaunchedEffect(first) {
        val arrival = playShowFlightMillis(multiplier)
        val began = withFrameMillis { it }
        coroutineScope {
            for (i in group.indices) launch {
                if (i > 0) delay(i * PLAY_SHOW_STAGGER_MILLIS)
                arrive[i].animateTo(1f, tween(arrival, easing = FastOutSlowInEasing))
            }
        }
        val arrived = withFrameMillis { it }
        // A finger held as they arrive holds the pile there
        pause.lifted()
        for (i in group.indices.reversed()) {
            current = i
            flying = false
            val start = withFrameMillis { it }
            val (repeats, gap) = pace.begin(pace.key(shown[i]), start)
            val showFor = playShowMillis(millis, repeats)
            val flightFor = playShowFlightMillis(multiplier, repeats)
            PlayShowProbe.listener?.invoke(viewer, group[i], repeats, showFor, flightFor)
            var flown = start
            val target = targets[i]
            val run = launch {
                pause.wait(showFor)
                flown = withFrameMillis { it }
                flying = true
                if (target != null) leave[i].animateTo(1f, tween(flightFor, easing = FastOutLinearInEasing))
                // A finger held during the flight still holds the view: what comes next waits for it
                pause.lifted()
            }
            showing[0] = run
            run.join()
            showing[0] = null
            val landed = withFrameMillis { it }
            pace.landed(landed)
            // HIS-112 (owner playtest 2026-11-11): where a play's time goes, for the report: the gap before it (the device and
            // the table getting it on screen), its arrival with the others of its event, its time shown against the setting,
            // its flight
            AppLog.info("game", "Play shown ${group[i].kind::class.simpleName} in ${group[i].event.take(8)} " +
                "(${group.size - i} of ${group.size} put on the Stack together, from ${if (sources[i] == null) "nowhere on the table" else "its source"}): " +
                (if (gap == null) "first of its chain" else "$gap ms after the one before landed") +
                (if (i == group.lastIndex) ", flown in ${arrived - began} ms (${group.size} from ${PLAY_SHOW_STAGGER_MILLIS} ms apart, $arrival ms each)" else "") +
                ", shown ${flown - start} ms (setting $millis ms, repeat $repeats: $showFor ms), flight ${landed - flown} ms ($flightFor ms)" +
                (if (run.isCancelled) ", ended by a double tap" else "") + if (target == null) ", no Stack card to fly to" else "")
            end(i)
        }
    }
    val density = LocalDensity.current
    // WEB-025 (§5: no press-and-hold Inspect on desktop): on the desktop table the left button held on a play only pauses it
    val holdInspects = LocalAppLayout.current != AppLayout.Desktop
    val step = if (group.size <= 1) 0f else minOf(PLAY_SHOW_PILE_STEP, PLAY_SHOW_PILE_SPREAD / (group.size - 1))
    // The pile's middle stays the board's: each play's place in it, from the board's centre (dp)
    fun pile(i: Int): Float = (i - (group.size - 1) / 2f) * step
    Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp), contentAlignment = Alignment.Center) {
        for (i in group.indices) if (i !in gone) key(i) {
            val entry = group[i]
            val look = shown[i]
            val now = i == current
            val source = sources[i]
            val target = targets[i]
            // HIS-112 (owner, 2026-11-08): a single tap does nothing, so a skip isn't triggered by accident
            val touches = if (!now) Modifier else Modifier.choiceCardGestures(entry,
                tap = { if (pause.tapOnShow(tapScope)) skip(i) },
                hold = { holding -> pause.finger = holding; if (holdInspects) inspect(entry, holding) }, moved = moved)
            // The card's centre is the board's (and its place in the pile), so its flights move and scale it about its own
            // centre. They are read while drawing only: they move the card without composing it again
            Box(Modifier
                .graphicsLayer {
                    val a = arrive[i].value
                    if (a <= 0f) { alpha = 0f; return@graphicsLayer }
                    val home = pile(i)
                    var x = home
                    var y = home
                    var sx = 1f
                    var sy = 1f
                    var fade = 1f
                    if (a < 1f) {
                        if (source != null) {
                            x = source.center.x - board.center.x + (home - (source.center.x - board.center.x)) * a
                            y = source.center.y - board.center.y + (home - (source.center.y - board.center.y)) * a
                            val small = maxOf(source.width / width, source.height / height).coerceIn(.05f, 1f)
                            sx = small + (1f - small) * a
                            sy = sx
                        } else { sx = .2f + .8f * a; sy = sx; fade = a }
                    }
                    val l = leave[i].value
                    if (target != null && l > 0f) {
                        x = home + (target.center.x - board.center.x - home) * l
                        y = home + (target.center.y - board.center.y - home) * l
                        sx = 1f + (target.width / width - 1f) * l
                        sy = 1f + (target.height / height - 1f) * l
                        fade = playShowFlightAlpha(l)
                    }
                    translationX = x * density.density
                    translationY = y * density.density
                    scaleX = sx
                    scaleY = sy
                    alpha = fade
                }
                .testTag(if (now) PLAY_SHOW_TAG else PLAY_SHOW_WAITING_TAG)
                .semantics { contentDescription = look.description }
                .then(touches)) {
                val ability = look.ability
                when {
                    ability != null -> AbilityTokenFace(ability, look.art, look.own, Modifier.size(width.dp, height.dp), look.typeIcon)
                    look.face != null -> InspectFaceView(look.face, DpSize(width.dp, height.dp))
                    else -> Box(Modifier.size(width.dp, height.dp).cardFrame(false, if (look.own) OwnTableColor else OpponentTableColor)
                        .background(Color(0xff506b61)))
                }
            }
        }
        // The line of the play shown under its card, gone once its flight starts
        val i = current
        if (i >= 0 && i !in gone && !flying) {
            val look = shown[i]
            val entry = group[i]
            val home = pile(i)
            Surface(Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(home.dp.roundToPx(),
                            (home.dp.toPx() + height.dp.toPx() / 2 + 8.dp.toPx() + placeable.height / 2f).roundToInt())
                    }
                }
                .widthIn(max = 300.dp)
                .choiceCardGestures(entry, tap = { if (pause.tapOnShow(tapScope)) skip(i) },
                    hold = { holding -> pause.finger = holding; if (holdInspects) inspect(entry, holding) }, moved = moved),
                shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .94f), shadowElevation = 4.dp) {
                CardText(look.caption.tinted(viewer), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MenuInk,
                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center), maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** HIS-112: where the shown play [show] lands: its own card on the Stack (its place and size), else the Stack's top card,
 * else the Stack. */
fun playShowTarget(scene: TableScene, show: GameDevice.PlayShow? = null): TableBounds? {
    val pile = scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
    val entry = show?.let { "entry:${it.entry}" }
    if (entry != null) scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD && it.cardId == entry &&
        it.visible.isNotEmpty() }?.let { return it.bounds }
    return pile?.let { p -> scene.items.lastOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD && it.viewport == p.viewport } }?.bounds
        ?: pile?.bounds ?: scene.stackRegion
}

/** [standIn] for a played card without a name: a hidden one by design, a visible one a fault (reported). */
private fun unnamed(visible: Boolean, standIn: String, show: GameDevice.PlayShow): String =
    if (!visible) standIn else Faults.placeholder("PlayShows", "visible play without a name", standIn,
        mapOf("entry" to show.entry.toString(), "card" to show.card?.value.toString()))
