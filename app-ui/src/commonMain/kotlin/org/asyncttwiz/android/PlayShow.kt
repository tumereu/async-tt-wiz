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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.flow.first
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
    val art: ArtworkReference? = null, val typeIcon: String = "",
    /** D6, D7: a copy of a spell put on the Stack, drawn with the copy marker (a copied ability's token carries its own). */
    val copy: Boolean = false)

object PlayShows {
    /** What [viewer]'s table shows of [show] on [play] (the board at its event); [names]: the players' names by account;
     * [after]: the board once the play is done (a special action's card as it shows it: a card it turned face up named). */
    fun of(play: TableBoard, show: GameDevice.PlayShow, viewer: String, names: Map<String, String>, after: TableBoard? = null): PlayShown {
        if (show.kind == GameDevice.PlayKind.Special) return special(after ?: play, show, viewer, names)
        val entry = "entry:${show.entry}"
        val effect = play.board.stackEffects.firstOrNull { it.id == entry }
        val own = show.by.value == viewer
        val who = if (own) "You" else org.asyncttwiz.automatum.PlayerNames.of(names, show.by.value, "The other player", "PlayShows")
        val line = effect?.let { StackLine.entryPieces(play, entry, viewer, names) }
        val cardId = show.card?.value
        val card = (if (effect != null) CombatStackRules.inspection(play.board, entry) else null)
            ?: cardId?.takeIf { show.kind != GameDevice.PlayKind.Copied }?.let { id -> play.board.cards.firstOrNull { it.id == id } }
        val visible = card != null && effect?.faceDown != true &&
            (effect?.frozen != null || CardVisibility.isVisibleFaceUp(play.board, card, viewer))
        val definition = card?.let { c -> play.definitions.firstOrNull { it.digest == c.definition }?.definition }
        val faceIndex = definition?.faces?.indexOfFirst { it.faceId == card.faceId }?.takeIf { it >= 0 } ?: 0
        val name = definition?.faces?.getOrNull(faceIndex)?.shownName?.takeIf { visible && it.isNotBlank() }
        // D6 (owner 2026-11-19): a copy put on the Stack shows as the copied spell's card or the copied ability's token, its line
        // naming it a copy; it flies in from what copied it ([show]'s card), so its own card is its entry's
        val copied = show.kind == GameDevice.PlayKind.Copied
        val spellCopy = copied && effect?.item?.kind == org.asyncttwiz.protocol.MtgStackKind.SpellCopy
        return when (show.kind) {
            GameDevice.PlayKind.Copied -> if (spellCopy) {
                val caption = line ?: listOf(StackTextPiece("Copy of ${name ?: unnamed(visible, "a spell", show)}"))
                val face = if (visible && definition != null) inspectFaces(InspectedCard.retained(definition), faceIndex,
                    flippedOnTable(definition, card.faceId), "play-show/${card.id}",
                    if (own) OwnTableColor else OpponentTableColor, card.token).firstOrNull()?.copy(oracleName = null) else null
                PlayShown(caption.plain(), caption, own, face = face, covered = face == null, copy = true)
            } else {
                val ability = effect?.let { StackAbilities.of(play, entry, viewer) }
                    ?: StackAbilityShown(true, name ?: unnamed(visible, "Ability", show), "", copy = true)
                val source = choiceCard(play, viewer, "play-show/source", effect?.sourceId, visible, ability.source)
                val caption = line ?: listOf(StackTextPiece(ability.name))
                PlayShown(caption.plain(), caption, own, ability = ability, art = source.art, typeIcon = source.typeIcon, copy = true)
            }
            GameDevice.PlayKind.Cast -> {
                val caption = line ?: listOf(StackTextPiece("$who cast ${name ?: unnamed(visible, "a card", show)}"))
                // TBL-138: a card printed under another name has its Oracle name labelled in Inspect and the quick look only
                val face = if (visible && definition != null) inspectFaces(InspectedCard.retained(definition), faceIndex,
                    flippedOnTable(definition, card.faceId), "play-show/${card.id}",
                    if (own) OwnTableColor else OpponentTableColor, card.token).firstOrNull()?.copy(oracleName = null) else null
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
            GameDevice.PlayKind.Special -> special(play, show, viewer, names)
        }
    }

    /**
     * HIS-112 (owner, 2026-11-20): a special action (CR 116.2) shows as an ability does, its card's ability token with the
     * source's art, its kind "Special action" and its words as an ability reads (TBL-115: "{3}{R}: Unlock Dusty Attic"); its
     * line says what it did as the event text does (HIS-091: "Bob unlocked Dusty Attic", "Bob foretold a card"). [play] is
     * the board once it is done.
     */
    private fun special(play: TableBoard, show: GameDevice.PlayShow, viewer: String, names: Map<String, String>): PlayShown {
        val own = show.by.value == viewer
        val who = if (own) "You" else org.asyncttwiz.automatum.PlayerNames.of(names, show.by.value, "The other player", "PlayShows")
        val cardId = show.card?.value
        val card = cardId?.let { id -> play.board.cards.firstOrNull { it.id == id } }
        val visible = card != null && CardVisibility.isVisibleFaceUp(play.board, card, viewer)
        val definition = card?.let { c -> play.definitions.firstOrNull { it.digest == c.definition }?.definition }
        val name = definition?.faces?.let { faces -> faces.firstOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }
            ?.shownName?.takeIf { visible && it.isNotBlank() }
        val rules = cardId?.let { id -> play.engine?.state?.objects?.values?.lastOrNull { it.card.value == id }?.definition }
        val origin = show.ability
        val done = origin?.let { org.asyncttwiz.automatum.CastFlow.specialActionDone(rules, it, name) }
        val words = origin?.let { org.asyncttwiz.automatum.CastFlow.specialActionText(rules, it) }.orEmpty()
        val line = if (done != null) "$who $done" else Faults.placeholder("PlayShows", "special action without its words",
            "$who used ${name ?: "a card"}", mapOf("entry" to show.entry.toString(), "card" to cardId.toString(), "ability" to origin.toString()))
        val ability = StackAbilityShown(false, name ?: "A card", words, kind = "Special action")
        val source = choiceCard(play, viewer, "play-show/source", cardId, visible, ability.source)
        val caption = listOf(StackTextPiece(line))
        return PlayShown(line, caption, own, ability = ability, art = source.art, typeIcon = source.typeIcon)
    }
}

/** HIS-112: the shown card's height: about half the board's, at most this. */
private const val PLAY_SHOW_MAX_HEIGHT = 300f

/** HIS-112, WEB-015 (owner playtest 2026-11-15): on the desktop table the shown card is this much larger, at most this share of
 * the battlefields' height above what floats over their bottom, so its line fits under it. */
private const val DESKTOP_PLAY_SHOW = 1.5f
private const val DESKTOP_PLAY_SHOW_SHARE = .74f

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

