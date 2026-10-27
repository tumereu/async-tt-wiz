package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

data class OccurrenceDrawing(val viewId: String, val card: PlayCard, val bounds: TableBounds,
    val projection: Boolean, val frozen: Boolean = false, val textMarks: Set<Int> = emptySet())
data class CombatDropRegion(val label: String, val bounds: TableBounds, val destination: CombatDestination,
    val extendLeft: Boolean = false, val extendRight: Boolean = false, val landing: TablePoint = bounds.center) {
    fun contains(point: TablePoint) = point.y >= bounds.y && point.y < bounds.bottom &&
        (extendLeft || point.x >= bounds.x) && (extendRight || point.x < bounds.right)
}
data class CombatGeometry(val width: Float, val height: Float, val cards: List<OccurrenceDrawing>,
    val dividers: List<Float>, val destinations: List<CombatDropRegion>,
    val lanes: List<CombatLaneLabel> = emptyList(), val laneDividers: List<Float> = emptyList(), val labelHeight: Float = 0f)
data class CombatLaneLabel(val cardId: String?, val label: String, val lines: List<String>, val bounds: TableBounds,
    val artwork: org.asyncttwiz.storage.ArtworkReference? = null)
data class StackGeometry(val width: Float, val height: Float, val count: Int,
    val visible: List<OccurrenceDrawing>, val orderedIds: List<String>)

/** dp scene geometry; the caller passes the exact current ordinary-table card dimensions. */
object CombatStackPresentation {
    fun cardGap(cardWidth: Float) = cardWidth * .04f
    // The ordinary card outline is 1.5dp at 28dp card width; leave two outlines each side.
    fun slotMargin(cardWidth: Float) = cardWidth * 3f / 28f
    fun rowWidth(count: Int, cardWidth: Float) = count * cardWidth + cardGap(cardWidth) * (count - 1).coerceAtLeast(0)
    fun slotWidth(blockers: Int, attackers: Int, cardWidth: Float) =
        maxOf(rowWidth(blockers, cardWidth), rowWidth(attackers, cardWidth)) + 2 * slotMargin(cardWidth)

