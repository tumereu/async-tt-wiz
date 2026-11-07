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

data class QuickContext(val combatAreaId: String? = null,
    val playable: Boolean = true, val viewer: String? = null,
    /** Automatum on (I3): what the engine allows in combat now, once worked out ([automatumCombatQuickActions]). */
    val automatumCombat: org.asyncttwiz.automatum.CombatShown? = null,
    /** CMB-028: the active attack target lane (null: the defending player) and its name, for "Rest → <target>". */
    val combatTarget: String? = null, val combatTargetName: String = "",
    /** UI review round 2 #3: the viewer's own cast is open, so the step arrow can't act ([stepBlocked]). */
    val castOpen: Boolean = false,
    /** HIS-110 (owner, 2026-11-07): the other player's reveal panel is open: the main button is greyed until its OK. */
    val revealOpen: Boolean = false,
    /** CMB-040 (owner, 2026-11-05): how many of the defender's creatures the viewer placed as assumed blockers while
     * waiting for their blocks; null when not waiting for them. */
    val assumedBlockers: Int? = null,
    /** CMB-033 (owner, 2026-11-05): the go-on action of an open combat choice, shown on the main button. */
    val mainAction: MainAction? = null)

/** A disabled action stays in place, greyed and inert; one [refused] says why on tap (Automatum on, I-D28). */
data class QuickAction(val name: String, val glyph: String, val intent: TableActionIntent, val badge: String? = null,
    val enabled: Boolean = true, val refused: String? = null)

/** Complete G11/G12 catalog. Phase offerings precede an explicit Stack target; never guess top. */
object QuickActions {
    const val contextualPositions = 3
    /** I-D49: the opening's quick actions with Automatum on. */
    const val MULLIGAN = "Mulligan"
    const val KEEP = "Keep"
    const val NO_MORE_MULLIGANS = "No more mulligans: the hand you keep would have no cards."
    /** While the viewer's status is exactly the opening status, they are still mulliganing: Mulligan and Keep (I-D49). */
    fun openingMulligan(play: TableBoard, viewer: String?) = PlayerStatus.opening(play.board, viewer)
    /** Only mulligan offerings: no step arrow for a player who would not otherwise see the strip. */
    fun mulliganOnly(play: TableBoard, viewer: String?) = openingMulligan(play, viewer) &&
        play.board.turn.accountId != viewer && play.board.effectiveAttentionHolder != viewer
    fun visible(play: TableBoard, viewer: String?) = viewer != null &&
        (play.board.turn.accountId == viewer || play.board.effectiveAttentionHolder == viewer || openingMulligan(play, viewer))
    /** The quick actions besides the Automatum toggles: Automatum's combat staging while it offers it (CMB-028,
     * CMB-034), else the viewer's opening (Mulligan and Keep, TRN-062). Nothing changes the game by hand (I-D52). */
    fun contextual(play: TableBoard, context: QuickContext): List<QuickAction> {
        if (!context.playable) return emptyList()
        val scope = TableActionScope(play.gameId, play.playId)
        automatumCombatQuickActions(play, context, scope)?.let { return it }
        if (!openingMulligan(play, context.viewer)) return emptyList()
        val taken = play.board.players.firstOrNull { it.accountId == context.viewer }?.mulligans ?: 0
        val more = GameRules.STANDARD.canMulligan(taken)
        return listOf(QuickAction(MULLIGAN, "⇄", TableActionIntent.OpeningMulligan(scope), enabled = more,
            refused = if (more) null else NO_MORE_MULLIGANS), QuickAction(KEEP, "", TableActionIntent.Keep(scope)))
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
fun QuickActionStrip(play: TableBoard, context: QuickContext, controller: TableActionController,
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
    val actions = if (toggles) emptyList() else QuickActions.contextual(play, context)
    val scope = TableActionScope(play.gameId, play.playId)
    Box(modifier, contentAlignment = Alignment.Center) {
        val engine = LocalEngineRoutes.current
        // TRN-016, TRN-017 (owner playtest 2026-11-06): the main button keeps one width whatever quick actions show; they
        // share the rest of the row equally
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // I-D56 §14 (TRN-059): on engine state the toggles are the player's private settings for this game
                if (toggles && engine != null) EngineToggleCells(engine, true)
                for (action in actions) key(action.intent) {
                    // CMB-040: Clear of the assumed blocks works while the viewer waits for the defender
                    val assumed = (action.intent as? TableActionIntent.Combat)?.tool == CombatTool.ASSUME_CLEAR
                    InlinePopupCell(MenuControl(action.name, action.enabled && (assumed || controller.enabled(scope)), refused = action.refused) { perform(action.intent) },
                        quickActionLabel(action.name), owner = Color.Black, modifier = Modifier.weight(1f).height(48.dp))
                }
            }
            // I-D46 d: with Automatum on the main button (the step arrow, TRN-063) says what a tap does, in a neutral colour;
            // it is the one way to go on (UI review round 2 #1), Resolve included (STK-029, owner 2026-11-05). Owner playtest
            // 2026-11-03 #2: in the other player's turn only while the viewer holds priority or must decide; its room stays,
            // so the toggles don't move. I-D56 §14: on engine state only the viewer's own actions: hidden while the other
            // player must act
            val arrow = (toggles || visible && !QuickActions.mulliganOnly(play, context.viewer)) &&
                (engine?.arrowShown() ?: stepArrowShown(play, context.viewer))
            // TRN-066, TRN-070: while the other player must act, its place holds the wait's Assume (or Cancel); TRN-068: the
            // unconfirmed mark sits at its corner without moving anything
            val wait = if (arrow) null else engine?.waitAction()?.let { w ->
                if (context.revealOpen) EngineTableRoutes.WaitAction(w.label, w.icon, w.tooltip, REVEAL_BUSY, run = w.run) else w }
            val unconfirmed = engine?.engine?.shown?.indicator != null
            val main = context.mainAction.takeIf { arrow }?.let { m ->
                if (context.revealOpen) MainAction(m.label, m.icon, REVEAL_BUSY, m.tooltip, m.run) else m }
            val control = engine?.control() == true
            RecordShown(bottomBarRecord(if (arrow) main?.label ?: AutomatumStep.of(play, control).label else wait?.label, if (toggles) null else actions.map { it.name }))
            // TRN-063 (owner, 2026-11-06): a main button action is the player's own action, whose result the working sign
            // waits for; one not taken drops it
            fun own(action: () -> Boolean) { engine?.ownAction(); if (!action()) engine?.ownActionDropped() }
            val working = rememberSlowActionSign { engine?.ownActionPending(play) == true } && (arrow || wait != null)
            Box(Modifier.width(MainButtonWidth)) {
                if (arrow) AutomatumStepArrow(play, controller.enabled(scope), Modifier.fillMaxWidth(),
                    onHold = { own { perform(TableActionIntent.End(scope)) } },
                    // TRN-017, STK-029 (owner 2026-11-07): holding Resolve is the Resolve chain
                    onResolveHold = engine?.let { routes -> { own { routes.holdResolve() } } },
                    blocked = stepBlocked(play, context.castOpen, context.automatumCombat, context.revealOpen),
                    resolveTip = "Resolve: pass, so the top of the Stack resolves once ${engine?.opponentName ?: "the other player"} passes too",
                    main = main?.let { m -> MainAction(m.label, m.icon, m.refusal, m.tooltip) { own { m.run(); true } } }, control = control) {
                    own { perform(TableActionIntent.Next(scope)) }
                }
                else if (wait != null) EngineWaitArrow(EngineTableRoutes.WaitAction(wait.label, wait.icon, wait.tooltip, wait.refusal,
                    wait.hold?.let { hold -> { own { hold(); true } } }, wait.holdName) {
                    own { wait.run(); true } }, Modifier.fillMaxWidth())
                else Spacer(Modifier.fillMaxWidth().height(48.dp))
                if (working) SlowActionShimmer(Modifier.matchParentSize().clip(AdvanceArrowShape))
                if (unconfirmed) EngineUnconfirmedMark(engine!!.opponentName, Modifier.align(Alignment.TopStart).offset((-10).dp, (-10).dp))
            }
        }
    }
}