    /** How many times [key] showed before in the chain a play beginning at [now] would continue (nothing changed). */
    fun peek(key: Any?, now: Long): Int {
        if (key == null || landedAt?.let { now - it <= PLAY_SHOW_CHAIN_GAP_MILLIS } != true) return 0
        return seen[key] ?: 0
    }

    /** The play shown landed at [now] (a play taken back or ended by timeline mode never lands: the next begins a chain). */
    fun landed(now: Long) { landedAt = now }

    /** What makes plays repeats of one another ([PlayShown.ability]: an ability's source name and text); null for a spell. */
    fun key(shown: PlayShown): Any? = shown.ability
}

/** Tests: each play shown as its time starts on [me]'s table, with the repeats before it in its chain and its times. */
object PlayShowProbe {
    @Volatile var listener: ((me: String, show: GameDevice.PlayShow, repeats: Int, millis: Long, flightMillis: Int) -> Unit)? = null
    /** Each play shown's flight out of the middle as it starts on [me]'s table: where it flies to (the Stack, or a special
     * action's own card; null: nowhere, it ends where it is) on its [scene]. */
    @Volatile var flight: ((me: String, show: GameDevice.PlayShow, target: TableBounds?, scene: TableScene) -> Unit)? = null
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
    // HIS-112 (owner, 2026-11-20): a special action shows by itself, before what it puts on the Stack (its triggers)
    if (first.kind == GameDevice.PlayKind.Special) return listOf(first)
    val group = ArrayList<GameDevice.PlayShow>()
    for (m in open) { if (m is GameDevice.PlayShow && m.event == first.event && m.kind != GameDevice.PlayKind.Special) group += m else break }
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
    // HIS-112 (owner, 2026-11-20): a special action taken from the other player's hand (foretell, plot, suspend): their hand's fan
    if (show.kind == GameDevice.PlayKind.Special) play.board.cards.firstOrNull { it.id == card }?.let { c ->
        val hand = play.board.containers.firstOrNull { it.id == c.containerId }?.takeIf { it.zone == PlayZone.HAND }
        if (hand != null) scene.items.firstOrNull { it.kind == TableItemKind.HAND && it.owner == (hand.accountId ?: c.owner) }?.let { return it.bounds }
    }
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

/** HIS-112: what a group of plays shown looks like and where each flies in from, fixed as they begin. */
private class PlayShowLooks(val shown: List<PlayShown>, val sources: List<TableBounds?>)

/**
 * HIS-112 (owner 2026-11-07, 2026-11-12, 2026-11-14; MTG Arena as the reference): steps 1–3 of the Stack animation
 * ([StackSchedule]). Once the table is [ready] for them (the board drawn where they went on the Stack, nothing else of the
 * Stack animation playing), the plays [shows] put on the Stack in one go ([playShowGroup]) fly in from their sources on the
 * table ([playShowSource]; one out of sight from the board's edge in its direction), growing from small to large in the
 * middle, one after another [PLAY_SHOW_STAGGER_MILLIS] apart in Stack order, so they pile up there, the last on top. Then one
 * at a time, top first: it stays large for [millis] (shorter for a repeat of the same ability in its chain, [pace]:
 * [playShowMillis]) with its line under it, then shrink-flies into its entry's place ([playShowTarget]: the Stack's top, on
 * the phone and the desktop table alike), with an accelerating curve ([playShowFlightMillis]), its line
 * gone as the flight starts and the card fading out over the flight's second half; the next is already waiting large under
 * it. Until it lands its entry isn't on the table's board ([withoutEntries]); when it lands it is gone from this screen at
 * once and [done] runs for it (its entry shows in its place, and the device's dismissal follows). A tap on the one shown
 * does nothing (the main button skips it, TRN-165), and holding it inspects it ([inspect], [moved]: TBL-104, TBL-131). A
 * finger held on it or on the table pauses its time ([pause]) and holds the pile once flown in. The view holds meanwhile (the
 * device holds the later events). Only the card shown and its line take touches. Cheap per frame: the flights are read while
 * drawing only, nothing composes again. [play] is the board with the plays' entries on it.
 */
@Composable
fun PlayShowOverlay(play: TableBoard, scene: TableScene, viewer: String, shows: List<GameDevice.PlayShow>,
    names: Map<String, String>, millis: Long, pause: ViewPause = remember { ViewPause() },
    inspect: (GameDevice.PlayShow, Boolean) -> Unit = { _, _ -> },
    moved: (dx: Float, dy: Float, step: Float) -> Unit = { _, _, _ -> },
    /** Outlives these plays: the chain they are part of, for their pace. */
    pace: PlayShowPace = remember { PlayShowPace() },
    /** Whether the table is ready for these plays (read as snapshot state). */
    ready: () -> Boolean = { true },
    /** The board once these plays are done (a special action's card as it then shows), or null. */
    after: () -> TableBoard? = { null }, done: (GameDevice.PlayShow) -> Unit) {
    // WEB-015: on the desktop table the battlefields above what floats over their bottom
    val board = scene.boardArea ?: return
    val first = shows.firstOrNull() ?: return
    // Fixed for the group's life: what shows, where each flies from and to (the table as it is when they begin)
    val group = remember(first) { shows.toList() }
    val currentPlay by rememberUpdatedState(play)
    val currentScene by rememberUpdatedState(scene)
    fun looksNow(): PlayShowLooks {
        val p = currentPlay
        val s = currentScene
        val area = s.boardArea ?: board
        val done = after()
        return PlayShowLooks(group.map { PlayShows.of(p, it, viewer, names, done) }, group.map { playShowStart(area, playShowSource(s, p, it)) })
    }
    // Made as they begin: at once when the table is ready for them, else once it is
    var looks by remember(first) { mutableStateOf(if (ready()) looksNow() else null) }
    // Where each flies to, worked out as its flight starts (the Stack where it is then)
    val targets = remember(first) { arrayOfNulls<TableBounds>(group.size) }
    // Where this overlay's board lies on the screen (root dp), for the dock's place
    val boxOrigin = remember(first) { FloatArray(2) }
    val multiplier by rememberCardMovePreferences(viewer).multiplierState()
    // HIS-112, WEB-015 (owner playtest 2026-11-15: "could be a little bit bigger than it is now on desktop"): about 1.5× on the
    // desktop table, its line still under it on the battlefields
    val height = if (scene.desktop) minOf(board.height * DESKTOP_PLAY_SHOW_SHARE, PLAY_SHOW_MAX_HEIGHT * DESKTOP_PLAY_SHOW)
        else minOf(board.height * .46f, PLAY_SHOW_MAX_HEIGHT)
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
    // WEB-067: the frame times name the plays shown among what animates while they show
    DisposableEffect(first) {
        DebugJank.animation("play shown", true)
        onDispose { DebugJank.animation("play shown", false) }
    }
    LaunchedEffect(first) {
        // The board drawn where they went on the Stack, and nothing else of the Stack animation playing (an entry's arrows)
        if (looks == null) {
            val asked = withFrameMillis { it }
            snapshotFlow { ready() }.first { it }
            looks = looksNow()
            AppLog.info("game", "Plays shown in ${group.first().event.take(8)}: waited ${withFrameMillis { it } - asked} ms for the " +
                "table to reach them (the board's steps, an entry's arrows)")
        }
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
        val shown = looks?.shown ?: return@LaunchedEffect
        val sources = looks?.sources ?: return@LaunchedEffect
        for (i in group.indices.reversed()) {
            current = i
            flying = false
            val start = withFrameMillis { it }
            val (repeats, gap) = pace.begin(pace.key(shown[i]), start)
            val showFor = playShowMillis(millis, repeats)
            val flightFor = playShowFlightMillis(multiplier, repeats)
            PlayShowProbe.listener?.invoke(viewer, group[i], repeats, showFor, flightFor)
            var flown = start
            var target: TableBounds? = null
            val run = launch {
                pause.wait(showFor)
                flown = withFrameMillis { it }
                // Step 3 into its entry's place: the Stack (STK-007; on the desktop table too, WEB-017); a special action back
                // into its own card (owner, 2026-11-20)
                target = if (group[i].kind == GameDevice.PlayKind.Special)
                    playShowStart(currentScene.boardArea ?: board, playShowSource(currentScene, currentPlay, group[i]))
                    else playShowTarget(currentScene, group[i])
                targets[i] = target
                PlayShowProbe.flight?.invoke(viewer, group[i], target, currentScene)
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
            // HIS-112 (owner playtest 2026-11-11; Stack animation spec 2026-11-14: each step's start and length): where a play's
            // time goes, for the report: the gap before it (the device and the table getting it on screen), step 1 (its
            // fly-in with the others of its event), step 2 (its middle hold against the setting), step 3 (its flight to the
            // Stack), each from the group's start
            AppLog.info("game", "Play shown ${group[i].kind::class.simpleName} ${group[i].entry} in ${group[i].event.take(8)} " +
                "(${group.size - i} of ${group.size} put on the Stack together, from ${if (sources[i] == null) "nowhere on the table" else "its source"}" +
"): " +
                (if (gap == null) "first of its chain" else "$gap ms after the one before landed") +
                "; step 1 (fly-in) at 0 ms for ${arrived - began} ms (${group.size} from $PLAY_SHOW_STAGGER_MILLIS ms apart, $arrival ms each)" +
                "; step 2 (middle hold) at ${start - began} ms for ${flown - start} ms (setting $millis ms, repeat $repeats: $showFor ms)" +
                "; step 3 (${if (group[i].kind == GameDevice.PlayKind.Special) "into its own card ${group[i].card?.value}" else "to the Stack"}) " +
                "at ${flown - began} ms for ${landed - flown} ms ($flightFor ms)" +
                (if (target == null) ", no place to fly to" else ""))
            end(i)
        }
    }
    val density = LocalDensity.current
    // WEB-025 (§5: no press-and-hold Inspect on desktop): on the desktop table the left button held on a play only pauses it
    val holdInspects = LocalAppLayout.current != AppLayout.Desktop
    val step = if (group.size <= 1) 0f else minOf(PLAY_SHOW_PILE_STEP, PLAY_SHOW_PILE_SPREAD / (group.size - 1))
    // The pile's middle stays the board's: each play's place in it, from the board's centre (dp)
    fun pile(i: Int): Float = (i - (group.size - 1) / 2f) * step
    Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp)
        .onGloballyPositioned { c -> c.positionInRoot().let { boxOrigin[0] = it.x / density.density; boxOrigin[1] = it.y / density.density } },
        contentAlignment = Alignment.Center) {
        val shown = looks?.shown ?: return@Box
        val sources = looks?.sources ?: return@Box
        for (i in group.indices) if (i !in gone) key(i) {
            val entry = group[i]
            val look = shown[i]
            val now = i == current
            val source = sources[i]
            // HIS-139 (owner 2026-11-19): a tap does nothing (the main button skips it, TRN-165)
            val touches = if (!now) Modifier else Modifier.choiceCardGestures(entry,
                tap = {},
                hold = { holding -> pause.finger = holding; if (holdInspects) inspect(entry, holding) }, moved = moved,
                // HIS-126 (owner, 2026-11-26): a finger down on it pauses it at once (not on the desktop table, WEB-025)
                touched = { down -> if (holdInspects) pause.touch = down })
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
                    val target = targets[i]
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
                .then(touches)
                // WEB-024: on the desktop table the finger key over the play shown inspects it, as holding it does on the phone
                .then(if (now) Modifier.fingerTarget(if (entry.kind == GameDevice.PlayKind.Special) entry.card?.value.orEmpty() else "entry:${entry.entry}") else Modifier)) {
                val ability = look.ability
                when {
                    ability != null -> AbilityTokenFace(ability, look.art, look.own, Modifier.size(width.dp, height.dp), look.typeIcon)
                    look.face != null -> Box {
                        InspectFaceView(look.face, DpSize(width.dp, height.dp))
                        if (look.copy) CopyMarker((height * .12f).dp, Modifier.align(Alignment.TopEnd))
                    }
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
                .choiceCardGestures(entry, tap = {},
                    hold = { holding -> pause.finger = holding; if (holdInspects) inspect(entry, holding) }, moved = moved,
                // HIS-126 (owner, 2026-11-26): a finger down on it pauses it at once (not on the desktop table, WEB-025)
                touched = { down -> if (holdInspects) pause.touch = down }),
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