    fun combat(board: BoardState, cardWidth: Float, cardHeight: Float,
        definitions: List<SubmittedDefinition> = emptyList(), names: Map<String, String> = emptyMap(), viewer: String = ""): CombatGeometry {
        require(cardWidth > 0 && cardHeight >= cardWidth)
        val area = board.combat ?: return CombatGeometry(0f, 0f, emptyList(), emptyList(), emptyList())
        val margin = cardGap(cardWidth)
        val padding = slotMargin(cardWidth)
        val rowHeight = cardHeight + margin * 2
        val cards = mutableListOf<OccurrenceDrawing>()
        val targets = mutableListOf<CombatDropRegion>()
        val dividers = mutableListOf<Float>()
        val laneDividers = mutableListOf<Float>()
        val groups = area.pairs.groupBy { it.attackTarget }
        val pairs = groups.values.flatten()
        val lanes = mutableListOf<CombatLaneLabel>()
        val font = cardWidth * .22f
        var laneX = 0f
        for ((target, members) in groups.ifEmpty { mapOf(null to emptyList()) }) {
            val width = members.sumOf { slotWidth(it.blockers.size, it.attackers.size, cardWidth).toDouble() }.toFloat()
                .coerceAtLeast(cardWidth + 2 * padding)
            val targetCard = board.cards.find { it.id == target }
            val hidden = targetCard != null && (targetCard.isFaceDown || !CardVisibility.isVisibleFaceUp(board, targetCard, viewer))
            val label = when {
                target == null -> "→ ${names[CombatStackRules.defendingPlayer(board)] ?: "Player"}"
                hidden -> "→ Face-down card"
                else -> definitions.find { it.digest == targetCard?.definition }?.definition?.faces
                    ?.find { it.faceId == targetCard?.faceId }?.name ?: "Card"
            }
            val textWidth = width - 4 * margin - if (target != null) cardWidth * .3f else 0f
            val columns = (textWidth / (font * .6f)).toInt().coerceAtLeast(1)
            val lines = label.chunked(columns)
            lanes += CombatLaneLabel(target, label, lines, TableBounds(laneX, 0f, width, 0f))
            if (laneX > 0) laneDividers += laneX
            laneX += width
        }
        val labelHeight = maxOf(cardWidth * .5f, lanes.maxOf { it.lines.size } * font * 1.2f + margin * 2)
        var x = 0f
        fun insertion(after: String?, before: String?, at: Float, left: Boolean = false, right: Boolean = false) {
            // Display grouping can make two visual neighbors nonadjacent in recorded pair order.
            val anchor = area.pairs.indexOfFirst { it.id == after }
            val gap = if (anchor >= 0) IdentityGap(after, area.pairs.getOrNull(anchor + 1)?.id) else {
                val next = area.pairs.indexOfFirst { it.id == before }
                IdentityGap(area.pairs.getOrNull(next - 1)?.id, before)
            }
            val target = (pairs.find { it.id == before } ?: pairs.find { it.id == after })?.attackTarget
            for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                val y = labelHeight + if (role == CombatRole.BLOCKER) 0f else rowHeight
                targets += CombatDropRegion("New ${role.name.lowercase()} pair ${if (before == null) "at end" else "before $before"}",
                    TableBounds(at - if (left) 0f else if (right) margin else 12f, y, if (left || right) margin else 24f, rowHeight),
                    CombatDestination(area.id, role, pairGap = gap, attackTarget = target), left, right,
                    TablePoint(at, y + rowHeight / 2))
            }
        }
        for ((index, pair) in pairs.withIndex()) {
            if (index > 0) dividers += x
            val pairWidth = slotWidth(pair.blockers.size, pair.attackers.size, cardWidth)
            for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                val side = pair.side(role)
                val y = labelHeight + if (role == CombatRole.BLOCKER) 0f else rowHeight
                var left = x + (pairWidth - rowWidth(side.size, cardWidth)) / 2
                targets += CombatDropRegion("Append ${role.name.lowercase()} to pair ${index + 1}",
                    TableBounds(x, y, pairWidth, rowHeight), CombatDestination(area.id, role, pair.id, attackTarget = pair.attackTarget),
                    landing = TablePoint(left + side.size * (cardWidth + margin), y + rowHeight / 2))
                for ((member, occurrence) in side.withIndex()) {
                    val card = CombatStackRules.inspection(board, occurrence.id)!!.copy(tapped = false)
                    cards += OccurrenceDrawing(occurrence.id, card,
                        TableBounds(left, y + margin, cardWidth, cardHeight), true)
                    targets += CombatDropRegion("Insert ${role.name.lowercase()} before ${occurrence.id}",
                        TableBounds(left - margin, y, margin * 2, rowHeight), CombatDestination(area.id, role, pair.id,
                            insertion = CombatInsertion.GAP, cardGap = IdentityGap(side.getOrNull(member - 1)?.id, occurrence.id)),
                        landing = TablePoint(left, y + rowHeight / 2))
                    left += cardWidth + margin
                }
            }
            x += pairWidth
        }
        if (area.pairs.isEmpty()) {
            x = cardWidth + 2 * padding
            insertion(null, null, x / 2, left = true, right = true)
        } else {
            dividers.forEachIndexed { index, at -> insertion(pairs[index].id, pairs[index + 1].id, at) }
            insertion(null, pairs.first().id, 0f, left = true)
            insertion(pairs.last().id, null, x, right = true)
        }
        return CombatGeometry(x, labelHeight + rowHeight * 2, cards, dividers, targets,
            lanes.map { it.copy(bounds = it.bounds.copy(height = labelHeight)) }, laneDividers, labelHeight)
    }

    fun stack(board: BoardState, cardWidth: Float, cardHeight: Float): StackGeometry {
        require(cardWidth > 0 && cardHeight >= cardWidth)
        val ids = CombatStackRules.stack(board).cards
        val visible = ids.take(1).map { id ->
            val card = requireNotNull(CombatStackRules.inspection(board, id)).copy(tapped = false)
            val effect = board.stackEffects.find { it.id == id }
            OccurrenceDrawing(id, card, TableBounds(0f, 0f,
                cardWidth, cardHeight), effect != null, effect?.frozen != null,
                board.stackTextMarks[id].orEmpty())
        }
        return StackGeometry(maxOf(cardWidth, visible.maxOfOrNull { it.bounds.x + it.bounds.width } ?: 0f),
            maxOf(cardHeight, visible.maxOfOrNull { it.bounds.y + it.bounds.height } ?: 0f), ids.size, visible, ids.toList())
    }

    fun label(state: PlayState, drawing: OccurrenceDrawing): String {
        val card = drawing.card
        val hidden = if (drawing.frozen) card.covered else !CardVisibility.isVisibleFaceUp(state.board, card, "")
        val name = if (hidden) "Covered card" else state.definitions.find { it.digest == card.definition }?.definition
            ?.faces?.find { it.faceId == card.faceId }?.name ?: "Card"
        return when {
            drawing.frozen -> "Effect, $name, retained source, read-only"
            drawing.projection -> "Projection, $name"
            else -> name
        }
    }
}

