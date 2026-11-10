package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.asyncttwiz.automatum.StackAbilityText
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStackItem
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PlayZone

/**
 * STK-057 (owner playtest 2026-11-07, Fractured Sanity): the line saying what the other player's top Stack entry is while
 * you hold priority over it: "Bob cast Fractured Sanity · each opponent mills 14 cards", "Fractured Sanity's cycling
 * trigger · each opponent mills 4 cards", with its chosen modes, X and targets: each target named where its text names it
 * (owner 2026-11-07: "Bob cast Storm Spark · deals 3 damage to Ember Cub", the name in its controller's colour), else after
 * it ("… → Ember Herald"). Hidden cards are never named (HIS-062: "a card").
 */
internal object StackLine {
    /** The line's words for [viewer] on [play] ([pieces]), or null. */
    fun text(play: TableBoard, viewer: String, names: Map<String, String>): String? = pieces(play, viewer, names)?.plain()

    /** The line for [viewer] on [play] as pieces (target names carry their controllers), or null: nothing on the Stack, the
     * top entry is their own, or they don't hold priority. [names]: the players' names by account id. */
    fun pieces(play: TableBoard, viewer: String, names: Map<String, String>): List<StackTextPiece>? {
        val top = CombatStackRules.stack(play.board).cards.firstOrNull() ?: return null
        val item = play.board.stackEffects.singleOrNull { it.id == top }?.item ?: return null
        if (item.controller == viewer || play.board.priority?.holder != viewer) return null
        return entryPieces(play, top, viewer, names)
    }

    /** STK-057 (owner, 2026-11-09): the line as the band shows it: its main part ("Bob cast Storm Spark") and the rest under
     * it, smaller ("deals 3 damage to Ember Cub", with X and the targets that follow it), empty when there is none. */
    fun split(pieces: List<StackTextPiece>): Pair<List<StackTextPiece>, List<StackTextPiece>> {
        val rest = pieces.drop(1).toMutableList()
        rest.firstOrNull()?.let { first ->
            val text = first.text.removePrefix(" · ").trimStart()
            if (text.isEmpty()) rest.removeAt(0) else rest[0] = first.copy(text = text)
        }
        return pieces.take(1) to rest
    }

    /** The line's words for the Stack entry [top] as [viewer] sees it, whoever controls it and holds priority (HIS-112: a
     * play shown names it so), or null when it isn't an entry Automatum made. */
    fun entryPieces(play: TableBoard, top: String, viewer: String, names: Map<String, String>): List<StackTextPiece>? {
        val effect = play.board.stackEffects.singleOrNull { it.id == top } ?: return null
        val item = effect.item ?: return null
        val who = if (item.controller == viewer) "You" else names[item.controller] ?: "The other player"
        val card = CombatStackRules.inspection(play.board, top)
        val visible = card != null && !effect.faceDown && (effect.frozen != null || CardVisibility.isVisibleFaceUp(play.board, card, viewer))
        val face = if (!visible) null else play.definitions.firstOrNull { it.digest == card!!.definition }?.definition?.faces
            ?.let { faces -> faces.firstOrNull { it.faceId == card!!.faceId } ?: faces.firstOrNull() }
        val name = face?.name?.takeIf { it.isNotBlank() }
        val ability = if (visible) StackAbilities.of(play, top, viewer) else null
        val head = when (item.kind) {
            MtgStackKind.Spell -> "$who cast ${name ?: "a card"}"
            MtgStackKind.SpellCopy -> "$who's copy of ${name ?: "a spell"}"
            MtgStackKind.Activated, MtgStackKind.Triggered -> ability?.let { "${it.source}'s ${it.kind.replaceFirstChar { c -> c.lowercase() }}" }
                ?: "$who's ability"
        }
        val engine = if (visible) StackAbilityText.decoded(item) else null
        // The effect with its targets in place (owner 2026-11-07); the targets it doesn't name follow it
        val targeted = if (engine != null) StackAbilityText.targetedEffect(engine, face?.rulesText, name) else null
        val shortEffect: List<StackTextPiece>? = when {
            !visible -> null
            targeted != null -> StackTargetText.pieces(play, item, targeted, viewer, names)
            engine != null -> null
            // A spell without the engine's data (a board made by hand): its first printed line, for an instant or sorcery
            item.kind == MtgStackKind.Spell && face != null && ("Instant" in face.typeLine || "Sorcery" in face.typeLine) ->
                face.rulesText.lines().firstOrNull { it.isNotBlank() }?.let { listOf(StackTextPiece(StackAbilityText.short(it, name))) }
            else -> null
        }?.takeIf { it.plain().isNotEmpty() }
        // X shows filled into the effect; where the effect doesn't say it, after it
        val x = item.values.filterKeys { engine == null || it != "X" || !StackAbilityText.mentionsX(engine, face?.rulesText) }
            .map { (n, v) -> "$n = $v" }
        val unplaced = if (targeted != null) StackTargetText.unplaced(item, targeted) else item.targets.values.flatten()
        val pieces = mutableListOf(StackTextPiece(head))
        if (shortEffect != null) { pieces += StackTextPiece(" · "); pieces += shortEffect }
        x.forEach { pieces += StackTextPiece(" · $it") }
        if (unplaced.isNotEmpty()) {
            pieces += StackTextPiece(" → ")
            unplaced.forEachIndexed { i, ref ->
                if (i > 0) pieces += StackTextPiece(", ")
                pieces += StackTextPiece(StackAbilities.targetName(play, ref, viewer, names, you = "you", hidden = "a card"),
                    StackTargetText.controller(play, ref))
            }
        }
        return pieces
    }
}

