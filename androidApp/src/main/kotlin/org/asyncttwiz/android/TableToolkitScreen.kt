package org.asyncttwiz.android

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/** The production overlay tree, mounted once alongside the Canvas and using its captured IDs. */
@Composable
internal fun TableToolkitScreen(toolkit: TableToolkitController, play: TableBoard, game: GameSummary,
    landscape: Boolean,
    peekScroll: ScrollState? = null, scene: TableScene? = null, peekSwipe: InspectSwipe? = null,
    onTimeline: (() -> Unit)? = null, reviewing: Boolean = false, gameActionsBlocked: Boolean = false) {
    val names = mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username)
    val enabled = toolkit.enabled()
    val endingColor = if (play.board.turn.accountId == toolkit.viewer) OwnTableColor else OpponentTableColor
    val bound = toolkit.capture
    val popup = toolkit.popupItem
    CompositionLocalProvider(LocalInlinePopupAnchor provides InlinePopupAnchor(popup?.id ?: "table",
        popup?.bounds, popup?.owner == null || popup.owner == toolkit.viewer)) {
    if (bound != null && toolkit.panel == null) EngineCardMenu(toolkit, play, bound, names, enabled)
    toolkit.player?.let { PlayerPanel(play, it, toolkit.viewer, toolkit::dismiss) }

    // STK-048: picking a target on the Stack, its list opens in place with the legal entries glowing
    if (toolkit.stackTargeting) ManageStackDialog(play, toolkit.viewer, toolkit::closeStackTargets,
        targets = toolkit.stackTargets(play), pick = toolkit::pickStackTarget)
    when (val panel = toolkit.panel) {
        "manageStack" -> ManageStackDialog(play, toolkit.viewer, toolkit::dismiss)
        "table" -> {
            TableActionMenu(play, toolkit.table, toolkit::dismiss, perform = toolkit::perform, names = names, onTimeline = onTimeline,
                blocked = gameActionsBlocked)
        }
        "phase" -> PhaseStepPicker(play, toolkit.viewer, endingColor, toolkit::dismiss, names = names)
        else -> if (panel?.startsWith("library/") == true) {
            val id = panel.substringAfter('/')
            InlinePopupPanel(toolkit::dismiss, "Library", width = 264.dp, key = panel) {
                ZoneMenu(rows = libraryTopRows(toolkit, play, id, names, enabled))
            }
        } else if (panel?.startsWith("pile/") == true) {
            val pile = play.board.containers.singleOrNull { it.id == panel.substringAfter('/') && it.zone in PublicPiles.zones }
            if (pile == null) LaunchedEffect(panel) { toolkit.dismiss() }
            else InlinePopupPanel(toolkit::dismiss, moveZoneLabel(pile.zone), width = 152.dp, height = 104.dp, key = panel) {
                // Looking is all a pile offers: Search is its only cell
                ZoneMenu(kept = {
                    InlinePopupCell(MenuControl("Search", pile.cards.isNotEmpty()) { toolkit.openPublicPile(pile.id, toggle = false) })
                })
            }
        }
    }
    }
    toolkit.peek?.let { GameplayCardInspection(play, toolkit.viewer, it, { toolkit.peek(null) }, names, peekScroll, peekSwipe) }
    // TBL-104: the panels' Inspect is drawn here, outside their layout
    val inspectHost = remember { InspectHost() }
    CompositionLocalProvider(LocalInspectHost provides inspectHost) {
    // I-D50: the raised hand card's cost chips
    RaisedCardChips(toolkit, play, scene)
    // TBL-122: a battlefield card's ability chips when it has several to activate
    PermanentAbilityChips(toolkit, play, scene)
    // Automatum (I2a): the open cast's panel by the Stack, over the table
    CastPanelHost(toolkit, play, scene, names)
    // Automatum (I2b): priority, resolution and trigger choices by the Stack
    SharedFlowHost(toolkit, play, scene, names, reviewing)
    val engine = LocalEngineRoutes.current
    // I-D49: the opening's Keep / Bottom screen
    OpeningKeepHost(toolkit, play, scene)
    // I-D56 §14: the async play panels of a game on engine state
    if (engine != null) EngineAsyncHost(toolkit, engine, play, scene, names, LocalOpponentPresence.current, game)
    // Automatum (I3): what combat allows and would do on this board, worked out in the background
    AutomatumCombatHost(toolkit, play)
    }
    HostedInspectionView(inspectHost)
}

internal fun toolkitQuickContext(play: TableBoard, viewer: String? = null): QuickContext =
    QuickContext(combatAreaId = play.board.combat?.id, viewer = viewer)

/**
 * Automatum on (§3 I4): what the engine offers for the card a menu was opened on (a combat copy's real card), as rows
 * above the menu's cells: casts, a land play, each activated ability with its text, mana abilities and special actions,
 * those it doesn't allow now greyed with the reason on tap. A card in the other player's hand shows only a greyed Cast
 * (I-D27: nothing about it is read). Until Automatum has worked the board out (SYS-025) one greyed row says so.
 */
internal fun automatumRows(toolkit: TableToolkitController, play: TableBoard, bound: ToolkitCapture, names: Map<String, String>,
    enabled: Boolean): List<EngineRow> {
    val cardId = CombatStackRules.liveSource(play.board, bound.contextViewId)?.id ?: bound.contextViewId
    val card = play.board.cards.singleOrNull { it.id == cardId } ?: return emptyList()
    val zone = play.board.containers.singleOrNull { it.id == card.containerId }?.zone
    if (zone == PlayZone.HAND && !CastController.ownHand(play, cardId, toolkit.viewer))
        return listOf(EngineRow(MenuControl("Cast", false, refused = CastController.NOT_YOURS) {}, Icons.Outlined.AutoAwesome))
    val plays = toolkit.cardPlays(play)
        ?: return listOf(EngineRow(MenuControl(WORKING_OUT, false) {}, Icons.Outlined.HourglassTop))
    return engineRows(toolkit, play, cardId, plays.of(cardId), names, enabled)
}

