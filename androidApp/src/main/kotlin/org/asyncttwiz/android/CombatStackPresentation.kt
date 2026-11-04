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
/** [emptyLane] marks the one drop covering a whole empty attack-target lane row. [exact] regions keep their
 * bounds on the table (empty lanes and one-sided pair-edge gaps); other new-pair gaps narrow to a screen tolerance.
 * Only the outermost drop on each side extends beyond the area's ends ([extendLeft]/[extendRight]). */
data class CombatDropRegion(val label: String, val bounds: TableBounds, val destination: CombatDestination,
    val extendLeft: Boolean = false, val extendRight: Boolean = false, val landing: TablePoint = bounds.center,
    val emptyLane: Boolean = false, val exact: Boolean = emptyLane) {
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
    val padding: Float = 2f, val accountId: String? = null, val inset: Float = padding,
    /** CMB-013: the target's life, loyalty or defense, at the header's right end (empty for a hidden target). */
    val value: String = "",
    /** Whose side the target is on (the defending player, or the card's controller), for the header's tint. */
    val side: String? = null) {
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

    /** Room for the name and, when the target has one, its value with the preview's "16 → 11" (CMB-013, CMB-029). */
    fun laneWidth(lane: CombatLaneLabel) = lane.textInset + laneTextWidth(lane.label, lane.fontSize) + lane.inset +
        if (lane.value.isEmpty()) 0f else laneTextWidth("  ${lane.value} → ${lane.value}", lane.fontSize)

    /** CMB-031: the gap between the blocker and attacker rows, where the arrows are drawn. */
    fun arrowGap(cardHeight: Float) = cardHeight * .5f

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
            // Combat review #3 (CR 506.4c): with Automatum on attackers keep a target that left the battlefield; they
            // attack nothing
            val gone = target != null && board.containers.none { it.zone == PlayZone.BATTLEFIELD && it.id == targetCard?.containerId }
            val hidden = !gone && targetCard != null && (targetCard.isFaceDown || !CardVisibility.isVisibleFaceUp(board, targetCard, viewer))
            val defender = CombatStackRules.defendingPlayer(board)
            val avatar = if (target == null) avatars[defender]?.takeIf { it.face in 0..15 } else null
            val name = names[defender] ?: "Player"
            val face = definitions.find { it.digest == targetCard?.definition }?.definition?.faces?.find { it.faceId == targetCard?.faceId }
            val label = when {
                target == null -> if (avatar != null) name else "→ $name"
                hidden -> "→ Face-down card"
                gone -> "→ ${face?.name ?: "Card"} (gone)"
                else -> face?.name ?: "Card"
            }
            val value = when {
                target == null -> board.players.firstOrNull { it.accountId == defender }?.life.orEmpty()
                hidden || gone -> ""
                // Combat review #5: with Automatum on loyalty and defense are the typed counters Automatum changes
                targetCard != null -> {
                    val kind = if (AttackTargets.hasType(face?.typeLine.orEmpty(), "Planeswalker")) "loyalty" else "defense"
                    (targetCard.typedCounters.firstOrNull { it.kind == kind }?.count ?: 0).toString()
                }
                else -> targetCard?.corner.orEmpty()
            }
            val side = if (target == null) defender else board.containers.firstOrNull { it.id == targetCard?.containerId }?.accountId
            val lane = CombatLaneLabel(target, label, listOf(label), TableBounds(laneX, 0f, cardSpan, 0f), fontSize = font,
                artwork = avatar?.let { org.asyncttwiz.storage.ArtworkReference(it.scryfallId, it.face, "avatar-v1", "art_crop") },
                accountId = defender.takeIf { avatar != null }, inset = laneLabelInset(cardWidth), value = value, side = side)
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
        val gap = arrowGap(cardHeight)
        fun rowY(role: CombatRole) = if (mirrored) { if (role == CombatRole.ATTACKER) 0f else rowHeight + gap }
            else labelHeight + if (role == CombatRole.BLOCKER) 0f else rowHeight + gap
        var x = 0f
        // [left]/[right]: a margin-wide gap just inside the pair starting/ending at [at], never narrowed;
        // [extend]: it also catches drops beyond that end of the area (only when nothing lies further out).
        fun insertion(after: String?, before: String?, at: Float, left: Boolean = false, right: Boolean = false,
            lanePair: String? = before ?: after, extend: Boolean = false) {
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
                    CombatDestination(area.id, role, pairGap = gap, attackTarget = target), left && extend, right && extend,
                    TablePoint(at, y + rowHeight / 2), exact = left || right)
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
                // An empty lane between two pairs keeps both pair edges one-sided, so the whole empty lane
                // stays its own drop (a centered gap tolerance would reach into it).
                val apart = kotlin.math.abs(edges[index - 1].second - edges[index].first) > .01f
                insertion(pairs[index - 1].id, pairs[index].id, edges[index].first, left = apart)
                if (apart) insertion(pairs[index - 1].id, pairs[index].id, edges[index - 1].second, right = true, lanePair = pairs[index - 1].id)
            }
            // The area's end gaps extend outward only when no empty lane lies beyond them.
            insertion(null, pairs.first().id, edges.first().first, left = true, extend = edges.first().first < .01f)
            insertion(pairs.last().id, null, edges.last().second, right = true, extend = edges.last().second > x - .01f)
        }
        return CombatGeometry(x, labelHeight + rowHeight * 2 + gap, cards, dividers, targets,
            lanes.map { it.copy(bounds = it.bounds.copy(y = if (mirrored) rowHeight * 2 + gap else 0f, height = labelHeight)) },
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

    fun label(state: TableBoard, drawing: OccurrenceDrawing): String {
        val card = drawing.card
        val hidden = if (drawing.frozen) card.covered else CombatStackRules.faceDownEntry(state.board, drawing.viewId) ||
            !CardVisibility.isVisibleFaceUp(state.board, card, "")
        val name = if (hidden) "Covered card" else state.definitions.find { it.digest == card.definition }?.definition
            ?.faces?.find { it.faceId == card.faceId }?.name ?: "Card"
        return when {
            drawing.frozen -> "Effect, $name, retained source, read-only"
            drawing.projection -> "Projection, $name"
            else -> name
        }
    }
}