/**
 * STK-058 (owner playtest 2026-11-07): where the top Stack entry's targets are on the table: [items] the scene items of
 * the targets shown there (a battlefield card, a Graveyard or Exile pile holding one, a player's life square), [stack]
 * when one is another Stack entry; [own] whether the viewer controls it. Lower entries' targets show only in the Stack
 * list (STK-018).
 */
internal data class StackTargetMarks(val entry: String, val own: Boolean, val items: List<String>, val stack: Boolean, val label: String)

internal object StackTargets {
    /** The marks of [play]'s top Stack entry on [scene] for [viewer], or null when it has no targets. */
    fun of(play: TableBoard, scene: TableScene, viewer: String, names: Map<String, String>): StackTargetMarks? {
        val top = CombatStackRules.stack(play.board).cards.firstOrNull() ?: return null
        val item: MtgStackItem = play.board.stackEffects.singleOrNull { it.id == top }?.item ?: return null
        val targets = item.targets.values.flatten().distinct().takeIf { it.isNotEmpty() } ?: return null
        val entries = play.board.stackEffects.filter { it.item != null }
        var stack = false
        val items = targets.mapNotNull { ref ->
            if (ref is MtgRef.StackItem || entries.any { it.item?.id == ref }) { stack = true; return@mapNotNull null }
            when (ref) {
                is MtgRef.Player -> scene.items.firstOrNull { it.id == "life/${ref.accountId}" }?.id
                is MtgRef.Card -> scene.items.firstOrNull { it.cardId == ref.cardId && it.zone == PlayZone.BATTLEFIELD && !it.projection &&
                    it.viewport != "combat" && it.kind == TableItemKind.CARD && it.visible.isNotEmpty() }?.id
                    ?: play.board.cards.singleOrNull { it.id == ref.cardId }?.containerId?.let { pile ->
                        scene.items.firstOrNull { it.id == pile && it.kind == TableItemKind.PILE &&
                            (it.zone == PlayZone.GRAVEYARD || it.zone == PlayZone.EXILE) }?.id }
                is MtgRef.StackItem -> null
            }
        }.distinct()
        val label = "Targets of the top Stack entry: " + targets.joinToString(", ") {
            StackAbilities.targetName(play, it, viewer, names, you = "you", hidden = "a card") }
        return StackTargetMarks(top, item.controller == viewer, items, stack, label)
    }
}

