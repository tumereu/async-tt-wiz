package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import arcanum.api.TurnStep
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.EngineWorker
import org.asyncttwiz.protocol.game.GameDevice

/*
 * What a player does on their table and what it shows, through the app's own UI (the table's semantics nodes: every
 * card, pile and control the screen draws), and the app's own state behind it for assertions ([shown]: the board the
 * table shows; [engine]: the engine thread's game).
 */

/** The table's last published snapshot (what the screen shows). */
internal val E2ePlayer.shown: EngineGame.Shown get() = requireNotNull(game?.shown) { "$name's table isn't loaded" }

/** The engine state the table shows. */
internal val E2ePlayer.state: GameState get() = shown.engine

/** The device's status as the table shows it. */
internal val E2ePlayer.status: GameDevice.Status get() = shown.status

/** Reads the engine thread's game (the device's whole state) on the engine thread. */
internal fun <T> E2ePlayer.engine(read: EngineGame.() -> T): T {
    val open = requireNotNull(game ?: last) { "$name has no game open" }
    return engineWorker.call { open.read() }
}

internal val E2ePlayer.me: PlayerId get() = PlayerId(id)

/** The name of the card [id] on [s]. */
internal fun cardName(s: GameState, id: ObjectId): String? = s.obj(id)?.definition?.faces?.first()?.name

/** The ATW card id of an engine card object. */
internal fun atw(id: ObjectId): String = (id as ObjectId.Card).card.value

/** [player]'s cards named [name] in [ids] (a zone) on the shown state. */
internal fun E2ePlayer.named(ids: Collection<ObjectId>, name: String, s: GameState = state): List<ObjectId> =
    ids.filter { cardName(s, it) == name }

internal fun E2ePlayer.hand(name: String): List<ObjectId> = state.let { s -> named(s.player(me).hand, name, s) }
internal fun E2ePlayer.field(name: String, owner: PlayerId = me): List<ObjectId> =
    state.let { s -> s.battlefield.filter { s.obj(it)?.controller == owner && cardName(s, it) == name } }

/** The player's own Main 1 with priority and the Stack empty, as the table shows it. */
internal fun E2ePlayer.inOwnMain(main: TurnStep = TurnStep.PrecombatMain): Boolean = game?.shown?.let { shown ->
    val s = shown.engine
    shown.status == GameDevice.Status.YourMove && s.turn.active == me && s.turn.step == main && s.stack.isEmpty() &&
        s.priority.holder == me
} == true

// ---- The screen's nodes

private fun SemanticsNode.description(): String = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
private fun byId(node: SemanticsNode) = SemanticsMatcher("node #${node.id}") { it.id == node.id }

/** The bounds of [owner]'s battlefield on this player's table. */
internal fun E2ePlayer.battlefieldBounds(owner: E2ePlayer = this): Rect =
    nodes(hasContentDescription("${owner.name} Battlefield,", substring = true)).fetchSemanticsNodes().single().boundsInRoot

/** This player's card nodes named [name] owned by [owner] (each card the table draws: hand, battlefield, Stack). */
private fun E2ePlayer.cardNodes(name: String, owner: E2ePlayer): List<SemanticsNode> =
    nodes(hasContentDescription("$name, owner ${owner.name}, ", substring = true)).fetchSemanticsNodes()
        .filter { it.description().startsWith("$name, owner ${owner.name}, ") }

/** The card [name] in this player's hand on the screen (the [index]th from the left). */
internal fun E2ePlayer.handCard(name: String, index: Int = 0): SemanticsNodeInteraction {
    val field = battlefieldBounds()
    val cards = cardNodes(name, this).filter { it.boundsInRoot.center.y > field.bottom }.sortedBy { it.boundsInRoot.left }
    check(cards.size > index) { "$name has no ${name} in hand on the screen: ${screen()}" }
    return node(byId(cards[index]))
}

/** The card [name] on [owner]'s battlefield on this player's screen (the [index]th from the left). */
internal fun E2ePlayer.battlefieldCard(name: String, owner: E2ePlayer = this, index: Int = 0): SemanticsNodeInteraction {
    val field = battlefieldBounds(owner)
    val cards = cardNodes(name, owner).filter { field.contains(it.boundsInRoot.center) }.sortedBy { it.boundsInRoot.left }
    check(cards.size > index) { "$name: no ${name} of ${owner.name}'s on the battlefield on the screen: ${screen()}" }
    return node(byId(cards[index]))
}

/** Whether the screen shows a card [name] of [owner]'s on their battlefield. */
internal fun E2ePlayer.showsOnBattlefield(name: String, owner: E2ePlayer = this): Boolean = runCatching {
    val field = battlefieldBounds(owner)
    cardNodes(name, owner).any { field.contains(it.boundsInRoot.center) }
}.getOrDefault(false)

