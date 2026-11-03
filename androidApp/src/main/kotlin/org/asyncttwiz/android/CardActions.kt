package org.asyncttwiz.android

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import org.asyncttwiz.protocol.*

/**
 * A card's menu (TBL-115, TBL-116; battlefield card, combat copy, Graveyard or Exile panel card): what Automatum offers
 * for it as rows, then only Inspect, Other side and the combat draft's Attack → and Remove from combat. Nothing changes
 * the game by hand (I-D52). A hand card has no menu (ZON-079).
 */
@Composable
internal fun EngineCardMenu(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture, names: Map<String, String>,
    enabled: Boolean) {
    val view = bound.contextViewId
    val card = CombatStackRules.liveSource(play.board, view) ?: return
    val viewer = toolkit.viewer
    val layout = CardRingPages.context(play.board, view) ?: CardMenuLayout.CARD
    if (layout == CardMenuLayout.HAND) return
    val faces = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
    val other = if (card.covered) faces.singleOrNull { it.faceId == card.faceId } else faces.firstOrNull { it.faceId != card.faceId }
    val combat = bound.viewIds.filter { id -> CombatStackRules.occurrences(play.board.combat).any { it.id == id } }
    // A battlefield card's own "Attack →" retargets its attacker copies, exactly like the copy's
    val attackers = play.board.combat?.pairs.orEmpty().flatMap { it.attackers }.filter { it.sourceId == card.id }.map { it.id }
    fun entry(control: MenuControl, id: String = control.name) = RingEntry(id, ringShortLabel(control.name), control)
    val entries = listOf(
        entry(MenuControl("Inspect") { toolkit.inspect(view) }, "inspect"),
        // The deliberate, logged way to see another face, also an opponent's face-down front (TBL-088)
        entry(MenuControl("Inspect other side", enabled && other != null) {
            other?.let { toolkit.submit(PlayAction.InspectFace(card.id, it.faceId, card.containerId)) }
        }, "otherSide").copy(label = "Other side"),
        if (layout == CardMenuLayout.COMBAT)
            entry(MenuControl("Attack →", enabled && CombatStackController.attackerPairs(play.board, bound.viewIds).isNotEmpty()) {
                toolkit.openAttackTargets() })
        else entry(MenuControl("Attack →", enabled && attackers.isNotEmpty()) { toolkit.openAttackTargets(attackers) }),
        entry(MenuControl("Remove from combat", enabled && combat.isNotEmpty()) {
            if (toolkit.removeFromCombat(bound.copy(viewIds = combat))) toolkit.dismiss()
        }))
    val ownerColor = if (card.owner == viewer) OwnTableColor else OpponentTableColor
    val rows = automatumRows(toolkit, play, bound, names, enabled)
    val pages = CardRingPages.automatumPages(layout)
    val anchors = LocalCardRingAnchors.current
    if (view in anchors) CardActionRing(play.gameId, viewer, card.id, view, entries, card.owner == viewer, ownerColor,
        toolkit::dismiss, layout, pages, rows, remembered = false)
    else BoxWithConstraints(Modifier.fillMaxSize()) {
        // A card without a table drawing: the menu centres on the screen
        val anchor = TableBounds(maxWidth.value / 2 - 24, maxHeight.value / 2 - 50, 48f, 100f)
        CompositionLocalProvider(LocalCardRingAnchors provides (anchors + (view to anchor))) {
            CardActionRing(play.gameId, viewer, card.id, view, entries, card.owner == viewer, ownerColor, toolkit::dismiss,
                layout, pages, rows, remembered = false)
        }
    }
}

