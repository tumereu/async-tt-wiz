package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

data class QuickContext(val selectMode: Boolean = false, val selectedSourceIds: List<String> = emptyList(),
    val combatAreaId: String? = null, val stackTarget: ExplicitStackTarget? = null,
    val playable: Boolean = true, val viewer: String? = null)
data class QuickAction(val name: String, val glyph: String, val intent: TableActionIntent, val badge: String? = null)

/** Complete G11/G12 catalog. Phase offerings precede an explicit Stack target; never guess top. */
object QuickActions {
    const val contextualPositions = 3
    fun contextual(play: PlayState, context: QuickContext, controller: TableActionController): List<QuickAction> {
        if (!context.playable) return emptyList()
        val scope = TableActionScope(play.gameId, play.id)
        val result = mutableListOf<QuickAction>()
        fun add(name: String, glyph: String, intent: TableActionIntent?, badge: String? = null) {
            if (intent != null) result += QuickAction(name, glyph, intent, badge)
        }
        fun bundle(vararg actions: TableActionIntent?) = if (actions.any { it == null }) null
            else TableActionIntent.Bundle(scope, actions.filterNotNull())
        fun choose(position: TurnPosition) = TableActionIntent.Choose(scope, position)
        val turnPlayer = context.viewer == play.board.turn.accountId
        if (turnPlayer) when (TurnPosition.of(play.board.turn)) {
            TurnPosition.UNTAP -> {
                add("Untap all", "↶", controller.untap(play, false))
                add("Untap all + next", "↶", controller.untap(play, true), "↦")
                add("Start turn", "", bundle(controller.untap(play, false), controller.draw(play, false),
                    choose(TurnPosition.PRECOMBAT_MAIN)))
                if (play.board.turn.number == "1") {
                    add("Return Hand → Shuffle → Draw N", "⇄", controller.library(play, LibraryTool.MULLIGAN_N), "↓")
                    add("Return Hand → Shuffle → Draw previous Hand size −1", "⇄",
                        controller.library(play, LibraryTool.MULLIGAN_MINUS_ONE), "−1")
                }
            }
            TurnPosition.UPKEEP -> add("Draw + go to main", "", bundle(controller.draw(play, false), choose(TurnPosition.PRECOMBAT_MAIN)))
            TurnPosition.DRAW -> {
                add("Draw one", "↓", controller.draw(play, false))
                add("Draw one + next", "↓", controller.draw(play, true), "↦")
            }
            TurnPosition.PRECOMBAT_MAIN -> {
                add("Go to attacks", "", TableActionIntent.SelectAttackers(scope, advance = true))
                add("Skip combat", "", choose(TurnPosition.END_STEP))
            }
            TurnPosition.DECLARE_ATTACKERS -> if (context.selectedSourceIds.isNotEmpty()) {
                add("Declare + tap", "⚔", TableActionIntent.Combat(scope, CombatTool.DECLARE_AND_TAP,
                    context.combatAreaId, context.selectedSourceIds.distinct()), "↷")
                add("Declare without tapping", "⚔", TableActionIntent.Combat(scope, CombatTool.DECLARE,
                    context.combatAreaId, context.selectedSourceIds.distinct()))
            } else {
                add("Select attackers", "", TableActionIntent.SelectAttackers(scope, advance = false))
                add("No attack", "", TableActionIntent.Combat(scope, CombatTool.NO_ATTACK, context.combatAreaId))
            }
            TurnPosition.END_COMBAT -> add("Advance + close combat", "▱",
                TableActionIntent.Combat(scope, CombatTool.ADVANCE_AND_CLOSE, context.combatAreaId), "↦")
            TurnPosition.POSTCOMBAT_MAIN, TurnPosition.END_STEP -> add("End turn", "", TableActionIntent.End(scope))
            else -> Unit
        }
        if (!turnPlayer && play.board.players.any { it.accountId == context.viewer } &&
            TurnPosition.of(play.board.turn) == TurnPosition.DECLARE_BLOCKERS)
            add("No blocks + next", "", TableActionIntent.Next(scope))
        context.stackTarget?.let { target ->
            val (label, glyph) = when (target.kind) {
                StackTargetKind.INSTANT_OR_SORCERY -> "Move to Graveyard" to "▣"
                StackTargetKind.PERMANENT -> "Move to Battlefield" to "▤"
                StackTargetKind.EFFECT -> "Remove this effect" to "▱"
            }
            add(label, glyph, TableActionIntent.Stack(scope, target))
        }
        return result
    }
}

enum class PressAction { TAP, HOLD, TOOLTIP }

/** Same arbitration for pointer controls and focused host tests. Each press has one terminal action. */
class ActionPress(private val assignedHold: Boolean) {
    var consumed = false
        private set
    fun hold(): PressAction? {
        if (consumed) return null
        consumed = true
        return if (assignedHold) PressAction.HOLD else PressAction.TOOLTIP
    }
    fun release(): PressAction? {
        if (consumed) return null
        consumed = true
        return PressAction.TAP
    }
    fun cancel() { consumed = true }
}

