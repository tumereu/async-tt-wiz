package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The chat bubbles on the table (LOB-136, LOB-137): [incoming] the other player's oldest unread message, which stays until
 * tapped ([read]; holding it opens the chat at it, [open]); [sent] the viewer's own message just sent, shown for about
 * three seconds once the table is in sight ([tableShown]) and then faded ([sentShown] tells it went).
 */
data class TableChatBubbles(val incoming: IncomingChatBubble?, val sent: SentChat?, val tableShown: Boolean,
    val authorName: String, val read: (String) -> Unit, val open: (String) -> Unit, val sentShown: (Long) -> Unit)

/** A bubble placed on the table: its [bounds], its [lines] and the [counter] ("1/3") beside them. */
private data class PlacedChatBubble(val bounds: TableBounds, val lines: List<String>, val counter: String?, val life: TableBounds)

/** LOB-136, LOB-137: both players' chat bubbles by their life squares on a [width]×[height] table whose bottom bar is
 * [bottom] high; the own bubble keeps off the other player's. */
@Composable
internal fun ChatBubblesOverlay(scene: TableScene, viewer: String, bubbles: TableChatBubbles, width: Float, height: Float,
    bottom: Float) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // dp-sized text, as the table's own drawings (the layout's line heights are dp)
    val style = remember(density) { TextStyle(fontSize = with(density) { ChatBubbleLayout.TEXT.dp.toSp() }, color = BubbleInk) }
    val counterStyle = remember(density) {
        TextStyle(fontSize = with(density) { ChatBubbleLayout.COUNTER_TEXT.dp.toSp() }, color = BubbleCounter, fontWeight = FontWeight.Bold)
    }
    fun measure(text: String, s: TextStyle = style) = with(density) { measurer.measure(text, s).size.width.toDp().value }
    val incoming = bubbles.incoming
    val sent = bubbles.sent
    var sentDone by remember(sent?.key) { mutableStateOf(false) }
    val showSent = sent != null && !sentDone
    val placed = remember(scene, incoming, if (showSent) sent else null, width, height, bottom, density) {
        val opponentLife = scene.items.firstOrNull { it.kind == TableItemKind.LIFE && it.owner != viewer && it.visible.isNotEmpty() }
        val ownLife = scene.items.firstOrNull { it.kind == TableItemKind.LIFE && it.owner == viewer && it.visible.isNotEmpty() }
        val other = if (incoming != null && opponentLife != null)
            place(scene, opponentLife, below = true, incoming.text, incoming.counter, width, height, bottom, emptyList(),
                { measure(it) }, { measure(it, counterStyle) })
            else null
        val own = if (showSent && ownLife != null)
            place(scene, ownLife, below = false, sent!!.text, null, width, height, bottom, listOfNotNull(other?.bounds),
                { measure(it) }, { measure(it, counterStyle) })
            else null
        other to own
    }
    val (other, own) = placed
    if (incoming != null && other != null) {
        val read by rememberUpdatedState(bubbles.read)
        val open by rememberUpdatedState(bubbles.open)
        val description = "Chat bubble: ${bubbles.authorName}: ${incoming.text}" + (incoming.counter?.let { ", $it" } ?: "")
        val b = other.bounds
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)
            .testTag("chat-bubble").semantics {
                contentDescription = description
                onClick("Read") { read(incoming.messageId); true }
                onLongClick("Open chat") { open(incoming.messageId); true }
            }
            .pointerInput(incoming.messageId) {
                detectTapGestures(onTap = { read(incoming.messageId) }, onLongPress = { open(incoming.messageId) })
            }) {
            Canvas(Modifier.fillMaxSize()) { drawChatBubble(other, OpponentTableColor, measurer, style, counterStyle) }
        }
    }
    if (sent != null && own != null && !sentDone) {
        val alpha = remember(sent.key) { Animatable(1f) }
        val shown by rememberUpdatedState(bubbles.sentShown)
        LaunchedEffect(sent.key, bubbles.tableShown) {
            if (!bubbles.tableShown) return@LaunchedEffect
            alpha.snapTo(1f)
            delay(ChatBubbleRules.OWN_SHOWN_MILLIS)
            alpha.animateTo(0f, tween(ChatBubbleRules.OWN_FADE_MILLIS))
            sentDone = true
            shown(sent.key)
        }
        val b = own.bounds
        // Drawn only: taps reach the table beneath
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)
            .graphicsLayer { this.alpha = alpha.value }.testTag("own-chat-bubble")
            .semantics { contentDescription = "Your chat bubble: ${sent.text}" }) {
            Canvas(Modifier.fillMaxSize()) { drawChatBubble(own, OwnTableColor, measurer, style, counterStyle) }
        }
    }
}