/** One pointer recognizer owns click/assigned hold/name tooltip. Sync updates callbacks, not the gesture. */
@Composable
internal fun ManualActionSquare(name: String, symbol: String, enabled: Boolean = true, fill: Color? = null,
    selected: Boolean = false, circle: Boolean = false, tooltipName: String = name,
    assignedHold: (() -> Unit)? = null, holdName: String? = null, iconTint: Color? = null,
    bordered: Boolean = true, modifier: Modifier = Modifier, labelled: Boolean = false,
    refused: String? = null, onClick: () -> Unit) {
    // I-D46: a refused square stays greyed; a tap shakes it and holding it says why
    val reason = refused.takeIf { !enabled }
    val tappable = enabled || reason != null
    var shakes by remember { mutableIntStateOf(0) }
    val currentClick by rememberUpdatedState(if (reason != null) { { shakes += 1 } } else onClick)
    val currentHold by rememberUpdatedState(assignedHold)
    var tooltip by remember { mutableStateOf(false) }
    val background = fill ?: Color.White
    Box(modifier.shaken(shakes).then(if (labelled) Modifier.fillMaxWidth().heightIn(min = 48.dp) else Modifier.size(48.dp)).ringInputTarget()
        .pressFeedback(enabled).semantics(mergeDescendants = true) {
        contentDescription = name
        role = Role.Button
        this.selected = selected
        if (!enabled) disabled()
        onClick { if (tappable) currentClick(); tappable }
        if (assignedHold != null) onLongClick(holdName) { if (enabled) currentHold?.invoke(); enabled }
    }.onKeyEvent {
        if (enabled && it.key in listOf(Key.Enter, Key.Spacebar, Key.DirectionCenter)) {
            if (it.type == KeyEventType.KeyUp) currentClick()
            true
        } else false
    }.focusable(enabled).pointerInput(enabled, reason) {
        val press = ManualActionPress()
        awaitEachGesture {
            val down = awaitFirstDown()
            val capturedClick = currentClick
            val capturedHold = currentHold
            down.consume()
            press.start()
            try {
                var released = false
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            press.cancel(); break
                        }
                        if (!change.pressed) { released = true; change.consume(); break }
                    }
                }
                if (released) { if (press.release() && tappable) capturedClick() }
                else if (!press.consumed) {
                    press.hold(if (enabled) capturedHold else null)
                    tooltip = press.tooltip
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || change.isConsumed ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        change.consume()
                    }
                    press.cancel()
                }
            } finally { tooltip = false }
        }
    }, contentAlignment = Alignment.Center) {
        if (labelled) {
            val tint = (if (fill == null) MenuInk else Color.White).copy(alpha = if (enabled) 1f else .38f)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(background, MaterialTheme.shapes.extraSmall)
                .border(1.dp, MenuBorder, MaterialTheme.shapes.extraSmall).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuIcon(name, tint = tint)
                Text(name, Modifier.weight(1f), color = tint, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
        Box(Modifier.fillMaxSize().then(if (bordered) Modifier.border(if (selected) 2.dp else 1.dp,
            if (selected) iconTint ?: OwnTableColor else MenuBorder, MaterialTheme.shapes.extraSmall) else Modifier)
            .background(Color.White, MaterialTheme.shapes.extraSmall), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (circle) 32.dp else 44.dp)
            .background(background.copy(alpha = if (circle) .25f else if (enabled) 1f else .4f), if (circle) CircleShape else MaterialTheme.shapes.extraSmall)
            .then(if (circle) Modifier.border(1.dp, iconTint ?: MenuBorder, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            val tint = (if (circle) MenuInk else iconTint ?: MenuInk).copy(alpha = if (enabled) 1f else .38f)
            if (name.startsWith("Set ") || name.startsWith("Adjust corner") || name.startsWith("Adjust integer"))
                Text(symbol, Modifier.padding(2.dp), color = tint, maxLines = 2, overflow = TextOverflow.Clip,
                    fontSize = if (symbol.length > 7) 10.sp else if (name.startsWith("Set ") && !circle) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold)
            else MenuIcon(name, tint = tint)
        }
        }
        }
        if (tooltip) HoldTooltip(reason ?: tooltipName)
    }
}

