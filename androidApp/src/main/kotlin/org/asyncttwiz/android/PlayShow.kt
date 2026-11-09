package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.storage.ArtworkReference
import kotlin.math.roundToInt

/** The test tag of the play shown in the middle of the table (HIS-112). */
internal const val PLAY_SHOW_TAG = "play-show"

/**
 * HIS-112: what the table shows of a play: [description] says it in words (its content description), [caption] the line
 * under it (as the Stack line names it, STK-057); a spell as its card [face] ([covered]: a face-down spell, a card back),
 * an ability as its token ([ability], with its source's [art] or [typeIcon]); [own] colours its outline.
 */
internal data class PlayShown(val description: String, val caption: List<StackTextPiece>, val own: Boolean,
    val face: InspectTile.Face? = null, val covered: Boolean = false, val ability: StackAbilityShown? = null,
    val art: ArtworkReference? = null, val typeIcon: String = "")

internal object PlayShows {
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
                val caption = line ?: listOf(StackTextPiece("$who cast ${name ?: "a card"}"))
                val face = if (visible && definition != null) inspectFaces(InspectedCard.retained(definition), faceIndex,
                    flippedOnTable(definition, card.faceId), "play-show/${card.id}",
                    if (own) OwnTableColor else OpponentTableColor, card.token).firstOrNull() else null
                PlayShown(caption.plain(), caption, own, face = face, covered = face == null)
            }
            GameDevice.PlayKind.Activated, GameDevice.PlayKind.Triggered -> {
                val triggered = show.kind == GameDevice.PlayKind.Triggered
                val ability = effect?.let { StackAbilities.of(play, entry, viewer) }
                    ?: StackAbilityShown(triggered, name ?: "Ability", "")
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
internal const val PLAY_SHOW_FLIGHT_MILLIS = 250

/** HIS-112: how long the flight to the Stack takes at the Card move speed [multiplier] (TBL-038). */
internal fun playShowFlightMillis(multiplier: Float): Int =
    (PLAY_SHOW_FLIGHT_MILLIS / cardMoveMultiplier(multiplier)).roundToInt().coerceAtLeast(1)

/** HIS-112: the shown card's opacity at [progress] of its flight: whole over the first half, fading out over the second. */
internal fun playShowFlightAlpha(progress: Float): Float = (2f - 2f * progress).coerceIn(0f, 1f)

/**
 * HIS-112 (owner 2026-11-07, MTG Arena as the reference): [show] large in the middle of the board for [millis], then it
 * flies to the Stack's top card, taking its place and size, with an accelerating curve ([playShowFlightMillis]); its line
 * goes as the flight starts and the card fades out over the flight's second half. When it lands it is gone from this
 * screen at once, and [done] (the device's dismissal, on the engine thread) runs. A double tap on it ends it at once, a
 * single tap does nothing, and holding it inspects it ([inspect], [moved]: TBL-104, TBL-131). A finger held on it or on the
 * table pauses its time ([pause]). The view holds meanwhile (the device holds the later events). Only the card and its line
 * take touches.
 */
@Composable
internal fun PlayShowOverlay(play: TableBoard, scene: TableScene, viewer: String, show: GameDevice.PlayShow,
    names: Map<String, String>, millis: Long, pause: ViewPause = remember { ViewPause() }, inspect: (Boolean) -> Unit = {},
    moved: (dx: Float, dy: Float, step: Float) -> Unit = { _, _, _ -> }, done: () -> Unit) {
    val board = scene.world?.visibleViewport ?: return
    val shown = remember(show) { PlayShows.of(play, show, viewer, names) }
    val multiplier by rememberCardMovePreferences(viewer).multiplierState()
    val height = minOf(board.height * .46f, PLAY_SHOW_MAX_HEIGHT)
    val width = height / 1.4f
    val target = playShowTarget(scene)
    val flight = remember(show) { Animatable(0f) }
    var flying by remember(show) { mutableStateOf(false) }
    // Landed or tapped: off this screen at once, whenever the device gets to its dismissal
    var gone by remember(show) { mutableStateOf(false) }
    val ended = remember(show) { booleanArrayOf(false) }
    fun end() { gone = true; if (!ended[0]) { ended[0] = true; done() } }
    LaunchedEffect(show) {
        pause.wait(millis)
        flying = true
        if (target != null) flight.animateTo(1f, tween(playShowFlightMillis(multiplier), easing = FastOutLinearInEasing))
        // A finger held during the flight still holds the view: what comes next waits for it
        pause.lifted()
        end()
    }
    if (gone) return
    val density = LocalDensity.current
    // HIS-112 (owner, 2026-11-08): a single tap does nothing, so a skip isn't triggered by accident
    val scope = rememberCoroutineScope()
    val firstTap = remember(show) { arrayOfNulls<Job>(1) }
    val touches = Modifier.choiceCardGestures(show, tap = {
        if (firstTap[0]?.isActive == true) end() else firstTap[0] = scope.launch { delay(DOUBLE_TAP_MILLIS) }
    }, hold = { holding -> pause.finger = holding; inspect(holding) }, moved = moved)
    Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp), contentAlignment = Alignment.Center) {
        // The card's centre is the board's, so the flight moves and scales it about its own centre. The flight is read
        // while drawing only: it moves the card without composing it again
        Box(Modifier
            .graphicsLayer {
                val p = flight.value
                if (target != null) {
                    translationX = (target.center.x - board.center.x) * p * density.density
                    translationY = (target.center.y - board.center.y) * p * density.density
                    scaleX = 1f + (target.width / width - 1f) * p
                    scaleY = 1f + (target.height / height - 1f) * p
                    alpha = playShowFlightAlpha(p)
                }
            }
            .testTag(PLAY_SHOW_TAG)
            .semantics { contentDescription = shown.description }
            .then(touches)) {
            val ability = shown.ability
            when {
                ability != null -> AbilityTokenFace(ability, shown.art, shown.own, Modifier.size(width.dp, height.dp), shown.typeIcon)
                shown.face != null -> InspectFaceView(shown.face, DpSize(width.dp, height.dp))
                else -> Box(Modifier.size(width.dp, height.dp).cardFrame(false, if (shown.own) OwnTableColor else OpponentTableColor)
                    .background(Color(0xff506b61)))
            }
        }
        // Its line under the card, gone once the flight starts
        if (!flying) Surface(Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, (height.dp.toPx() / 2 + 8.dp.toPx() + placeable.height / 2f).roundToInt())
                }
            }
            .widthIn(max = 300.dp)
            .then(touches),
            shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .94f), shadowElevation = 4.dp) {
            CardText(shown.caption.tinted(viewer), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MenuInk,
                style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center), maxLines = 3,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** HIS-112: where the shown play lands: the Stack's top card (its place and size), else the Stack. */
internal fun playShowTarget(scene: TableScene): TableBounds? {
    val pile = scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
    return pile?.let { p -> scene.items.lastOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD && it.viewport == p.viewport } }?.bounds
        ?: pile?.bounds ?: scene.stackRegion
}
