package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

data class QuickContext(val selectMode: Boolean = false, val selectedSourceIds: List<String> = emptyList(),
    val combatAreaId: String? = null,
    val playable: Boolean = true, val viewer: String? = null,
    /** Automatum on (I3): what the engine allows in combat now, once worked out ([automatumCombatQuickActions]). */
    val automatumCombat: org.asyncttwiz.automatum.CombatShown? = null,
    /** CMB-028: the active attack target lane (null: the defending player) and its name, for "Rest → <target>". */
    val combatTarget: String? = null, val combatTargetName: String = "",
    /** Finalreview-combat #7: the viewer's pause bar is shown with its own Pass, so combat offers no second one. */
    val pauseBar: Boolean = false)

/** A disabled action stays in place, greyed and inert; one [refused] says why on tap (Automatum on, I-D28). */
data class QuickAction(val name: String, val glyph: String, val intent: TableActionIntent, val badge: String? = null,
    val enabled: Boolean = true, val refused: String? = null)

/** Complete G11/G12 catalog. Phase offerings precede an explicit Stack target; never guess top. */
object QuickActions {
    const val contextualPositions = 3
    const val READY = "Ready"
    const val ASSUME_NO_BLOCKS = "Assume no blocks"
    /** While the viewer's status is exactly the opening status, they are still mulliganing: Mull N, Mull −1, Ready. */
    fun openingMulligan(play: PlayState, viewer: String?) = PlayerStatus.opening(play.board, viewer)
    /** Only mulligan offerings: no step arrow for a player who would not otherwise see the strip. */
    fun mulliganOnly(play: PlayState, viewer: String?) = openingMulligan(play, viewer) &&
        play.board.turn.accountId != viewer && play.board.effectiveAttentionHolder != viewer
    fun visible(play: PlayState, viewer: String?) = viewer != null &&
        (play.board.turn.accountId == viewer || play.board.effectiveAttentionHolder == viewer || openingMulligan(play, viewer))
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
        // Rules v33: the steps on the way are recorded, and the draw happens in the Draw step itself.
        val toDraw = TableActionIntent.Choose(scope, TurnPosition.DRAW, pass = true)
        val turnPlayer = context.viewer == play.board.turn.accountId
        // Turn numbers count both players' turns, so "1" is only the starting player's first turn: no draw.
        val firstTurn = play.board.turn.number == "1"
        // Automatum on (I3): combat goes through the engine; its own offerings replace the manual ones
        val automatumCombat = automatumCombatQuickActions(play, context, scope)
        if (automatumCombat != null) return result.apply { addAll(automatumCombat) }
        // The starting player's turn-1 shortcuts wait until neither player is still mulliganing.
        if (turnPlayer && !(firstTurn && PlayerStatus.anyOpening(play.board))) when (TurnPosition.of(play.board.turn)) {
            TurnPosition.UNTAP -> {
                add("Untap all", "↶", controller.untap(play, false))
                add("Untap all + next", "↶", controller.untap(play, true), "↦")
                if (firstTurn) add("Go to main", "", choose(TurnPosition.PRECOMBAT_MAIN))
                else add("Untap, draw → main", "", bundle(controller.untap(play, false), toDraw, controller.draw(play, false),
                    choose(TurnPosition.PRECOMBAT_MAIN)))
            }
            TurnPosition.UPKEEP -> if (firstTurn) add("Go to main", "", choose(TurnPosition.PRECOMBAT_MAIN))
                else add("Draw + go to main", "", bundle(toDraw, controller.draw(play, false), choose(TurnPosition.PRECOMBAT_MAIN)))
            TurnPosition.DRAW -> if (firstTurn) add("Go to main", "", choose(TurnPosition.PRECOMBAT_MAIN)) else {
                add("Draw one", "↓", controller.draw(play, false))
                add("Draw one + next", "↓", controller.draw(play, true), "↦")
            }
            TurnPosition.PRECOMBAT_MAIN -> {
                add("Select attackers", "", TableActionIntent.SelectAttackers(scope, advance = true))
                add("Skip combat", "", choose(TurnPosition.END_STEP))
            }
            TurnPosition.DECLARE_ATTACKERS -> if (context.selectedSourceIds.isNotEmpty()) {
                add("Declare + tap", "⚔", TableActionIntent.Combat(scope, CombatTool.DECLARE_AND_TAP,
                    context.combatAreaId, context.selectedSourceIds.distinct()), "↷")
                add("Declare without tapping", "⚔", TableActionIntent.Combat(scope, CombatTool.DECLARE,
                    context.combatAreaId, context.selectedSourceIds.distinct()))
            } else {
                val declared = play.board.combat?.pairs?.any { it.attackers.isNotEmpty() } == true
                add(if (declared) "Select more attackers" else "Select attackers", "", TableActionIntent.SelectAttackers(scope, advance = false))
                if (!declared) add("No attack", "", TableActionIntent.Combat(scope, CombatTool.NO_ATTACK, context.combatAreaId))
                // Two separate steps (Declare blockers, then Combat damage): the defender can continue from blocks.
                else add(ASSUME_NO_BLOCKS, "", TableActionIntent.Combat(scope, CombatTool.ASSUME_NO_BLOCKS, context.combatAreaId))
            }
            // Experimental damage aid; all offerings live in one removable helper.
            // CMB-022 (I-D39): no damage maths by hand; players deal the damage themselves
            TurnPosition.COMBAT_DAMAGE -> add("Close + next", "", TableActionIntent.Combat(scope, CombatTool.DAMAGE_CLOSE_AND_NEXT, context.combatAreaId))
            TurnPosition.END_COMBAT -> add("Advance + close combat", "▱",
                TableActionIntent.Combat(scope, CombatTool.ADVANCE_AND_CLOSE, context.combatAreaId), "↦")
            TurnPosition.POSTCOMBAT_MAIN, TurnPosition.END_STEP -> add("End turn", "", TableActionIntent.End(scope))
            else -> Unit
        }
        if (openingMulligan(play, context.viewer)) {
            val targets = context.viewer?.let { controller.targets(play, it) }
            add("Mull N", "⇄", controller.library(play, LibraryTool.MULLIGAN_N, targets), "↓")
            add("Mull −1", "⇄", controller.library(play, LibraryTool.MULLIGAN_MINUS_ONE, targets), "−1")
            add(READY, "", TableActionIntent.Status(scope, ""))
            if (!turnPlayer) return result
        }
        if (!turnPlayer && play.board.players.any { it.accountId == context.viewer } &&
            TurnPosition.of(play.board.turn) == TurnPosition.DECLARE_BLOCKERS)
            add("No blocks + next", "", TableActionIntent.Next(scope))
        // Automatum on (§3 I4, I-D28): quick actions that change the game by hand (untapping, drawing, mulligans,
        // closing the combat area) stay in place, greyed, and say why; step changes go through Automatum (TRN-057)
        if (play.board.automatum && !AutomatumBoards.manualAllowed(play))
            return result.map { if (manualIntent(it.intent)) it.copy(enabled = false, refused = AutomatumDrags.MANUAL) else it }
        return result
    }

    /** Whether [intent] changes the game by hand rather than moving the turn on. */
    fun manualIntent(intent: TableActionIntent): Boolean = when (intent) {
        is TableActionIntent.Bundle -> intent.actions.any(::manualIntent)
        is TableActionIntent.Untap, is TableActionIntent.Draw, is TableActionIntent.Library, is TableActionIntent.Orient,
        is TableActionIntent.Token -> true
        is TableActionIntent.Combat -> intent.tool !in setOf(CombatTool.AUTOMATUM_DECLARE, CombatTool.AUTOMATUM_PASS,
            CombatTool.AUTOMATUM_STAGE, CombatTool.AUTOMATUM_CLEAR, CombatTool.AUTOMATUM_NO_BLOCKS, CombatTool.DECLARE)
        else -> false
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

/** Equal-width popup cells fill the space before the right-corner advance arrow. */
@Composable
fun QuickActionStrip(play: PlayState, context: QuickContext, controller: TableActionController,
    endingPlayerColor: Color, modifier: Modifier = Modifier, perform: (TableActionIntent) -> Boolean = controller::perform,
    /** I-D46 (c): sends the viewer's own Automatum setting changes (Skip when I have no choice). */
    submit: (PlayAction) -> Boolean = { false }) {
    if (!context.playable) return
    // I-D46 (c): with Automatum on the row is the viewer's Automatum toggles, on either player's turn
    val toggles = AutomatumToggles.shown(play, context)
    val visible = QuickActions.visible(play, context.viewer)
    if (!visible && !toggles) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Opponent's turn", color = Color.Black.copy(alpha = .38f), style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val actions = if (toggles) emptyList() else QuickActions.contextual(play, context, controller)
    val scope = TableActionScope(play.gameId, play.id)
    val style = advanceButtonStyle(play.board, requireNotNull(context.viewer))
    val nextLabel = nextStepLabel(play.board.turn)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compact = maxWidth < 480.dp && (toggles || actions.size >= 3)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (toggles) AutomatumToggleCells(play, requireNotNull(context.viewer), controller.enabled(scope), submit)
                for (action in actions) key(action.intent) {
                    InlinePopupCell(MenuControl(action.name, action.enabled && controller.enabled(scope), refused = action.refused) { perform(action.intent) },
                        quickActionLabel(action.name), owner = Color.Black, modifier = Modifier.weight(1f).height(48.dp))
                }
            }
            if (visible && !QuickActions.mulliganOnly(play, context.viewer)) ActionSquare(nextLabel, "↦", controller.enabled(scope), Modifier.width(if (compact) 68.dp else 72.dp).shadow(3.dp, AdvanceArrowShape)
                .background(style.fill, AdvanceArrowShape).border(1.dp, style.outline, AdvanceArrowShape).clip(AdvanceArrowShape),
                fill = style.fill, ink = style.ink, compactContent = true,
                holdName = "End turn", onHold = { perform(TableActionIntent.End(scope)) },
                gestureKey = scope,
                icon = { Column(Modifier.fillMaxWidth().padding(start = 2.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MenuIcon(nextStep(play.board.turn)?.label ?: "End turn", Modifier.size(20.dp), tint = LocalContentColor.current)
                    Text(nextLabel, Modifier.fillMaxWidth(), fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.sp,
                        maxLines = 2, textAlign = TextAlign.Center)
                } },
                onClick = { perform(TableActionIntent.Next(scope)) })
        }
    }
}