/** Renderer slot reuses the table's normal card face/art/cover conventions at precisely the same scale. */
@Composable
private fun OccurrenceCard(drawing: OccurrenceDrawing, width: Float, height: Float, viewer: String,
    selected: Boolean, related: Boolean, label: String, onClick: () -> Unit,
    face: @Composable (PlayCard, Modifier) -> Unit) {
    val b = drawing.bounds
    Box(Modifier.offset(b.x.dp, b.y.dp).size(b.width.dp, b.height.dp)
        .semantics { contentDescription = label }.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(width.dp, height.dp).graphicsLayer { rotationZ = if (!drawing.projection && drawing.card.tapped) 90f else 0f }) {
            val owner = if (drawing.card.owner == viewer) OwnTableColor else OpponentTableColor
            face(drawing.card, Modifier.fillMaxSize().cardFrame(drawing.card.token, owner, drawing.projection))
            Canvas(Modifier.fillMaxSize()) {
                if (related) drawRect(Color(0xffbd982f), topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = size.copy(width = size.width - 6.dp.toPx(), height = size.height - 6.dp.toPx()), style = Stroke(2.dp.toPx()))
                if (selected) drawRect(Color(0xff397ccc), style = Stroke(4.dp.toPx()))
            }
        }
        StackMarkChips(drawing.textMarks, Modifier.align(Alignment.TopEnd).widthIn(max = b.width.dp))
    }
}

/** Horizontal navigation, compact opposing groups and divider-only empty insertion affordances.
 * Opening/panning/recomposition creates no shared area/pair or inferred source edit.
 */
