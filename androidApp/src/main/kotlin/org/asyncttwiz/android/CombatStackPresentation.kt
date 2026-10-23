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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

data class OccurrenceDrawing(val viewId: String, val card: PlayCard, val bounds: TableBounds,
    val projection: Boolean, val frozen: Boolean = false)
data class CombatDropRegion(val label: String, val bounds: TableBounds, val destination: CombatDestination)
data class CombatGeometry(val width: Float, val height: Float, val cards: List<OccurrenceDrawing>,
    val dividers: List<Float>, val destinations: List<CombatDropRegion>)
data class StackGeometry(val width: Float, val height: Float, val count: Int,
    val visible: List<OccurrenceDrawing>, val orderedIds: List<String>)

/** dp scene geometry; the caller passes the exact current ordinary-table card dimensions. */
object CombatStackPresentation {
    fun combat(board: BoardState, cardWidth: Float, cardHeight: Float): CombatGeometry {
        require(cardWidth > 0 && cardHeight >= cardWidth)
        val area = board.combat ?: return CombatGeometry(0f, 0f, emptyList(), emptyList(), emptyList())
        val margin = cardWidth * .04f
        val rowHeight = cardHeight + margin * 2
        val cards = mutableListOf<OccurrenceDrawing>()
        val targets = mutableListOf<CombatDropRegion>()
        val dividers = mutableListOf<Float>()
        var x = 0f
        fun insertion(after: String?, before: String?) {
            for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                val y = if (role == CombatRole.BLOCKER) 0f else rowHeight
                targets += CombatDropRegion("New ${role.name.lowercase()} pair ${if (before == null) "at end" else "before $before"}",
                    TableBounds(x, y, cardWidth, rowHeight), CombatDestination(area.id, role, pairGap = IdentityGap(after, before)))
            }
            x += cardWidth
        }
        for ((index, pair) in area.pairs.withIndex()) {
            insertion(area.pairs.getOrNull(index - 1)?.id, pair.id)
            dividers += x
            fun width(occurrence: CombatOccurrence): Float = if (CombatStackRules.inspection(board, occurrence.id)!!.tapped) cardHeight else cardWidth
            fun sideWidth(side: List<CombatOccurrence>) = side.sumOf { width(it).toDouble() }.toFloat() +
                margin * (side.size - 1).coerceAtLeast(0)
            val pairWidth = maxOf(sideWidth(pair.blockers), sideWidth(pair.attackers)) + margin * 2
            for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                val side = pair.side(role)
                val y = if (role == CombatRole.BLOCKER) 0f else rowHeight
                var left = x + margin
                targets += CombatDropRegion("Append ${role.name.lowercase()} to pair ${index + 1}",
                    TableBounds(x, y, pairWidth, rowHeight), CombatDestination(area.id, role, pair.id))
                for ((member, occurrence) in side.withIndex()) {
                    val card = CombatStackRules.inspection(board, occurrence.id)!!
                    val envelopeWidth = width(occurrence)
                    val envelopeHeight = if (card.tapped) cardWidth else cardHeight
                    cards += OccurrenceDrawing(occurrence.id, card,
                        TableBounds(left, y + (rowHeight - envelopeHeight) / 2, envelopeWidth, envelopeHeight), true)
                    targets += CombatDropRegion("Insert ${role.name.lowercase()} before ${occurrence.id}",
                        TableBounds(left - margin, y, margin * 2, rowHeight), CombatDestination(area.id, role, pair.id,
                            insertion = CombatInsertion.GAP, cardGap = IdentityGap(side.getOrNull(member - 1)?.id, occurrence.id)))
                    left += envelopeWidth + margin
                }
            }
            x += pairWidth
            dividers += x
        }
        insertion(area.pairs.lastOrNull()?.id, null)
        return CombatGeometry(x, rowHeight * 2, cards, dividers, targets)
    }

    fun stack(board: BoardState, cardWidth: Float, cardHeight: Float): StackGeometry {
        require(cardWidth > 0 && cardHeight >= cardWidth)
        val ids = CombatStackRules.stack(board).cards
        val peek = cardWidth * .12f
        val visible = ids.take(3).mapIndexed { index, id ->
            val card = requireNotNull(CombatStackRules.inspection(board, id))
            val effect = board.stackEffects.find { it.id == id }
            // Same top-at-bottom-right corner peeks as Library/Graveyard, not a fan.
            OccurrenceDrawing(id, card, TableBounds((2 - index) * peek, (2 - index) * peek,
                if (card.tapped) cardHeight else cardWidth, if (card.tapped) cardWidth else cardHeight), effect != null, effect?.frozen != null)
        }
        return StackGeometry(maxOf(cardWidth, visible.maxOfOrNull { it.bounds.x + it.bounds.width } ?: 0f),
            maxOf(cardHeight, visible.maxOfOrNull { it.bounds.y + it.bounds.height } ?: 0f), ids.size, visible, ids.toList())
    }

    fun label(state: PlayState, drawing: OccurrenceDrawing): String {
        val card = drawing.card
        val hidden = if (drawing.frozen) card.covered else !CardVisibility.isVisibleFaceUp(card, PlayZone.STACK, null, "")
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
        Box(Modifier.requiredSize(width.dp, height.dp).graphicsLayer { rotationZ = if (drawing.card.tapped) 90f else 0f }) {
            face(drawing.card, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val owner = if (drawing.card.owner == viewer) Color(0xff287f85) else Color(0xffa84f58)
                drawRect(owner, style = Stroke(2.dp.toPx(), pathEffect = if (drawing.projection)
                    PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null))
                if (related) drawRect(Color(0xffbd982f), topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = size.copy(width = size.width - 6.dp.toPx(), height = size.height - 6.dp.toPx()), style = Stroke(2.dp.toPx()))
                if (selected) drawRect(Color(0xff397ccc), style = Stroke(4.dp.toPx()))
            }
        }
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
    val scene = CombatStackPresentation.combat(state.board, cardWidth, cardHeight)
    if (state.board.combat == null) return
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Box(Modifier.size(scene.width.dp, scene.height.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                for (x in scene.dividers) drawLine(Color.Black.copy(alpha = .12f), Offset(x.dp.toPx(), 0f),
                    Offset(x.dp.toPx(), size.height), 1.dp.toPx())
            }
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
