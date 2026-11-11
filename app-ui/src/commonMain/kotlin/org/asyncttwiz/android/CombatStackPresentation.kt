package org.asyncttwiz.android

import org.asyncttwiz.protocol.Faults
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
/**
 * CMB-028, CMB-034 (owner, 2026-11-08): where the next creature goes, a card's place and size in a lane: a blocker's over the
 * attackers of [pairId] (one with no blocker yet), an attacker's at the end of the lane attacking [attackTarget] (null: the
 * player). The table draws the ones the viewer is placing ([CombatAreaOverlay]).
 */
data class CombatSlot(val role: CombatRole, val bounds: TableBounds, val pairId: String?, val attackTarget: String?)
/**
 * CMB-050 (owner playtest 2026-11-10): the combat damage the attackers of pair [pairId] dealt to their target ([target]: its
 * chip's name), [amount], as a small red splash at [bounds] (on the screen), drawn behind the pair's blockers.
 */
data class CombatLaneDamage(val pairId: String, val bounds: TableBounds, val amount: Int, val target: String) {
    companion object {
        /** The splash's size as a share of a combat card's width. */
        const val SIZE = .6f
    }
}
data class CombatGeometry(val width: Float, val height: Float, val cards: List<OccurrenceDrawing>,
    val dividers: List<Float>, val destinations: List<CombatDropRegion>,
    val lanes: List<CombatLaneLabel> = emptyList(), val laneDividers: List<Float> = emptyList(), val labelHeight: Float = 0f,
    val mirrored: Boolean = false, val slots: List<CombatSlot> = emptyList(),
    /** CMB-031: the gap between the card rows, where the arrows are drawn. */
    val arrowGap: TableBounds? = null,
    /** CMB-050: each pair's part of the blockers' row (its width, the row's height), by pair id. */
    val blockerRows: Map<String, TableBounds> = emptyMap()) {
    /** The defending viewer sees the area mirrored: attackers on top, blockers below, lane labels at the bottom. */
    val labelY get() = if (mirrored) height - labelHeight else 0f
    /** Vertical span of the two card rows (everything but the lane label row). */
    val rowsTop get() = if (mirrored) 0f else labelHeight
    val rowsBottom get() = if (mirrored) height - labelHeight else height
    /** Card-slot separators: [dividers] that are not also lane separators. They span only the card rows,
     * while lane separators ([laneDividers]) cross the whole area height, the label row included. */
    val slotDividers get() = dividers.filter { x -> laneDividers.none { kotlin.math.abs(it - x) < .01f } }
}
/**
 * A target's lane header (CMB-013): its chip, centred in the lane, with the crossed swords, a round avatar (the defending
 * player's, [accountId], or the card's art), the name ([label] in full, [chipText] on the chip) and the target's life,
 * loyalty or defense ([value]). [bounds] is the lane's header part (the chip row); [inset] the space kept free at each
 * lane edge, so nothing touches a lane separator.
 */