/** TRN-017: the main button's one width, whatever quick actions show beside it. */
internal val MainButtonWidth = 112.dp

internal val AdvanceArrowShape = GenericShape { size, _ ->
    val tip = size.height / 4f
    moveTo(0f, 0f)
    lineTo(size.width - tip, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - tip, size.height)
    lineTo(0f, size.height)
    close()
}

internal fun quickActionLabel(name: String) = ringShortLabel(name)

/** What the bottom bar shows, for the record of shown texts ([LocalShownTexts], the e2e tests' phones): the main button's
 * words ([main]; null: none) and the quick actions besides the settings toggles ([actions]; null: the toggles). */
internal fun bottomBarRecord(main: String?, actions: List<String>?): String =
    "$BOTTOM_BAR${main ?: "none"} · quick actions: ${actions?.joinToString(", ") ?: "settings"}"

/** The start of every [bottomBarRecord]. */
internal const val BOTTOM_BAR = "Bottom bar: main button "

/** Name-only hold tooltip, unless a real hold command is assigned. Never invokes click on release. */
@Composable
internal fun ActionSquare(name: String, glyph: String, enabled: Boolean, modifier: Modifier = Modifier,
    fill: Color = Color.Transparent, badge: String? = null, selected: Boolean = false,
    holdName: String? = null, onHold: (() -> Unit)? = null, ink: Color? = null,
    gestureKey: Any? = null, icon: (@Composable () -> Unit)? = null, compactContent: Boolean = false,
    /** What holding it shows when no hold command is assigned (its [name] otherwise). */
    tooltip: String? = null, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    val haptic = LocalHapticFeedback.current
    val performHold: (() -> Unit)? = onHold?.let { action -> { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick); action() } }
    val currentPerformHold by rememberUpdatedState(performHold)
    var holdTip by remember(name) { mutableStateOf(false) }
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
                if (event.changes.any { it.position != it.previousPosition || !it.pressed }) holdTip = false
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
                finally { holdTip = false; pressed = false }
            },
            onTap = { if (enabled && press.release() == PressAction.TAP) capturedClick?.invoke() },
            onLongPress = {
                if (enabled) when (press.hold()) {
                    PressAction.HOLD -> capturedHold?.invoke()
                    PressAction.TOOLTIP -> holdTip = true
                    else -> Unit
                }
            })
    }, contentAlignment = Alignment.Center) {
        if (icon != null) CompositionLocalProvider(LocalContentColor provides foreground) { icon() }
        else MenuIcon(name, tint = foreground)
        if (badge != null) Icon(menuIcon(if (badge == "↷") "Tap" else if (badge == "↓") "Draw one" else "Next step"),
            null, Modifier.align(Alignment.BottomEnd).padding(2.dp).size(12.dp), tint = foreground)
        if (holdTip) HoldTooltip(tooltip ?: name)
    }
}