internal val AdvanceArrowShape = GenericShape { size, _ ->
    val tip = size.height / 4f
    moveTo(0f, 0f)
    lineTo(size.width - tip, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - tip, size.height)
    lineTo(0f, size.height)
    close()
}

internal fun quickActionLabel(name: String) = when (name) {
    "Untap, draw → main" -> "Untap, draw\n→ main"
    "Untap all + next" -> "Untap + next"
    "Return Hand → Shuffle → Draw N" -> "Mulligan N"
    "Return Hand → Shuffle → Draw previous Hand size −1" -> "Mulligan −1"
    "Declare without tapping" -> "Declare"
    "Draw + go to main" -> "Draw + main"
    "Advance + close combat" -> "Close + next"
    else -> ringShortLabel(name)
}

/** Name-only hold tooltip, unless a real hold command is assigned. Never invokes click on release. */
@Composable
internal fun ActionSquare(name: String, glyph: String, enabled: Boolean, modifier: Modifier = Modifier,
    fill: Color = Color.Transparent, badge: String? = null, selected: Boolean = false,
    holdName: String? = null, onHold: (() -> Unit)? = null, ink: Color? = null,
    gestureKey: Any? = null, icon: (@Composable () -> Unit)? = null, compactContent: Boolean = false, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    val haptic = LocalHapticFeedback.current
    val performHold: (() -> Unit)? = onHold?.let { action -> { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick); action() } }
    val currentPerformHold by rememberUpdatedState(performHold)
    var tooltip by remember(name) { mutableStateOf(false) }
    var pressed by remember(name) { mutableStateOf(false) }
    val foreground = ink ?: if (fill == Color.Transparent) LocalContentColor.current else Color.White
    Box(modifier.then(if (compactContent) Modifier.height(48.dp) else Modifier.size(48.dp)).graphicsLayer {
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