data class CombatLaneLabel(val cardId: String?, val label: String, val bounds: TableBounds,
    val artwork: org.asyncttwiz.storage.ArtworkReference? = null, val fontSize: Float = CombatStackPresentation.CHIP_FONT,
    val accountId: String? = null, val inset: Float = 2f,
    /** CMB-013: the target's life, loyalty or defense, shown on the chip after the name (empty for a hidden or gone
     * target). */
    val value: String = "",
    /** Whose side the target is on (the defending player, or the card's controller), for the chip's tint. */
    val side: String? = null,
    /** The chip on the table (the lane's header and, before attackers are staged, its target selector), as wide as its
     * name and [value] need; the damage preview ("20 → 17", CMB-029) widens it about its centre. */
    val chip: TableBounds = bounds,
    /** The target's card is hidden from the viewer (a face-down card): no art. */
    val hidden: Boolean = false) {
    /** The name on the chip: at most [CombatStackPresentation.CHIP_LETTERS] letters, cut with "…" (CMB-013). */
    val chipText get() = CombatStackPresentation.chipText(label)
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
            c.isDigit() || c == '−' || c.isLetter() && c.code < 128 -> .6
            else -> 1.0
        }
    }.toFloat() * fontSize * 1.06f

    /** CMB-013 (owner playtest 2026-11-06): a target chip's height and text size (dp at zoom 1; it zooms with the band). */
    const val CHIP_HEIGHT = 24f
    const val CHIP_FONT = 11f
    /** The most letters of a name a chip shows; a longer name is cut with "…" (holding the chip shows it whole). */
    const val CHIP_LETTERS = 12
    /** Free space above and below a chip in its row. */
    const val CHIP_PAD = 4f
    /** CMB-001: the chip row, which is also the collapsed band's height (dp at zoom 1). */
    const val CHIP_ROW = CHIP_HEIGHT + 2 * CHIP_PAD
    /** The chip's parts: side padding, the crossed swords, the round avatar and the gaps between them. */
    const val CHIP_SIDE = 6f
    const val CHIP_ICON = 13f
    const val CHIP_AVATAR = 18f
    const val CHIP_GAP = 3f

    /** [name] as a chip shows it: whole up to [CHIP_LETTERS] letters, else cut with "…". */
    fun chipText(name: String): String = if (name.length <= CHIP_LETTERS) name else name.take(CHIP_LETTERS - 1).trimEnd() + "…"

    /** A chip's width for [name] (dp at zoom 1): the swords, the avatar, the name as [chipText] shows it and, when there
     * is one, [value] after it (the target's life, loyalty or defense, or the damage preview's "20 → 17"). */
    fun chipWidth(name: String, value: String = ""): Float = CHIP_SIDE + CHIP_ICON + CHIP_GAP + CHIP_AVATAR + CHIP_GAP + 1f +
        laneTextWidth(chipText(name), CHIP_FONT) + (if (value.isEmpty()) 0f else CHIP_VALUE_GAP + laneTextWidth(value, CHIP_FONT)) +
        CHIP_SIDE

    /** The gap between the name and the value on a chip (CMB-013). */
    const val CHIP_VALUE_GAP = 5f

    /** Room for the chip with, when the target has one, the widest damage preview it can show (CMB-013, CMB-029), so the
     * lane never changes and nothing moves when the preview appears. */
    fun laneWidth(lane: CombatLaneLabel) = 2 * lane.inset + chipWidth(lane.label, previewRoom(lane.value))

    /** The widest text the chip may show after the name for [value] (CMB-013, owner gallery review 2026-11-07): a negative
     * result with a minus and as many digits as [value], at least two ("3 → −88", "20 → −88"), which also covers a
     * result with one digit more than [value] ("9 → 12"). Rarer results that are wider still squeeze the chip's name. */
    fun previewRoom(value: String): String {
        if (value.isEmpty()) return ""
        val digits = value.trimStart('-', '−').takeIf { value.toIntOrNull() != null }?.length ?: return "$value → $value"
        return "$value → −" + "8".repeat(maxOf(digits, 2))
    }

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
        avatars: Map<String, Avatar> = emptyMap(),
        /** CMB-001 (owner playtest 2026-11-06): false for the collapsed band, which shows only the target chips (one
         * row, each lane one whole attacker drop); true once an attacker is in the area. */
        open: Boolean = true,
        /** CMB-028 (owner, 2026-11-08): the viewer declares attackers: each lane with attackers keeps room for one more at
         * its end, so choosing another target moves nothing. */
        attackerSlots: Boolean = false,
        /** The board's point (its event), for the log of a combat copy left out ([drawable]). */
        head: String = ""): CombatGeometry {
        require(cardWidth > 0 && cardHeight >= cardWidth)
        val area = board.combat?.let { drawable(board, it, head) } ?: return CombatGeometry(0f, 0f, emptyList(), emptyList(), emptyList())
        val margin = cardGap(cardWidth)
        val padding = slotMargin(cardWidth)
        val rowHeight = cardHeight + margin * 2
        val cards = mutableListOf<OccurrenceDrawing>()
        val slots = mutableListOf<CombatSlot>()
        val targets = mutableListOf<CombatDropRegion>()
        val dividers = mutableListOf<Float>()
        val laneDividers = mutableListOf<Float>()
        val used = area.pairs.groupBy { it.attackTarget }
        // Every possible target always has a lane (player first, then battlefield order), so drags can reach it.
        val groups = (listOf<String?>(null) + attackTargets(board, definitions, viewer) + used.keys).distinct()
            .associateWith { used[it].orEmpty() }
        val pairs = groups.values.flatten()
        val lanes = mutableListOf<CombatLaneLabel>()
        val pairWidths = mutableMapOf<String, Float>()
        val lanePads = mutableMapOf<String?, Float>()
        var laneX = 0f
        val attackerSlot = slotWidth(0, 1, cardWidth)
        // CMB-012, CMB-028 (owner playtest 2026-11-10): the slot takes no part in placing the combatants: a lane with
        // attackers keeps a slot's room on each side of them, so they stay centred where they are once declared (the slot
        // gone) and only the slot sits in the room after them
        fun slotRoom(members: List<CombatPair>) = if (attackerSlots && members.isNotEmpty()) attackerSlot else 0f
        for ((target, members) in groups) {
            val cardSpan = (members.sumOf { slotWidth(it.blockers.size, it.attackers.size, cardWidth).toDouble() }.toFloat() +
                2 * slotRoom(members))
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
                target == null -> name
                hidden -> "Face-down card"
                gone -> "${face?.name ?: unnamedTarget(targetCard?.id)} (gone)"
                else -> face?.name ?: unnamedTarget(targetCard?.id)
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
            val lane = CombatLaneLabel(target, label, TableBounds(laneX, 0f, cardSpan, 0f),
                artwork = avatar?.let { org.asyncttwiz.storage.ArtworkReference(it.scryfallId, it.face, "art_crop") },
                accountId = defender.takeIf { target == null }, inset = laneLabelInset(cardWidth), value = value, side = side,
                hidden = hidden)
            // The chip (with its value and the damage preview) always fits its lane; wide lanes widen the area, which then pans.
            val width = maxOf(cardSpan, laneWidth(lane))
            // A lane wider than its pairs (for its chip) keeps them packed in its middle (CMB-008)
            for (pair in members) pairWidths[pair.id] = slotWidth(pair.blockers.size, pair.attackers.size, cardWidth)
            if (members.isNotEmpty()) lanePads[target] = (width - cardSpan) / 2
            lanes += lane.copy(bounds = lane.bounds.copy(width = width))
            if (laneX > 0) laneDividers += laneX
            laneX += width
        }
        // CMB-001 (owner playtest 2026-11-06): collapsed, the band is the chip row alone; a tap on a chip picks its
        // target, and each whole lane is one attacker drop (a creature dragged there is staged attacking its target)
        if (!open) {
            for (lane in lanes) targets += CombatDropRegion("New attacker pair attacking ${lane.label}",
                TableBounds(lane.bounds.x, 0f, lane.bounds.width, CHIP_ROW),
                CombatDestination(area.id, CombatRole.ATTACKER, pairGap = IdentityGap(area.pairs.lastOrNull()?.id, null),
                    attackTarget = lane.cardId), lane === lanes.first(), lane === lanes.last(),
                TablePoint(lane.bounds.center.x, CHIP_ROW / 2), emptyLane = true)
            return CombatGeometry(laneX, CHIP_ROW, emptyList(), emptyList(), targets,
                lanes.map { headed(it.copy(bounds = it.bounds.copy(y = 0f, height = CHIP_ROW)), CHIP_PAD) },
                emptyList(), CHIP_ROW, false)
        }
        // CMB-013 (owner gallery review 2026-11-06): the chips on top, each with its target's value on it
        val labelHeight = CHIP_ROW
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
        val blockerRows = LinkedHashMap<String, TableBounds>()
        for ((target, members) in groups) {
            if (members.isEmpty()) {
                // A whole empty lane is one drop: a new pair attacking that target, after every recorded pair.
                val lane = lanes.firstOrNull { it.cardId == target }
                    ?: run { Faults.report("CombatStackPresentation", "empty lane without its target", mapOf("target" to target.toString())); continue }
                for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                    val y = rowY(role)
                    targets += CombatDropRegion("New ${role.name.lowercase()} pair attacking ${lane.label}",
                        TableBounds(x, y, lane.bounds.width, rowHeight),
                        CombatDestination(area.id, role, pairGap = IdentityGap(area.pairs.lastOrNull()?.id, null), attackTarget = target),
                        // Outermost empty lanes also catch drops beyond the area's ends.
                        lane === lanes.first(), lane === lanes.last(), TablePoint(x + lane.bounds.width / 2, y + rowHeight / 2),
                        emptyLane = true)
                }
                if (attackerSlots) slots += CombatSlot(CombatRole.ATTACKER, TableBounds(x + (lane.bounds.width - cardWidth) / 2,
                    rowY(CombatRole.ATTACKER) + margin, cardWidth, cardHeight), null, target)
                x += lane.bounds.width
                continue
            }
            val pad = lanePads[target] ?: 0f
            val room = slotRoom(members)
            x += pad + room
            for ((n, pair) in members.withIndex()) {
                val index = edges.size
                // Between pairs; a lane's first pair, after its free space, has the lane separator before it instead
                if (index > 0) dividers += if (n == 0) x - pad - room else x
                val pairWidth = pairWidths.getValue(pair.id)
                blockerRows[pair.id] = TableBounds(x, rowY(CombatRole.BLOCKER), pairWidth, rowHeight)
                // The lane's first and last pairs also take drops in the free space at its ends
                val before = if (n == 0) pad + room else 0f
                val after = if (n == members.lastIndex && !attackerSlots) pad else 0f
                // CMB-034: a blocker's slot over the attackers of a pair no blocker sits in
                if (pair.blockers.isEmpty()) slots += CombatSlot(CombatRole.BLOCKER, TableBounds(x + (pairWidth - cardWidth) / 2,
                    rowY(CombatRole.BLOCKER) + margin, cardWidth, cardHeight), pair.id, pair.attackTarget)
                for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
                    val side = pair.side(role)
                    val y = rowY(role)
                    var left = x + (pairWidth - rowWidth(side.size, cardWidth)) / 2
                    targets += CombatDropRegion("Append ${role.name.lowercase()} to pair ${index + 1}",
                        TableBounds(x - before, y, pairWidth + before + after, rowHeight),
                        CombatDestination(area.id, role, pair.id, attackTarget = pair.attackTarget),
                        landing = TablePoint(left + side.size * (cardWidth + margin), y + rowHeight / 2))
                    for (occurrence in side) {
                        // [drawable] left out every copy the board can't show
                        val card = CombatStackRules.inspection(board, occurrence.id)?.copy(tapped = false) ?: continue
                        cards += OccurrenceDrawing(occurrence.id, card,
                            TableBounds(left, y + margin, cardWidth, cardHeight), true)
                        left += cardWidth + margin
                    }
                }
                edges += x to x + pairWidth
                x += pairWidth
            }
            // CMB-028: the slot at the lane's end is a drop of its own: a new pair attacking the lane's target
            if (attackerSlots) {
                val y = rowY(CombatRole.ATTACKER)
                val left = x + (attackerSlot - cardWidth) / 2
                slots += CombatSlot(CombatRole.ATTACKER, TableBounds(left, y + margin, cardWidth, cardHeight), null, target)
                targets += CombatDropRegion("New attacker pair at the end of the lane", TableBounds(x, y, attackerSlot + pad, rowHeight),
                    CombatDestination(area.id, CombatRole.ATTACKER, pairGap = IdentityGap(area.pairs.lastOrNull()?.id, null), attackTarget = target),
                    landing = TablePoint(left + cardWidth / 2, y + rowHeight / 2), exact = true)
                x += attackerSlot
            }
            x += pad
        }
        if (pairs.isNotEmpty()) {
            // CMB-007: the area's end gaps extend outward only when no empty lane lies beyond them.
            insertion(null, pairs.first().id, edges.first().first, left = true, extend = lanes.first().cardId == pairs.first().attackTarget)
            insertion(pairs.last().id, null, edges.last().second, right = true, extend = lanes.last().cardId == pairs.last().attackTarget)
        }
        return CombatGeometry(x, labelHeight + rowHeight * 2 + gap, cards, dividers, targets,
            lanes.map { lane ->
                val top = if (mirrored) rowHeight * 2 + gap else 0f
                // Mirrored, the chips sit at the very bottom (by the defender's battlefield)
                headed(lane.copy(bounds = lane.bounds.copy(y = top, height = labelHeight)), top + CHIP_PAD)
            },
            laneDividers, labelHeight, mirrored, slots, TableBounds(0f, if (mirrored) rowHeight else labelHeight + rowHeight, x, gap),
            blockerRows)
    }

    /**
     * Problem reports WCRF3Q, R8XVPA: [area] without its combat copies whose card [board] doesn't have (a stale pair: a
     * blocker or attacker that is gone), and without the pairs that leaves empty, so the table still draws; each copy left
     * out is logged once as an error with its pair, the step and the board's point [head].
     */
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    internal fun drawable(board: BoardState, area: CombatArea, head: String): CombatArea {
        val missing = CombatStackRules.occurrences(area).filter { CombatStackRules.inspection(board, it.id) == null }
        if (missing.isEmpty()) return area
        for (occurrence in missing) {
            val key = "$head/${occurrence.id}"
            val seen = leftOut.load()
            if (key in seen) continue
            leftOut.store((seen + key).toList().takeLast(50).toSet())
            val pair = area.pairs.firstOrNull { p -> (p.attackers + p.blockers).any { it.id == occurrence.id } }
            AppLog.error("combat", "Combat copy left out, its card isn't on the board: occurrence ${occurrence.id} of " +
                "${occurrence.sourceId} in pair $pair; turn ${board.turn.number} ${board.turn.phase} ${board.turn.step}, " +
                "active ${board.turn.accountId}, head $head; pairs ${area.pairs}")
        }
        val gone = missing.map { it.id }.toSet()
        return area.copy(pairs = area.pairs.map { p ->
            p.copy(attackers = p.attackers.filterNot { it.id in gone }, blockers = p.blockers.filterNot { it.id in gone })
        }.filter { it.attackers.isNotEmpty() || it.blockers.isNotEmpty() })
    }
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val leftOut = kotlin.concurrent.atomics.AtomicReference<Set<String>>(emptySet())

    /** [lane] with its chip (its name and value, CMB-013) centred in the lane at [chipY]. */
    private fun headed(lane: CombatLaneLabel, chipY: Float): CombatLaneLabel {
        val width = chipWidth(lane.label, lane.value)
        return lane.copy(chip = TableBounds(lane.bounds.x + (lane.bounds.width - width) / 2, chipY, width, CHIP_HEIGHT))
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
            ?.faces?.find { it.faceId == card.faceId }?.name
            ?: Faults.placeholder("CombatStackPresentation", "combat copy without its card's name", "Card", mapOf("card" to card.id))
        return when {
            drawing.frozen -> "Effect, $name, retained source, read-only"
            drawing.projection -> "Projection, $name"
            else -> name
        }
    }
}

/** The stand-in of an attack target whose card data is missing (a fault: reported). */
private fun unnamedTarget(card: String?): String =
    Faults.placeholder("CombatStackPresentation", "attack target without its card's name", "Card", mapOf("card" to card.toString()))