/**
 * Automatum on (ZON-078, final cast review #8): what the engine offers for the top card of the Library [libraryId] (cast
 * or play it from the top, Seer of Harvestide-like), as the Library menu's rows; none when it offers
 * nothing, or for a top card the viewer may not see (CardPlays lists nothing of hidden cards, I-D27).
 */
internal fun libraryTopRows(toolkit: TableToolkitController, play: TableBoard, libraryId: String, names: Map<String, String>,
    enabled: Boolean): List<EngineRow> {
    val top = play.board.containers.singleOrNull { it.id == libraryId && it.zone == PlayZone.LIBRARY }?.cards?.firstOrNull()
        ?: return emptyList()
    val offered = toolkit.cardPlays(play)?.of(top)?.filter { it.legal }.orEmpty()
    if (offered.isEmpty()) return emptyList()
    val name = CastController.cardName(play, top)
    return engineRows(toolkit, play, top, offered, names, enabled).map { it.copy(control = it.control.copy(name = "${it.control.name} · $name")) }
}

/** The menu rows of [offered], what the engine offers for [cardId]. */
private fun engineRows(toolkit: TableToolkitController, play: TableBoard, cardId: String,
    offered: List<org.asyncttwiz.automatum.CardPlay>, names: Map<String, String>, enabled: Boolean): List<EngineRow> {
    val name = CastController.cardName(play, cardId)
    val busy = when {
        toolkit.cast.open -> CastController.BUSY
        play.flowOpen -> SharedFlowController.BUSY
        else -> null
    }
    return offered.map { offered ->
        val label = when (offered.kind) {
            org.asyncttwiz.automatum.CardPlay.Kind.Cast -> if (offered.text == "Cast") "Cast" else "Cast · ${offered.text}"
            else -> offered.text
        }
        val reason = busy ?: offered.refusal?.let { engineRefusal(it, offered.kind, name, names) }
        EngineRow(MenuControl(label, enabled && offered.legal && busy == null, refused = reason) {
            if (toolkit.play(cardId, offered)) toolkit.dismiss()
        }, engineIcon(offered.kind))
    }
}

internal const val WORKING_OUT = "Automatum is working out what this card can do…"

/** The reason shown for something the engine doesn't allow now (§3 I4), in plain words. */
internal fun engineRefusal(refusal: org.asyncttwiz.automatum.CardPlay.Refusal, kind: org.asyncttwiz.automatum.CardPlay.Kind,
    name: String, names: Map<String, String>): String = when (refusal) {
    is org.asyncttwiz.automatum.CardPlay.Refusal.Priority ->
        refusal.holder?.let { "${names[it] ?: "The other player"} holds priority now." } ?: "Nobody holds priority now."
    org.asyncttwiz.automatum.CardPlay.Refusal.Timing -> "Only in your main phase while the Stack is empty."
    org.asyncttwiz.automatum.CardPlay.Refusal.Loyalty -> "A loyalty ability: once each turn, in your main phase while the Stack is empty."
    org.asyncttwiz.automatum.CardPlay.Refusal.Used -> "Already used as often as allowed this turn."
    org.asyncttwiz.automatum.CardPlay.Refusal.NotYours -> when (kind) {
        org.asyncttwiz.automatum.CardPlay.Kind.Cast, org.asyncttwiz.automatum.CardPlay.Kind.Play -> CastController.NOT_YOURS
        else -> "Only its controller can do that."
    }
    org.asyncttwiz.automatum.CardPlay.Refusal.Other -> when (kind) {
        org.asyncttwiz.automatum.CardPlay.Kind.Cast -> "$name can't be cast now."
        org.asyncttwiz.automatum.CardPlay.Kind.Play -> "$name can't be played now."
        else -> "$name: Automatum doesn't allow that now (a cost it can't pay, or nothing to target)."
    }
}

private fun engineIcon(kind: org.asyncttwiz.automatum.CardPlay.Kind) = when (kind) {
    // UI review round 2 N12: a cast has the cast panel's sparkle, a land play the card placed on the table
    org.asyncttwiz.automatum.CardPlay.Kind.Cast -> Icons.Outlined.AutoAwesome
    org.asyncttwiz.automatum.CardPlay.Kind.Play -> PlayCardIcon
    org.asyncttwiz.automatum.CardPlay.Kind.Mana -> Icons.Outlined.WaterDrop
    org.asyncttwiz.automatum.CardPlay.Kind.Special -> Icons.Outlined.AutoFixHigh
    org.asyncttwiz.automatum.CardPlay.Kind.Ability -> Icons.Outlined.Bolt
}

/**
 * A zone menu (Library, Graveyard, Exile; §3 I4, I-D28, I-D46): only the engine's [rows] (the Library's top card cast or
 * played from the top, ZON-078) and what has no game effect ([kept], looking).
 */
@Composable
internal fun ZoneMenu(kept: @Composable () -> Unit = {}, rows: List<EngineRow> = emptyList()) {
    Column(verticalArrangement = Arrangement.spacedBy(InlinePopupStyle.cellGap.dp)) {
        for (row in rows) EngineActionRow(row, LocalInlinePopupColor.current)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { kept() }
    }
}

