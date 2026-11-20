package org.asyncttwiz.android

import org.asyncttwiz.protocol.Faults
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.asyncttwiz.automatum.PlayerNames
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
object StackLine {
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
        val who = if (item.controller == viewer) "You" else PlayerNames.of(names, item.controller, "The other player", "StackLine")
        // STK-212: the viewer's own entry is "Your copy of …", never "You's"
        val whose = if (item.controller == viewer) "Your" else "$who's"
        val card = CombatStackRules.inspection(play.board, top)
        val visible = card != null && !effect.faceDown && (effect.frozen != null || CardVisibility.isVisibleFaceUp(play.board, card, viewer))
        val face = if (!visible || card == null) null else play.definitions.firstOrNull { it.digest == card.definition }?.definition?.faces
            ?.let { faces -> faces.firstOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }
        val name = face?.shownName?.takeIf { it.isNotBlank() }
        val ability = if (visible) StackAbilities.of(play, top, viewer) else null
        val head = when (item.kind) {
            MtgStackKind.Spell -> "$who cast ${name ?: unnamed(visible, "a card", top)}"
            MtgStackKind.SpellCopy -> "$whose copy of ${name ?: unnamed(visible, "a spell", top)}"
            // D7: a copied ability by what it copies ("Copy of Bre of Clan Stoutarm's triggered ability")
            MtgStackKind.Activated, MtgStackKind.Triggered -> ability?.let { if (it.copy) it.name else "${it.source}'s ${it.kind.replaceFirstChar { c -> c.lowercase() }}" }
                ?: unnamed(visible, "$whose ability", top)
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
                face.rulesText.lines().firstOrNull { it.isNotBlank() }?.let { StackTargetText.controllerWords(item, StackAbilityText.short(it, name), viewer, names) }
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
data class StackTargetMarks(val entry: String, val own: Boolean, val items: List<String>, val stack: Boolean, val label: String)

object StackTargets {
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
fun TableBounds.edgeToward(toward: TablePoint, gap: Float = 0f): TablePoint {
    val c = center
    val dx = toward.x - c.x; val dy = toward.y - c.y
    if (dx == 0f && dy == 0f) return c
    val sx = if (dx == 0f) Float.MAX_VALUE else (width / 2 + gap) / kotlin.math.abs(dx)
    val sy = if (dy == 0f) Float.MAX_VALUE else (height / 2 + gap) / kotlin.math.abs(dy)
    val s = minOf(sx, sy, 1f)
    return TablePoint(c.x + dx * s, c.y + dy * s)
}

/** STK-058, Stack animation step 5: the last arrows drawn of a top entry ([entry]), in dp: each target's bounds and the
 * arrow's ends, the ringed Stack, and their colour; kept so they fade out as the entry resolves. */
private class DrawnArrows(val entry: String, val color: Color) {
    var targets: List<TableBounds> = emptyList()
    var arrows: List<Pair<TablePoint, TablePoint>> = emptyList()
    var ring: TableBounds? = null
}

/**
 * STK-058: the top Stack entry's target arrows, for both players: from the Stack to each target on the table, in the
 * entry's controller's colour, with a soft highlight round each target in the same colour (not the violet of picking
 * targets, STK-047); a target that is another Stack entry rings the Stack itself. Drawn only: taps reach the table.
 * Stack animation (owner spec 2026-11-14): on the live table [animation] grows them (step 4: each from the entry to its
 * target, several [StackSchedule.ARROW_STAGGER_MILLIS] apart, each target's highlight fading in as its arrow arrives; none
 * before the step starts), and when the entry leaves the Stack they fade out as its effect plays (step 5); in timeline mode
 * (no [animation]) they are simply drawn.
 */
@Composable
fun StackTargetArrows(play: TableBoard, scene: TableScene, viewer: String, names: Map<String, String>,
    animation: StackAnimation? = null, multiplier: Float = 1f, bounds: (TableItem) -> TableBounds) {
    val marks = StackTargets.of(play, scene, viewer, names)
    val stack = scene.stackRegion ?: scene.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.bounds
    // Step 5: the arrows of an entry that left the Stack fade where they were drawn last
    val last = remember { arrayOfNulls<DrawnArrows>(1) }
    val fading = remember { mutableStateOf<DrawnArrows?>(null) }
    val fade = remember { androidx.compose.animation.core.Animatable(1f) }
    val previous = last[0]
    if (animation != null && previous != null && previous.entry != marks?.entry &&
        previous.entry !in CombatStackRules.stack(play.board).cards && fading.value !== previous) {
        last[0] = null
        fading.value = previous
    }
    fading.value?.let { gone -> LaunchedEffect(gone) {
        // A take-back or a board jumped to leaves nothing of them on screen
        if (animation?.takenAsIs == true) { if (fading.value === gone) fading.value = null; return@LaunchedEffect }
        fade.snapTo(1f)
        fade.animateTo(0f, androidx.compose.animation.core.tween(
            (StackSchedule.ARROW_FADE_MILLIS / cardMoveMultiplier(multiplier)).toInt().coerceAtLeast(1)))
        if (fading.value === gone) fading.value = null
    } }
    fading.value?.let { gone ->
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            val a = fade.value
            for (b in gone.targets) highlight(b, gone.color, d, a)
            for ((from, to) in gone.arrows) targetArrow(Offset(from.x * d, from.y * d), Offset(to.x * d, to.y * d), gone.color, d, a)
            gone.ring?.let { highlight(it, gone.color, d, a) }
        }
    }
    if (marks == null || stack == null) return
    val color = if (marks.own) OwnTableColor else OpponentTableColor
    val targets = marks.items.mapNotNull { id -> scene.items.firstOrNull { it.id == id } }
    val drawn = remember(marks.entry, color) { DrawnArrows(marks.entry, color) }
    SideEffect { last[0] = drawn }
    Canvas(Modifier.fillMaxSize().testTag("stack-target-arrows").semantics { contentDescription = marks.label }) {
        val d = density
        // Where each target is drawn now (read while drawing, so a moving card redraws only the canvas); step 4's growth too
        val now = targets.map(bounds)
        val arrows = ArrayList<Pair<TablePoint, TablePoint>>(now.size)
        for ((i, b) in now.withIndex()) {
            val grown = animation?.arrowProgress(marks.entry, i) ?: 1f
            val from = stack.edgeToward(b.center)
            val to = b.edgeToward(stack.center, 3f)
            arrows += from to to
            if (grown <= 0f) continue
            highlight(b, color, d, grown)
            val tip = TablePoint(from.x + (to.x - from.x) * grown, from.y + (to.y - from.y) * grown)
            targetArrow(Offset(from.x * d, from.y * d), Offset(tip.x * d, tip.y * d), color, d)
        }
        val ringed = marks.stack && (animation?.arrowProgress(marks.entry, now.size) ?: 1f) > 0f
        if (ringed) highlight(stack, color, d, animation?.arrowProgress(marks.entry, now.size) ?: 1f)
        drawn.targets = now
        drawn.arrows = arrows
        drawn.ring = stack.takeIf { marks.stack }
    }
}

/** A soft highlight round [b] (dp) in [color]: a wide faint halo and a thin line; [alpha] fades it in or out. */
private fun DrawScope.highlight(b: TableBounds, color: Color, d: Float, alpha: Float = 1f) {
    drawRoundRect(color.copy(alpha = .22f * alpha), Offset((b.x - 4f) * d, (b.y - 4f) * d), Size((b.width + 8f) * d, (b.height + 8f) * d),
        CornerRadius(6f * d), style = Stroke(5f * d))
    drawRoundRect(color.copy(alpha = .85f * alpha), Offset((b.x - 2f) * d, (b.y - 2f) * d), Size((b.width + 4f) * d, (b.height + 4f) * d),
        CornerRadius(4f * d), style = Stroke(1.5f * d))
}

/** An arrow from [start] to [end] (px), its head at [end]: as a block's arrow (CMB-031), a little wider, with a white
 * edge so it reads over cards; [alpha] fades it out. */
private fun DrawScope.targetArrow(start: Offset, end: Offset, color: Color, d: Float, alpha: Float = 1f) {
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
    drawLine(Color.White.copy(alpha = .8f * alpha), start, base, 4.5f * d, cap = StrokeCap.Round)
    drawPath(tip, Color.White.copy(alpha = .8f * alpha), style = Stroke(2f * d))
    drawLine(color.copy(alpha = .9f * alpha), start, base, 2.5f * d, cap = StrokeCap.Round)
    drawPath(tip, color.copy(alpha = color.alpha * alpha))
}

/** [standIn] for a Stack entry without a name: a hidden one by design, a visible one a fault (reported). */
private fun unnamed(visible: Boolean, standIn: String, entry: String): String =
    if (!visible) standIn else Faults.placeholder("StackLine", "visible entry without a name", standIn, mapOf("entry" to entry))