@Composable
fun CombatLane(state: PlayState, viewer: String, cardWidth: Float, cardHeight: Float,
    selected: Set<String>, related: Set<String>, onCard: (String) -> Unit,
    placementEnabled: Boolean, onDestination: (CombatDestination) -> Unit,
    face: @Composable (PlayCard, Modifier) -> Unit, modifier: Modifier = Modifier) {
    val scene = CombatStackPresentation.combat(state.board, cardWidth, cardHeight, state.definitions, viewer = viewer)
    if (state.board.combat == null) return
    BoxWithConstraints(modifier) {
        val laneWidth = maxWidth
        Box(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = laneWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.size(scene.width.dp, scene.height.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    for (x in scene.dividers) drawLine(Color.Black.copy(alpha = if (x in scene.laneDividers) .7f else .22f),
                        Offset(x.dp.toPx(), 0f), Offset(x.dp.toPx(), size.height), (if (x in scene.laneDividers) 3.dp else 1.dp).toPx())
                }
                for (lane in scene.lanes) Text(lane.label, Modifier.offset(lane.bounds.x.dp, 0.dp)
                    .size(lane.bounds.width.dp, scene.labelHeight.dp), fontSize = (cardWidth * .22f).sp)
                for (region in scene.destinations) {
                    val b = region.bounds
                    Box(Modifier.offset(b.x.dp, b.y.dp).size(b.width.dp, b.height.dp)
                        .semantics { contentDescription = region.label }
                        .clickable(enabled = placementEnabled) { onDestination(region.destination) })
                }
                for (drawing in scene.cards) OccurrenceCard(drawing, cardWidth, cardHeight, viewer,
                    drawing.viewId in selected, drawing.viewId in related, CombatStackPresentation.label(state, drawing),
                    { onCard(drawing.viewId) }, face)
            }
        }
    }
}

/** Top plus two underneath is a drawing bound only. The top-left circle and whole pile open all IDs. */
@Composable
fun StackPile(state: PlayState, viewer: String, cardWidth: Float, cardHeight: Float,
    selected: Set<String>, related: Set<String>, onContents: (List<String>) -> Unit,
    face: @Composable (PlayCard, Modifier) -> Unit, modifier: Modifier = Modifier) {
    val scene = CombatStackPresentation.stack(state.board, cardWidth, cardHeight)
    Box(modifier.size(scene.width.dp, scene.height.dp).semantics { contentDescription = "Stack, ${scene.count} entries" }
        .clickable { onContents(scene.orderedIds) }) {
        for (drawing in scene.visible.asReversed()) OccurrenceCard(drawing, cardWidth, cardHeight, viewer,
            drawing.viewId in selected, drawing.viewId in related, CombatStackPresentation.label(state, drawing),
            { onContents(scene.orderedIds) }, face)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopStart)
            .sizeIn(minWidth = 24.dp, minHeight = 24.dp).clickable { onContents(scene.orderedIds) }) {
            Box(contentAlignment = Alignment.Center) { Text(scene.count.toString(), Modifier.padding(4.dp)) }
        }
    }
}

/** No cap, source deduplication or category sorting: frozen and live effects retain distinct ordered rows. */
@Composable
fun StackContents(state: PlayState, onEntry: (CombatStackTarget) -> Unit, modifier: Modifier = Modifier) {
    val ids = CombatStackRules.stack(state.board).cards
    LazyColumn(modifier) {
        itemsIndexed(ids, key = { _, id -> id }) { index, id ->
            val card = requireNotNull(CombatStackRules.inspection(state.board, id))
            val effect = state.board.stackEffects.find { it.id == id }
            val drawing = OccurrenceDrawing(id, card, TableBounds(0f, 0f, 0f, 0f), effect != null, effect?.frozen != null)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onEntry(CombatStackController.bind(state.board, id)) }
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text((index + 1).toString(), color = MenuInk)
                CompositionLocalProvider(LocalContentColor provides MenuInk) {
                    TableChromeIcon(if (effect != null) "stack" else "card")
                }
                StackMarkChips(state.board.stackTextMarks[id].orEmpty())
                Column(Modifier.weight(1f)) {
                    Text(CombatStackPresentation.label(state, drawing), color = MenuInk)
                    Text(if (drawing.frozen) "Remove this effect only" else if (drawing.projection) "Source actions / remove effect" else "Card actions",
                        style = MaterialTheme.typography.labelSmall, color = MenuInk.copy(alpha = .7f))
                }
                Text("›", color = MenuInk)
            }
            if (index != ids.lastIndex) HorizontalDivider(color = MenuBorder.copy(alpha = .5f))
        }
    }
}