/** A control (button, quick action, the step arrow) by what it says. */
internal fun E2ePlayer.control(label: String): SemanticsNodeInteraction = node(hasContentDescription(label) and hasClickAction())
internal fun E2ePlayer.showsControl(label: String): Boolean = shows(hasContentDescription(label) and hasClickAction())

/** Taps the control [label]. */
internal fun E2ePlayer.tap(label: String) { control(label).performClick() }

/** Holds the control [label] (a long press: the step arrow's End turn, Undo's earlier points). */
internal fun E2ePlayer.hold(label: String) { control(label).performTouchInput { longClick() } }

/** What the step arrow says now ([org.asyncttwiz.android.AutomatumStep] labels), null while it isn't shown. */
internal val STEP_LABELS = listOf("Pass", "Next", "To combat", "End turn", "Resolve", "No attack", "No blocks")
internal fun E2ePlayer.stepArrow(): String? = nodes(hasClickAction()).fetchSemanticsNodes().map { it.description() }
    .firstOrNull { it in STEP_LABELS || it.matches(Regex("(Attack|Block) \\([0-9]+\\)")) }

/** The playback button (Play / Pause, HIS-013). */
internal fun E2ePlayer.playbackButton(): SemanticsNodeInteraction = node(hasTestTag("playback-toggle"))
internal fun E2ePlayer.playbackLabel(): String? = nodes(hasTestTag("playback-toggle")).fetchSemanticsNodes().firstOrNull()
    ?.config?.getOrNull(SemanticsProperties.StateDescription)

/** Plays (or casts) the hand card [name]: a tap raises it, a tap on the raised card plays it (ZON-079). */
internal fun E2ePlayer.playFromHand(name: String, e2e: E2e) {
    handCard(name).performClick()
    e2e.waitUntil("$name raised in ${this.name}'s hand") { raisedInHand() == name }
    handCard(name).performClick()
}

/** The name of the hand card drawn raised above the hand row (ZON-079), if any. */
internal fun E2ePlayer.raisedInHand(): String? = runCatching {
    val field = battlefieldBounds()
    val cards = nodes(hasContentDescription(", owner $name, ", substring = true)).fetchSemanticsNodes()
        .filter { it.boundsInRoot.center.y > field.bottom }
    val row = cards.maxOf { it.boundsInRoot.top }
    cards.filter { it.boundsInRoot.top < row - 4f }.singleOrNull()?.description()?.substringBefore(", owner ")
}.getOrNull()

/**
 * The other player playing along by themselves, as the interim simulations' Bob did: on their table they keep their
 * opening hand, pass (the step arrow's Pass / Next / Resolve), declare no attacks and no blocks, end their own turn
 * (holding the arrow) in their main phases, press Play on a catch-up and Resolve at a playback stop. One action per board
 * shown; nothing while the app is away.
 */
internal fun E2ePlayer.playAlong() {
    val shown = game?.shown ?: return
    // Played and shown, nothing more of theirs going out: their next action waits for the board after it
    if (engine { device.session.pending.isNotEmpty() }) return
    if (!foreground || shown.version == alongAt) return
    val acted = runCatching {
        when (val status = shown.status) {
            GameDevice.Status.CatchUp -> { playbackButton().performClick(); true }
            is GameDevice.Status.Stopped ->
                if (showsControl("Keep")) { tap("Keep"); true }
                else if (shows(hasTestTag("stack-resolve"))) { node(hasTestTag("stack-resolve")).performClick(); true }
                else stepArrow()?.let { tap(it); true } ?: false
            GameDevice.Status.YourMove -> when {
                showsControl("Keep") -> { tap("Keep"); true }
                // Their own Main 1: a Mountain first (a double tap plays it), so the hand stays at seven
                shown.engine.let { s -> s.turn.active == me && s.turn.step == TurnStep.PrecombatMain && (s.turn.landsPlayed[me] ?: 0) == 0 &&
                    s.stack.isEmpty() && hand("Mountain").isNotEmpty() } -> {
                    handCard("Mountain").performTouchInput { doubleClick() }; true
                }
                else -> when (val arrow = stepArrow()) {
                    null -> false
                    "To combat", "End turn" -> { hold(arrow); true }
                    "Pass", "Next", "Resolve", "No attack", "No blocks" ->
                        if (shown.engine.turn.active == me && shown.engine.turn.step.let { it == TurnStep.PrecombatMain || it == TurnStep.PostcombatMain })
                            { hold(arrow); true } else { tap(arrow); true }
                    else -> false
                }
            }
            else -> false
        }
    }.getOrDefault(false)
    if (acted) alongAt = shown.version
}