/** The point where the line from [this] rectangle's centre towards [toward] leaves it, [gap] beyond its edge. */
internal fun TableBounds.edgeToward(toward: TablePoint, gap: Float = 0f): TablePoint {
    val c = center
    val dx = toward.x - c.x; val dy = toward.y - c.y
    if (dx == 0f && dy == 0f) return c
    val sx = if (dx == 0f) Float.MAX_VALUE else (width / 2 + gap) / kotlin.math.abs(dx)
    val sy = if (dy == 0f) Float.MAX_VALUE else (height / 2 + gap) / kotlin.math.abs(dy)
    val s = minOf(sx, sy, 1f)
    return TablePoint(c.x + dx * s, c.y + dy * s)
}

/**
 * STK-058: the top Stack entry's target arrows, for both players: from the Stack to each target on the table, in the
 * entry's controller's colour, with a soft highlight round each target in the same colour (not the violet of picking
 * targets, STK-047); a target that is another Stack entry rings the Stack itself. Drawn only: taps reach the table.
 */
@Composable
internal fun StackTargetArrows(play: TableBoard, scene: TableScene, viewer: String, names: Map<String, String>,
    bounds: (TableItem) -> TableBounds) {
    val marks = StackTargets.of(play, scene, viewer, names) ?: return
    val stack = scene.stackRegion ?: scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.bounds ?: return
    val color = if (marks.own) OwnTableColor else OpponentTableColor
    val targets = marks.items.mapNotNull { id -> scene.items.firstOrNull { it.id == id } }
    Canvas(Modifier.fillMaxSize().testTag("stack-target-arrows").semantics { contentDescription = marks.label }) {
        val d = density
        // Where each target is drawn now (read while drawing, so a moving card redraws only the canvas)
        for (b in targets.map(bounds)) {
            highlight(b, color, d)
            val from = stack.edgeToward(b.center)
            val to = b.edgeToward(stack.center, 3f)
            targetArrow(Offset(from.x * d, from.y * d), Offset(to.x * d, to.y * d), color, d)
        }
        if (marks.stack) highlight(stack, color, d)
    }
}

/** A soft highlight round [b] (dp) in [color]: a wide faint halo and a thin line. */
private fun DrawScope.highlight(b: TableBounds, color: Color, d: Float) {
    drawRoundRect(color.copy(alpha = .22f), Offset((b.x - 4f) * d, (b.y - 4f) * d), Size((b.width + 8f) * d, (b.height + 8f) * d),
        CornerRadius(6f * d), style = Stroke(5f * d))
    drawRoundRect(color.copy(alpha = .85f), Offset((b.x - 2f) * d, (b.y - 2f) * d), Size((b.width + 4f) * d, (b.height + 4f) * d),
        CornerRadius(4f * d), style = Stroke(1.5f * d))
}

/** An arrow from [start] to [end] (px), its head at [end]: as a block's arrow (CMB-031), a little wider, with a white
 * edge so it reads over cards. */
private fun DrawScope.targetArrow(start: Offset, end: Offset, color: Color, d: Float) {
    val dx = end.x - start.x; val dy = end.y - start.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy).takeIf { it > 1f } ?: return
    val ux = dx / length; val uy = dy / length
    val head = minOf(9f * d, length / 2)
    val base = Offset(end.x - ux * head, end.y - uy * head)
    val tip = Path().apply {
        moveTo(end.x, end.y)
        lineTo(base.x - uy * head * .6f, base.y + ux * head * .6f)
        lineTo(base.x + uy * head * .6f, base.y - ux * head * .6f)
        close()
    }
    drawLine(Color.White.copy(alpha = .8f), start, base, 4.5f * d, cap = StrokeCap.Round)
    drawPath(tip, Color.White.copy(alpha = .8f), style = Stroke(2f * d))
    drawLine(color.copy(alpha = .9f), start, base, 2.5f * d, cap = StrokeCap.Round)
    drawPath(tip, color)
}
