package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.storage.ArtworkReference
import kotlin.math.hypot

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
                val face = if (visible && definition != null) inspectFaces(InspectedCard.retained(card.definition, definition), faceIndex,
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

/**
 * HIS-112 (owner 2026-11-07, MTG Arena as the reference): [show] large in the middle of the board for [millis], then it
 * flies to the Stack at the card move speed and is [done]; a tap on it ends it at once. The view holds meanwhile (the
 * device holds the later events). Only the card and its line take touches.
 */
@Composable
internal fun PlayShowOverlay(play: TableBoard, scene: TableScene, viewer: String, show: GameDevice.PlayShow,
    names: Map<String, String>, millis: Long, done: () -> Unit) {
    val board = scene.world?.visibleViewport ?: return
    val shown = remember(show) { PlayShows.of(play, show, viewer, names) }
    val multiplier by rememberCardMovePreferences(viewer).multiplierState()
    val height = minOf(board.height * .46f, PLAY_SHOW_MAX_HEIGHT)
    val width = height / 1.4f
    val stack = scene.stackRegion ?: scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.bounds
    val flight = remember(show) { Animatable(0f) }
    val ended = remember(show) { booleanArrayOf(false) }
    fun end() { if (!ended[0]) { ended[0] = true; done() } }
    LaunchedEffect(show) {
        if (millis > 0) delay(millis)
        if (stack != null) {
            val distance = hypot(stack.center.x - board.center.x, stack.center.y - board.center.y)
            val speed = DEFAULT_CARD_MOVE_SPEED * cardMoveMultiplier(multiplier)
            flight.animateTo(1f, tween(cardMoveDurationMillis(distance, speed), easing = FastOutSlowInEasing))
        }
        end()
    }
    val density = LocalDensity.current
    Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp), contentAlignment = Alignment.Center) {
        // The flight is read while drawing only: it moves the card without composing it again
        Column(Modifier
            .graphicsLayer {
                val p = flight.value
                if (stack != null) {
                    translationX = (stack.center.x - board.center.x) * p * density.density
                    translationY = (stack.center.y - board.center.y) * p * density.density
                    val target = (stack.height / height).coerceIn(.05f, 1f)
                    scaleX = 1f + (target - 1f) * p
                    scaleY = scaleX
                }
            }
            .testTag(PLAY_SHOW_TAG)
            .semantics { contentDescription = shown.description }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Skip") { end() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ability = shown.ability
            when {
                ability != null -> AbilityTokenFace(ability, shown.art, shown.own, Modifier.size(width.dp, height.dp), shown.typeIcon)
                shown.face != null -> InspectFaceView(shown.face, DpSize(width.dp, height.dp))
                else -> Box(Modifier.size(width.dp, height.dp).cardFrame(false, if (shown.own) OwnTableColor else OpponentTableColor)
                    .background(Color(0xff506b61)))
            }
            Surface(Modifier.widthIn(max = 300.dp).graphicsLayer { alpha = (1f - flight.value * 3f).coerceIn(0f, 1f) },
                shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .94f), shadowElevation = 4.dp) {
                CardText(shown.caption.tinted(viewer), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MenuInk,
                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center), maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
