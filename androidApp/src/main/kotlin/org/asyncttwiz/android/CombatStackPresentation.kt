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
/** [emptyLane] marks the one drop covering a whole empty attack-target lane row; it is never narrowed. */
data class CombatDropRegion(val label: String, val bounds: TableBounds, val destination: CombatDestination,
    val extendLeft: Boolean = false, val extendRight: Boolean = false, val landing: TablePoint = bounds.center,
    val emptyLane: Boolean = false) {
    fun contains(point: TablePoint) = point.y >= bounds.y && point.y < bounds.bottom &&
        (extendLeft || point.x >= bounds.x) && (extendRight || point.x < bounds.right)
}
data class CombatGeometry(val width: Float, val height: Float, val cards: List<OccurrenceDrawing>,
    val dividers: List<Float>, val destinations: List<CombatDropRegion>,
    val lanes: List<CombatLaneLabel> = emptyList(), val laneDividers: List<Float> = emptyList(), val labelHeight: Float = 0f,
    val mirrored: Boolean = false) {
    /** The defending viewer sees the area mirrored: attackers on top, blockers below, lane labels at the bottom. */
    val labelY get() = if (mirrored) height - labelHeight else 0f
    /** Vertical span of the two card rows (everything but the lane label row). */
    val rowsTop get() = if (mirrored) 0f else labelHeight
    val rowsBottom get() = if (mirrored) height - labelHeight else height
    /** Card-slot separators: [dividers] that are not also lane separators. They span only the card rows,
     * while lane separators ([laneDividers]) cross the whole area height, the label row included. */
    val slotDividers get() = dividers.filter { x -> laneDividers.none { kotlin.math.abs(it - x) < .01f } }
}
/** [accountId] marks a player lane drawn with that player's round avatar icon. [inset] is the horizontal
 * space kept free at each lane edge, so the icon and name never touch a lane separator. */
data class CombatLaneLabel(val cardId: String?, val label: String, val lines: List<String>, val bounds: TableBounds,
    val artwork: org.asyncttwiz.storage.ArtworkReference? = null, val fontSize: Float = 11.5f,
    val padding: Float = 2f, val accountId: String? = null, val inset: Float = padding) {
    val lineHeight get() = fontSize * 1.25f
    val artworkHeight get() = lineHeight
    val artworkWidth get() = if (accountId != null) artworkHeight else artworkHeight / 1.4f
    val textInset get() = inset + if (cardId != null || accountId != null) artworkWidth + padding else 0f
    /** Room for the name between [textInset] and the right edge's [inset]. */
    val textWidth get() = bounds.width - textInset - inset
}
data class StackGeometry(val width: Float, val height: Float, val count: Int,
    val visible: List<OccurrenceDrawing>, val orderedIds: List<String>)

/** dp scene geometry; the caller passes the exact current ordinary-table card dimensions. */
object CombatStackPresentation {
    fun cardGap(cardWidth: Float) = cardWidth * .04f
    // Separator strokes and gaps scale with the card like its outline (1.5dp at the default 28dp card width),
    // so they keep their proportions at every card size and zoom.
    fun cardOutline(cardWidth: Float) = cardWidth * 1.5f / 28f
    fun laneDividerStroke(cardWidth: Float) = cardWidth * 2f / 28f
    fun slotDividerStroke(cardWidth: Float) = cardWidth * 1f / 28f
    const val LANE_DIVIDER_ALPHA = .55f
    const val SLOT_DIVIDER_ALPHA = .22f
    /** Free space between a separator line and the nearest card outline, lane icon or name. */
    fun dividerClearance(cardWidth: Float) = cardWidth * 2f / 28f
    /** Horizontal lane-label inset: half the lane separator plus the clearance. */
    fun laneLabelInset(cardWidth: Float) = laneDividerStroke(cardWidth) / 2 + dividerClearance(cardWidth)
    /** Every slot edge may border a lane separator: its half stroke, the clearance, then the card's outer outline half. */
    fun slotMargin(cardWidth: Float) = laneLabelInset(cardWidth) + cardOutline(cardWidth) / 2
    fun rowWidth(count: Int, cardWidth: Float) = count * cardWidth + cardGap(cardWidth) * (count - 1).coerceAtLeast(0)
    fun slotWidth(blockers: Int, attackers: Int, cardWidth: Float) =
        maxOf(rowWidth(blockers, cardWidth), rowWidth(attackers, cardWidth)) + 2 * slotMargin(cardWidth)

    /** Conservative bold-sans advance estimate (em per character), so a lane never clips its one-line name. */
    fun laneTextWidth(text: String, fontSize: Float): Float = text.sumOf { c ->
        when {
            c in "iIjlf.,:;'!|’ " -> .32
            c in "rt()[]-" -> .42
            c in "mwMW" -> .92
            c.isUpperCase() -> .72
            c.isDigit() || c.isLetter() && c.code < 128 -> .6
            else -> 1.0
        }
    }.toFloat() * fontSize * 1.06f