/** [text] as a bubble by [life] (and its [counter]): four lines when there is room for them, else one; null where not
 * even one line has free room. */
private fun place(scene: TableScene, life: TableItem, below: Boolean, text: String, counter: String?, width: Float, height: Float,
    bottom: Float, more: List<TableBounds>, measureText: (String) -> Float, measureCounter: (String) -> Float): PlacedChatBubble? {
    val obstacles = ChatBubbleLayout.obstacles(scene, life.id, width, height, bottom).let { it.copy(items = it.items + more) }
    val counterWidth = counter?.let(measureCounter) ?: 0f
    val room = (minOf(ChatBubbleLayout.MAX_WIDTH, width - 8f) - 2 * ChatBubbleLayout.PADDING -
        (if (counter != null) counterWidth + ChatBubbleLayout.COUNTER_GAP else 0f)).coerceAtLeast(12f)
    for (maxLines in listOf(ChatBubbleLayout.MAX_LINES, 1)) {
        val lines = ChatBubbleLayout.lines(text, room, maxLines, measureText)
        val (w, h) = ChatBubbleLayout.size(life.bounds, lines.maxOf(measureText), lines.size, counterWidth)
        ChatBubbleLayout.place(life.bounds, below, w, h, width, height, obstacles)?.let { return PlacedChatBubble(it, lines, counter, life.bounds) }
    }
    return null
}

private val BubbleInk = Color(0xff202a2e)
private val BubbleCounter = Color(0xff667b89)

/** The old status bubble's look (TRN-039): white, outlined and tailed in the player's [color], each line centred. */
private fun DrawScope.drawChatBubble(bubble: PlacedChatBubble, color: Color, measurer: TextMeasurer, style: TextStyle,
    counterStyle: TextStyle) {
    val d = density
    val b = bubble.bounds
    val tail = ChatBubbleLayout.tail(b, bubble.life)
    val t = ChatBubbleLayout.TAIL * d
    // The tail's base on the bubble's edge, in this canvas (the bubble's own bounds)
    val bx = (tail.x - b.x) * d; val by = (tail.y - b.y) * d
    val path = Path().apply {
        when (tail.side) {
            ChatBubbleLayout.TailSide.TOP -> { moveTo(bx - 5 * d, by + .5f * d); lineTo(bx, by - t); lineTo(bx + 5 * d, by + .5f * d) }
            ChatBubbleLayout.TailSide.BOTTOM -> { moveTo(bx - 5 * d, by - .5f * d); lineTo(bx, by + t); lineTo(bx + 5 * d, by - .5f * d) }
            ChatBubbleLayout.TailSide.LEFT -> { moveTo(bx + .5f * d, by - 5 * d); lineTo(bx - t, by); lineTo(bx + .5f * d, by + 5 * d) }
            ChatBubbleLayout.TailSide.RIGHT -> { moveTo(bx - .5f * d, by - 5 * d); lineTo(bx + t, by); lineTo(bx - .5f * d, by + 5 * d) }
        }
        close()
    }
    val corner = CornerRadius(6 * d)
    drawRoundRect(Color.White, Offset.Zero, size, corner)
    drawPath(path, Color.White)
    drawRoundRect(color, Offset.Zero, size, corner, style = Stroke(d))
    drawPath(path, color, style = Stroke(d))
    // Hide the outline where the tail joins the bubble
    when (tail.side) {
        ChatBubbleLayout.TailSide.TOP, ChatBubbleLayout.TailSide.BOTTOM ->
            drawLine(Color.White, Offset(bx - 4 * d, by), Offset(bx + 4 * d, by), 1.5f * d)
        else -> drawLine(Color.White, Offset(bx, by - 4 * d), Offset(bx, by + 4 * d), 1.5f * d)
    }
    val counter = bubble.counter?.let { measurer.measure(it, counterStyle) }
    val textRight = size.width - ChatBubbleLayout.PADDING * d - (counter?.let { it.size.width + ChatBubbleLayout.COUNTER_GAP * d } ?: 0f)
    val textLeft = ChatBubbleLayout.PADDING * d
    bubble.lines.forEachIndexed { n, line ->
        val measured = measurer.measure(line, style)
        val x = textLeft + ((textRight - textLeft) - measured.size.width) / 2
        val y = (ChatBubbleLayout.HEIGHT / 2 + n * ChatBubbleLayout.LINE) * d - measured.size.height / 2f
        drawText(measured, topLeft = Offset(x, y))
    }
    counter?.let { drawText(it, topLeft = Offset(size.width - ChatBubbleLayout.PADDING * d - it.size.width,
        (size.height - it.size.height) / 2f)) }
}