/** Three pannable contextual positions plus the permanently reserved tap/hold progression square. */
@Composable
fun QuickActionStrip(play: PlayState, context: QuickContext, controller: TableActionController,
    endingPlayerColor: Color, modifier: Modifier = Modifier, perform: (TableActionIntent) -> Boolean = controller::perform) {
    if (!context.playable) return
    val actions = QuickActions.contextual(play, context, controller)
    val scope = TableActionScope(play.gameId, play.id)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Only the contextual rail scrolls; overflow never displaces the fourth square.
        Row(Modifier.width(144.dp).horizontalScroll(rememberScrollState())) {
            repeat((QuickActions.contextualPositions - actions.size).coerceAtLeast(0)) { Spacer(Modifier.size(48.dp)) }
            for (action in actions) key(action.intent) {
                ActionSquare(action.name, action.glyph, controller.enabled(scope),
                    Modifier.background(Color.White).border(1.dp, MenuBorder), badge = action.badge,
                    onClick = { perform(action.intent) })
            }
        }
        ActionSquare("Next step", "↦", controller.enabled(scope), Modifier.background(Color.White).border(1.dp, endingPlayerColor),
            ink = endingPlayerColor,
            holdName = "End turn", onHold = { perform(TableActionIntent.End(scope)) },
            gestureKey = scope,
            icon = { TableChromeIcon("next") },
            onClick = { perform(TableActionIntent.Next(scope)) })
    }
}

/** Name-only hold tooltip, unless a real hold command is assigned. Never invokes click on release. */
@Composable
internal fun ActionSquare(name: String, glyph: String, enabled: Boolean, modifier: Modifier = Modifier,
    fill: Color = Color.Transparent, badge: String? = null, selected: Boolean = false,
    holdName: String? = null, onHold: (() -> Unit)? = null, ink: Color? = null,
    gestureKey: Any? = null, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    val haptic = LocalHapticFeedback.current
    val performHold: (() -> Unit)? = onHold?.let { action -> { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick); action() } }
    val currentPerformHold by rememberUpdatedState(performHold)
    var tooltip by remember(name) { mutableStateOf(false) }
    var pressed by remember(name) { mutableStateOf(false) }
    val foreground = ink ?: if (fill == Color.Transparent) LocalContentColor.current else Color.White
    Box(modifier.size(48.dp).graphicsLayer {
        scaleX = if (pressed) .92f else 1f; scaleY = scaleX
    }.background(fill).drawWithContent {
        drawContent()
        if (pressed && enabled) drawRect(foreground.copy(alpha = .16f))
    }.onKeyEvent { event ->
        if (enabled && event.key in listOf(Key.Enter, Key.Spacebar, Key.DirectionCenter)) {
            if (event.type == KeyEventType.KeyUp) click()
            true
        } else false
    }.focusable(enabled).semantics(mergeDescendants = true) {
        contentDescription = name
        role = Role.Button
        if (!enabled) disabled()
        this.selected = selected
        onClick(name) { if (enabled) { click(); true } else false }
        if (holdName != null) customActions = listOf(CustomAccessibilityAction(holdName) {
            if (enabled) { currentPerformHold?.invoke(); true } else false
        })
    }.pointerInput(name) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.position != it.previousPosition || !it.pressed }) tooltip = false
            }
        }
    }.pointerInput(name, enabled, holdName, gestureKey) {
        var press = ActionPress(onHold != null)
        var capturedClick: (() -> Unit)? = null
        var capturedHold: (() -> Unit)? = null
        detectTapGestures(
            onPress = {
                capturedClick = click; capturedHold = currentPerformHold
                press = ActionPress(capturedHold != null)
                pressed = enabled
                try { if (!tryAwaitRelease()) press.cancel() }
                finally { tooltip = false; pressed = false }
            },
            onTap = { if (enabled && press.release() == PressAction.TAP) capturedClick?.invoke() },
            onLongPress = {
                if (enabled) when (press.hold()) {
                    PressAction.HOLD -> capturedHold?.invoke()
                    PressAction.TOOLTIP -> tooltip = true
                    else -> Unit
                }
            })
    }, contentAlignment = Alignment.Center) {
        if (icon != null) CompositionLocalProvider(LocalContentColor provides foreground) { icon() }
        else MenuIcon(name, tint = foreground)
        if (badge != null) Icon(menuIcon(if (badge == "↷") "Tap" else if (badge == "↓") "Draw one" else "Next step"),
            null, Modifier.align(Alignment.BottomEnd).padding(2.dp).size(12.dp), tint = foreground)
        if (tooltip) HoldTooltip(name)
    }
}