    fun laneWidth(lane: CombatLaneLabel) = lane.textInset + laneTextWidth(lane.label, lane.fontSize) + lane.inset

    /** Rules v28: face-up planeswalkers on the defending player's battlefield, then face-up battles (Sieges)
     * on the attacking player's own battlefield, each in battlefield order. */
    fun attackTargets(board: BoardState, definitions: List<SubmittedDefinition>, viewer: String): List<String> {
        val cards = board.cards.associateBy { it.id }
        fun on(player: String?, type: String) = board.containers
            .singleOrNull { it.zone == PlayZone.BATTLEFIELD && player != null && it.accountId == player }?.cards.orEmpty().filter { id ->
                val card = cards[id] ?: return@filter false
                val typeLine = definitions.find { it.digest == card.definition }?.definition?.faces
                    ?.find { it.faceId == card.faceId }?.typeLine.orEmpty()
                !card.isFaceDown && CardVisibility.isVisibleFaceUp(board, card, viewer) &&
                    AttackTargets.battlefieldOwner(board, typeLine) == player && AttackTargets.hasType(typeLine, type)
            }
        return on(CombatStackRules.defendingPlayer(board), "Planeswalker") + on(CombatStackRules.attackingPlayer(board), "Battle")
    }

    fun combat(board: BoardState, cardWidth: Float, cardHeight: Float,
        definitions: List<SubmittedDefinition> = emptyList(), names: Map<String, String> = emptyMap(), viewer: String = "",
        titleScale: Float = 1f, avatars: Map<String, Avatar> = emptyMap()): CombatGeometry {
        require(cardWidth > 0 && cardHeight >= cardWidth)
        val area = board.combat ?: return CombatGeometry(0f, 0f, emptyList(), emptyList(), emptyList())
        val margin = cardGap(cardWidth)
        val padding = slotMargin(cardWidth)
        val rowHeight = cardHeight + margin * 2
        val cards = mutableListOf<OccurrenceDrawing>()
        val targets = mutableListOf<CombatDropRegion>()
        val dividers = mutableListOf<Float>()
        val laneDividers = mutableListOf<Float>()
        val used = area.pairs.groupBy { it.attackTarget }
        // Every possible target always has a lane (player first, then battlefield order), so drags can reach it.
        val groups = (listOf<String?>(null) + attackTargets(board, definitions, viewer) + used.keys).distinct()
            .associateWith { used[it].orEmpty() }
        val pairs = groups.values.flatten()
        val lanes = mutableListOf<CombatLaneLabel>()
        val font = 7f * cardWidth / 28f * cardScale(titleScale) // the card title size
        val pairWidths = mutableMapOf<String, Float>()
        var laneX = 0f
        for ((target, members) in groups) {
            val cardSpan = members.sumOf { slotWidth(it.blockers.size, it.attackers.size, cardWidth).toDouble() }.toFloat()
                .coerceAtLeast(cardWidth + 2 * padding)
            val targetCard = board.cards.find { it.id == target }
            val hidden = targetCard != null && (targetCard.isFaceDown || !CardVisibility.isVisibleFaceUp(board, targetCard, viewer))
            val defender = CombatStackRules.defendingPlayer(board)
            val avatar = if (target == null) avatars[defender]?.takeIf { it.face in 0..15 } else null
            val name = names[defender] ?: "Player"
            val label = when {
                target == null -> if (avatar != null) name else "→ $name"
                hidden -> "→ Face-down card"
                else -> definitions.find { it.digest == targetCard?.definition }?.definition?.faces
                    ?.find { it.faceId == targetCard?.faceId }?.name ?: "Card"
            }
            val lane = CombatLaneLabel(target, label, listOf(label), TableBounds(laneX, 0f, cardSpan, 0f), fontSize = font,
                artwork = avatar?.let { org.asyncttwiz.storage.ArtworkReference(it.scryfallId, it.face, "avatar-v1", "art_crop") },
                accountId = defender.takeIf { avatar != null }, inset = laneLabelInset(cardWidth))
            // Icon and full name always fit on one row, empty lanes included; wide lanes widen the area, which then pans.
            val width = maxOf(cardSpan, laneWidth(lane))
            for (pair in members) pairWidths[pair.id] = slotWidth(pair.blockers.size, pair.attackers.size, cardWidth) +
                (width - cardSpan) / members.size
            lanes += lane.copy(bounds = lane.bounds.copy(width = width))
            if (laneX > 0) laneDividers += laneX
            laneX += width
        }
        val labelHeight = maxOf(cardWidth * .5f, lanes.maxOf { it.lineHeight + 2 * it.padding })
        // The defender's view mirrors the attacker's: attackers on top, blockers below, labels at the bottom.
        val mirrored = viewer.isNotEmpty() && CombatStackRules.defendingPlayer(board) == viewer
        fun rowY(role: CombatRole) = if (mirrored) { if (role == CombatRole.ATTACKER) 0f else rowHeight }
            else labelHeight + if (role == CombatRole.BLOCKER) 0f else rowHeight
        var x = 0f
        fun insertion(after: String?, before: String?, at: Float, left: Boolean = false, right: Boolean = false,
            lanePair: String? = before ?: after) {
            // Display grouping can make two visual neighbors nonadjacent in recorded pair order.
            val anchor = area.pairs.indexOfFirst { it.id == after }
            val gap = if (anchor >= 0) IdentityGap(after, area.pairs.getOrNull(anchor + 1)?.id) else {
                val next = area.pairs.indexOfFirst { it.id == before }
                IdentityGap(area.pairs.getOrNull(next - 1)?.id, before)
            }
            val target = pairs.find { it.id == lanePair }?.attackTarget
            for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                val y = rowY(role)
                targets += CombatDropRegion("New ${role.name.lowercase()} pair ${if (before == null) "at end" else "before $before"}",
                    TableBounds(at - if (left) 0f else if (right) margin else 12f, y, if (left || right) margin else 24f, rowHeight),
                    CombatDestination(area.id, role, pairGap = gap, attackTarget = target), left, right,
                    TablePoint(at, y + rowHeight / 2))
            }
        }
        val edges = mutableListOf<Pair<Float, Float>>() // each laid-out pair's left and right x, in display order
        for ((target, members) in groups) {
            if (members.isEmpty()) {
                // A whole empty lane is one drop: a new pair attacking that target, after every recorded pair.
                val lane = lanes.first { it.cardId == target }
                for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                    val y = rowY(role)
                    targets += CombatDropRegion("New ${role.name.lowercase()} pair attacking ${lane.label}",
                        TableBounds(x, y, lane.bounds.width, rowHeight),
                        CombatDestination(area.id, role, pairGap = IdentityGap(area.pairs.lastOrNull()?.id, null), attackTarget = target),
                        // Outermost empty lanes also catch drops beyond the area's ends.
                        lane === lanes.first(), lane === lanes.last(), TablePoint(x + lane.bounds.width / 2, y + rowHeight / 2),
                        emptyLane = true)
                }
                x += lane.bounds.width
                continue
            }
            for (pair in members) {
                val index = edges.size
                if (index > 0) dividers += x
                val pairWidth = pairWidths.getValue(pair.id)
                for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                    val side = pair.side(role)
                    val y = rowY(role)
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
                edges += x to x + pairWidth
                x += pairWidth
            }
        }
        if (pairs.isNotEmpty()) {
            for (index in 1 until pairs.size) {
                insertion(pairs[index - 1].id, pairs[index].id, edges[index].first)
                // An empty lane between two pairs: the earlier pair's own right edge is a slot boundary too.
                if (edges[index - 1].second != edges[index].first)
                    insertion(pairs[index - 1].id, pairs[index].id, edges[index - 1].second, right = true, lanePair = pairs[index - 1].id)
            }
            insertion(null, pairs.first().id, edges.first().first, left = true)
            insertion(pairs.last().id, null, edges.last().second, right = true)
        }
        return CombatGeometry(x, labelHeight + rowHeight * 2, cards, dividers, targets,
            lanes.map { it.copy(bounds = it.bounds.copy(y = if (mirrored) rowHeight * 2 else 0f, height = labelHeight)) },
            laneDividers, labelHeight, mirrored)
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
    val appearance = rememberCardAppearance()
    val scene = CombatStackPresentation.combat(state.board, cardWidth, cardHeight, state.definitions, viewer = viewer,
        titleScale = appearance.text(CardTextScalePart.TITLE))
    if (state.board.combat == null) return
    BoxWithConstraints(modifier) {
        val laneWidth = maxWidth
        Box(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = laneWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.size(scene.width.dp, scene.height.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    for (x in scene.slotDividers) drawLine(Color.Black.copy(alpha = CombatStackPresentation.SLOT_DIVIDER_ALPHA),
                        Offset(x.dp.toPx(), scene.rowsTop.dp.toPx()), Offset(x.dp.toPx(), scene.rowsBottom.dp.toPx()),
                        CombatStackPresentation.slotDividerStroke(cardWidth).dp.toPx())
                    for (x in scene.laneDividers) drawLine(Color.Black.copy(alpha = CombatStackPresentation.LANE_DIVIDER_ALPHA),
                        Offset(x.dp.toPx(), 0f), Offset(x.dp.toPx(), scene.height.dp.toPx()),
                        CombatStackPresentation.laneDividerStroke(cardWidth).dp.toPx())
                }
                for (lane in scene.lanes) Text(lane.label, Modifier.offset(lane.bounds.x.dp, lane.bounds.y.dp)
                    .size(lane.bounds.width.dp, scene.labelHeight.dp).padding(horizontal = lane.inset.dp, vertical = lane.padding.dp),
                    fontSize = lane.fontSize.sp, lineHeight = lane.lineHeight.sp, maxLines = 1, softWrap = false)
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
