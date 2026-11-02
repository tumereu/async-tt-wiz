package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItemKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.protocol.*

/**
 * Automatum's shared flows on the table (integration spec §3 I2b, I-D11): by the Stack, in the cast panel's style,
 * - the player holding priority with something on the Stack: "Alice cast Storm Spark" with Respond and Let it resolve
 *   (Respond shrinks it to a bar over the hand while they cast a response, ZON-004);
 * - the player who decides an open choice of a resolution or a trigger: the choice, its prompt the card's sentence;
 * - the other player: "Bob is choosing — …" or "Bob may respond …", with "Choose for them" / "Pass for them" after a
 *   confirmation (D11); cards only that player may see stay hidden (I-D27).
 * The eye hides the panel to look at the board (I-D10). Nothing shows while a cast is open (its own panel does).
 */
@Composable
internal fun SharedFlowHost(toolkit: TableToolkitController, play: PlayState, scene: TableScene?, names: Map<String, String>) {
    val flows = toolkit.flows
    val shown = !toolkit.cast.open && AutomatumBoards.sharedFlowShown(play)
    LaunchedEffect(IdentityKey(play.board), shown) {
        if (!shown) { flows.show(null); return@LaunchedEffect }
        val ready = AutomatumBoards.cachedFlow(play)
        val history = toolkit.engineTags()
        flows.show(ready ?: withContext(Dispatchers.Default) { AutomatumBoards.sharedFlow(play, history) })
    }
    if (!shown) return
    val flow = flows.view?.takeIf { it.play.board === play.board } ?: return
    var confirm by remember(IdentityKey(play.board)) { mutableStateOf(false) }
    val viewer = toolkit.viewer
    fun name(player: String) = if (player == viewer) "You" else names[player] ?: "The other player"
    val stack = scene?.stackRegion ?: scene?.items?.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.bounds
    val handRow = scene?.items?.firstOrNull { it.kind == TableItemKind.HAND && it.owner == viewer }?.bounds
    val decision = flow.decision
    val holder = flow.priority
    val bar = flows.targeting || (decision == null && holder == viewer && flows.responding == play.currentNodeId)
    CastPanelPlacement(stack, if (bar) handRow?.center?.y else null) {
        when {
            decision != null && flows.answering != null -> when {
                flows.targeting -> FlowTargetBar(flows, flow, play, decision as Decision.Targets)
                flows.hidden -> HiddenEye(flows)
                else -> DecisionPanel(flows, flow, play, names, decision, forOther = decision.player.value != viewer,
                    otherName = name(decision.player.value))
            }
            decision != null -> WaitingBar("${name(decision.player.value)} ${if (decision.player.value == viewer) "are" else "is"} choosing — " +
                prompt(flows, flow, play, decision), "Choose for them") { confirm = true }
            holder == viewer -> when {
                flows.responding == play.currentNodeId -> RespondBar(flows, viewer)
                flows.hidden -> HiddenEye(flows)
                else -> RespondPrompt(flows, flow, play, names, viewer)
            }
            holder != null -> WaitingBar("${name(holder)} may respond to ${topName(flow, play)}", "Pass for them") { confirm = true }
        }
    }
    if (confirm) {
        val other = decision?.player?.value ?: holder
        if (other == null || other == viewer) { confirm = false; return }
        val who = name(other)
        AlertDialog(onDismissRequest = { confirm = false },
            title = { Text(if (decision != null) "Choose for $who?" else "Pass for $who?") },
            text = { Text(if (decision != null) "Automatum asks $who: ${prompt(flows, flow, play, decision)} You'll answer for them; " +
                "cards only $who can see stay hidden from you."
                else "$who may still want to respond to ${topName(flow, play)}. Passing for them lets it resolve.") },
            confirmButton = { TextButton({
                confirm = false
                if (decision != null) flows.chooseFor(decision) else flows.pass(other)
            }) { Text(if (decision != null) "Choose for them" else "Pass for them") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
    }
}

@Composable
private fun FlowSurface(description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(8.dp), color = Color.White,
        shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Column(Modifier.padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SlimBar(description: String, content: @Composable RowScope.() -> Unit) {
    Surface(Modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(22.dp), color = Color.White,
        shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun HiddenEye(flows: SharedFlowController) {
    Surface(Modifier.size(44.dp), shape = CircleShape, color = Color.White, shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        EyeButton(hidden = true, flows::toggleHidden, "Automatum panel")
    }
}

/** The priority prompt (CR 117.3d): what is on top of the Stack, Respond and Let it resolve. */
@Composable
private fun RespondPrompt(flows: SharedFlowController, flow: SharedFlow, play: PlayState, names: Map<String, String>, viewer: String) {
    FlowSurface("Respond prompt") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topTitle(flow, play, names, viewer), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            EyeButton(hidden = false, flows::toggleHidden, "Automatum panel")
        }
        flow.top?.let { item ->
            (flow.text(item) ?: topCard(flow, play)?.let { cardText(play, it) })?.let {
                Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f))
            }
            val targets = item.targets.values.flatten()
            if (targets.isNotEmpty()) Text("Target: " + targets.joinToString { targetName(flows, play, names + (viewer to "You"), it) }, fontSize = 12.sp)
        }
        val more = flow.state.stack.size - 1
        if (more > 0) Text("$more more below it on the Stack", fontSize = 11.sp, color = Color(0xff4b5a5f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton({ flows.respond(play) }) { Text("Respond") }
            Button({ flows.pass(viewer) }, colors = ButtonDefaults.buttonColors(containerColor = AutomatumViolet)) { Text("Let it resolve") }
        }
    }
}

/** Responding: the prompt as a bar over the hand while the player casts (double-tap or the hand menu's Cast). */
@Composable
private fun RespondBar(flows: SharedFlowController, viewer: String) {
    SlimBar("Responding") {
        Text("Respond — cast a card from your hand", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(flows::stopResponding) { Text("Back") }
        TextButton({ flows.pass(viewer) }) { Text("Let it resolve") }
    }
}

/** What the other player does now, with acting for them (D11) after a confirmation. */
@Composable
private fun WaitingBar(text: String, action: String, act: () -> Unit) {
    SlimBar("Automatum waiting") {
        Text(text, Modifier.weight(1f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        TextButton(act) { Text(action) }
    }
}

/** An open choice of a resolution or a trigger, for the player answering it. */
@Composable
private fun DecisionPanel(flows: SharedFlowController, flow: SharedFlow, play: PlayState, names: Map<String, String>,
    decision: Decision, forOther: Boolean, otherName: String) {
    FlowSurface("Automatum choice") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sourceName(flows, flow, play, decision), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            EyeButton(hidden = false, flows::toggleHidden, "Automatum panel")
        }
        if (forOther) Text("Choosing for $otherName", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AutomatumViolet)
        if (!hasOwnText(decision)) sourceText(flow, play, decision)?.let {
            Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f))
        }
        when (decision) {
            is Decision.ManaPayment -> {
                ManaSection(flows, flows, play, decision)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button({ flows.answer(Answer.ManaPayment(flows.mana)) }, enabled = flows.paid,
                        colors = ButtonDefaults.buttonColors(containerColor = AutomatumViolet)) { Text("Pay") }
                }
            }
            else -> OpenSection(flows, flows, play, names, decision)
        }
    }
}

/** Targets of a trigger, picked on the table as for a cast (I-D5, I-D6); there is no cancelling a shared flow. */
@Composable
private fun FlowTargetBar(flows: SharedFlowController, flow: SharedFlow, play: PlayState, decision: Decision.Targets) {
    SlimBar("Choose targets") {
        Text("${sourceName(flows, flow, play, decision)} — ${targetPrompt(decision)}", Modifier.weight(1f), fontSize = 13.sp,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (decision.max != 1 || decision.min == 0) TextButton(flows::confirmTargets, enabled = flows.picked.size >= decision.min) {
            Text(if (flows.picked.isEmpty()) "None" else "Done")
        }
    }
}

// ---- Words

/** "Alice cast Storm Spark", "You activated Elvish Seer", "Fertile Passing triggered". */
private fun topTitle(flow: SharedFlow, play: PlayState, names: Map<String, String>, viewer: String): String {
    val item = flow.top ?: return "Something is on the Stack"
    val who = if (item.controller.value == viewer) "You" else names[item.controller.value] ?: "The other player"
    val name = topName(flow, play)
    return when (item.kind) {
        StackItemKind.Spell, is StackItemKind.SpellCopy -> "$who cast $name"
        StackItemKind.Activated -> "$who activated $name"
        StackItemKind.Triggered -> "$name triggered"
    }
}

/** The name of the top of the Stack as its entry shows it (a card, or an ability whose source is gone, STK-022). */
private fun topName(flow: SharedFlow, play: PlayState): String {
    val entry = CombatStackRules.stack(play.board).cards.firstOrNull() ?: return "it"
    val card = CombatStackRules.inspection(play.board, entry) ?: return "it"
    return faceName(play, card) ?: "it"
}

private fun topCard(flow: SharedFlow, play: PlayState): String? =
    CombatStackRules.stack(play.board).cards.firstOrNull()?.let { CombatStackRules.inspection(play.board, it)?.id }

private fun faceName(play: PlayState, card: PlayCard): String? =
    play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
        ?.let { faces -> faces.singleOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name

/** The decision's source by name: the card or ability it is about. */
private fun sourceName(flows: SharedFlowController, flow: SharedFlow, play: PlayState, decision: Decision): String {
    val source = decision.source ?: return if (decision is Decision.OrderTriggers) "Triggers" else "Automatum"
    if (source is ObjectId.StackItem) flow.state.stackItems[source]?.source?.let { return objectName(flows, play, it) }
    return objectName(flows, play, source)
}

/** The printed text of the decision's source, for a choice without a sentence of its own. */
private fun sourceText(flow: SharedFlow, play: PlayState, decision: Decision): String? {
    val source = decision.source ?: return null
    flow.state.stackItems[source]?.let { item -> flow.text(item)?.let { return it } }
    return flow.atwCard(source)?.let { cardText(play, it) }
}

private fun hasOwnText(decision: Decision) =
    decision is Decision.May || decision is Decision.ChooseObjects || decision is Decision.Manual || decision is Decision.OrderTriggers

/** What the decision asks, in a few words: the card's sentence when it has one. */
private fun prompt(flows: SharedFlowController, flow: SharedFlow, play: PlayState, decision: Decision): String = when (decision) {
    is Decision.May -> decision.text
    is Decision.ChooseObjects -> decision.text
    is Decision.Manual -> decision.text
    is Decision.Targets -> "${sourceName(flows, flow, play, decision)}: ${targetPrompt(decision)}"
    is Decision.OrderTriggers -> "the order of their triggers."
    is Decision.LegendRule -> "which ${decision.name} to keep."
    is Decision.ChooseReplacement -> "which replacement applies first."
    is Decision.ManaPayment -> "whether to pay ${decision.cost}."
    is Decision.Modes -> "the modes of ${sourceName(flows, flow, play, decision)}."
    is Decision.Divide -> "how to divide ${decision.amount}."
    else -> "a choice for ${sourceName(flows, flow, play, decision)}."
}
