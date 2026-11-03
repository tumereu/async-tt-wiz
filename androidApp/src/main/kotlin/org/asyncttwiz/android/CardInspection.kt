package org.asyncttwiz.android

import org.asyncttwiz.automatum.ComputedBoard

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.asyncttwiz.protocol.*

/** Text remains available regardless of artwork/network state. No gameplay permission policy here. */
@Composable
internal fun CardInspection(controller: CardInspectionController, annotation: String? = null,
    markedOptions: List<Pair<Int, StackTextOption>> = emptyList(),
    token: Boolean = false, owner: Color? = null, projection: Boolean = false, peek: Boolean = false,
    peekScroll: ScrollState? = null, stackEntryId: String? = null,
    textOptions: List<StackTextOption> = emptyList(), faceDown: Boolean = false) {
    val state = controller.state ?: return
    val card = state.card
    val localScroll = key(card, state.face) { rememberScrollState() }
    val contentScroll = if (peek) peekScroll ?: localScroll else localScroll
    val content: @Composable () -> Unit = {
        Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth(.94f).fillMaxHeight(.94f)
            .semantics { paneTitle = "Card inspection" }, shape = MaterialTheme.shapes.large,
            color = Color.White, contentColor = MenuInk) {
            Column(Modifier.padding(12.dp)) {
                if (!peek) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(card.definition.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                         maxLines = 2, overflow = TextOverflow.Clip)
                    ManualActionSquare("Close card inspection", "", bordered = false, onClick = controller::close)
                }
                if (!peek && card.definition.faces.size > 1) InspectionFaceSwitcher(state.face, card.definition.faces.size, { previous, enabled, click ->
                    ManualActionSquare(if (previous) "Previous face" else "Next face", "", enabled, onClick = click)
                }, controller::face)
                key(card, state.face) {
                    val sideways = state.degrees % 180 != 0
                    val artwork: @Composable (Dp) -> Unit = { heightLimit ->
                        InspectionArtwork(state, heightLimit, peek, owner, projection, faceDown, controller)
                    }
                    val text: @Composable () -> Unit = {
                        InspectionText(state, annotation, markedOptions, textOptions, stackEntryId, peek)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val availableHeight = maxHeight
                        if (maxWidth > maxHeight && maxWidth >= 480.dp) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(if (sideways) 1.5f else 1f).fillMaxHeight().verticalScroll(rememberScrollState())) { artwork(availableHeight) }
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(contentScroll)
                                    .semantics { contentDescription = "Card inspection content" }) { text() }
                            }
                        } else {
                            val artworkHeight = if (peek) maxHeight * .45f else maxHeight
                            Column(Modifier.fillMaxSize().verticalScroll(contentScroll)
                                .semantics { contentDescription = "Card inspection content" }) {
                                artwork(artworkHeight); Spacer(Modifier.height(if (peek) 4.dp else 12.dp))
                                text(); Spacer(Modifier.height(if (peek) 4.dp else 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (peek) Popup(properties = PopupProperties(focusable = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)), contentAlignment = Alignment.Center) { content() }
    }
    else Dialog(onDismissRequest = controller::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MenuWindowDimming(); content()
    }
}

/** One card's artwork in an inspection or hold preview; sideways cards and previews stay within [heightLimit]. */
@Composable
private fun InspectionArtwork(state: CardInspectionState, heightLimit: Dp, peek: Boolean, owner: Color?, projection: Boolean,
    faceDown: Boolean, controller: CardInspectionController?) {
    val card = state.card
    val face = card.definition.faces[state.face]
    val orientation = card.orientation(state.face)
    val sideways = state.degrees % 180 != 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A card printed sideways shows turned and as large as fits: the full width, within the
            // height and never taller than a capped upright card.
            val (width, height) = inspectedArtworkSize(sideways, maxWidth,
                if (sideways || peek) heightLimit else Dp.Infinity)
            val density = LocalDensity.current
            val scale = if (!peek && state.enlarged) 2 else 1
            ArtworkViewport(Modifier.fillMaxWidth().height(height)
                .semantics { contentDescription = "Card artwork viewport" }) {
                CardArtwork(card.artwork[state.face], "Artwork for ${face.name}",
                    with(density) { (width * scale).roundToPx() }.coerceIn(1, 4096),
                    with(density) { (height * scale).roundToPx() }.coerceIn(1, 4096),
                    Modifier.requiredSize(width * scale, height * scale)
                        // Inspection always shows the whole printed card, including token corners.
                        .then(if (owner != null) Modifier.cardFrame(false, owner, projection) else Modifier),
                    placeholder = { InspectorArtworkPlaceholder(card.artwork[state.face], "Artwork for ${face.name}") },
                    degrees = state.degrees)
                // The owner's own face-down card: the front stays readable but is plainly marked.
                if (faceDown) Box(Modifier.requiredSize(width * scale, height * scale)
                    .background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                    Text("Face down", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        if (!peek) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton(if (state.enlarged) "Fit artwork" else "Enlarge artwork", Modifier.weight(1f), onClick = { controller?.zoom() })
            // Aftermath and flip halves: a personal turn to read the other half.
            if (orientation.alternate != null)
                ManualActionSquare(if (state.turned) "Rotate back" else "Rotate card", "", onClick = { controller?.turn() })
        }
    }
}

/** One card's text in an inspection or hold preview: annotation, Stack marks, name, cost, type, rules and printed values. */
@Composable
private fun InspectionText(state: CardInspectionState, annotation: String?, markedOptions: List<Pair<Int, StackTextOption>>,
    textOptions: List<StackTextOption>, stackEntryId: String?, peek: Boolean) {
    val face = state.card.definition.faces[state.face]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (peek) 4.dp else 12.dp)) {
        annotation?.let { Text(it) }
        // Current-face marks are highlighted inline below; list other faces too.
        for ((index, option) in markedOptions.filter { it.second.faceId != face.faceId }) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StackMarkChip(index)
            CardText(option.text)
        }
        val marked = markedOptions.filter { it.second.faceId == face.faceId }
        @Composable fun markedText(value: String, paragraph: Int?, title: Boolean = false) {
            val selected = marked.firstOrNull { it.second.paragraph == paragraph }
            val lineIndex = textOptions.indexOfFirst { it.faceId == face.faceId && it.paragraph == paragraph }
            val flash = if (stackEntryId != null && lineIndex >= 0) Modifier.tableChangeFlash(LocalTableChanges.current,
                TableChangeKey(stackEntryId, TableChangeKind.TEXT_MARK, line = lineIndex)) else Modifier
            Row((if (selected != null) Modifier.fillMaxWidth().background(stackMarkColor(selected.first).copy(alpha = .16f),
                RoundedCornerShape(4.dp)).padding(6.dp).semantics { stateDescription = "Stack text mark ${selected.first + 1}" } else Modifier).then(flash),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected != null) StackMarkChip(selected.first)
                CardText(value, style = if (title) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
            }
        }
        markedText(face.name, null, title = true)
        if (face.manaCost.isNotEmpty()) CardText(face.manaCost)
        if (face.typeLine.isNotEmpty()) Text(face.typeLine)
        face.rulesText.lines().map(String::trim).filter(String::isNotEmpty).forEachIndexed { index, paragraph ->
            markedText(paragraph, index)
        }
         if (face.power.isNotEmpty() || face.toughness.isNotEmpty()) Text("${face.power}/${face.toughness}")
        if (face.loyalty.isNotEmpty()) Text("Loyalty: ${face.loyalty}")
        if (face.defense.isNotEmpty()) Text("Defense: ${face.defense}")
        if (face.corner.isNotEmpty() && face.power.isEmpty() && face.toughness.isEmpty() &&
            face.loyalty.isEmpty() && face.defense.isEmpty()) Text(face.corner)
    }
}

/**
 * Face switcher between an inspection's title and its artwork: two square chevron buttons drawn by [button]
 * (true = previous) around a centred "n / m", spaced off the title above and the artwork below.
 */
@Composable
internal fun InspectionFaceSwitcher(face: Int, count: Int,
    button: @Composable (previous: Boolean, enabled: Boolean, click: () -> Unit) -> Unit, select: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        button(true, face > 0) { select(face - 1) }
        Text("${face + 1} / $count", Modifier.widthIn(min = 56.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium)
        button(false, face < count - 1) { select(face + 1) }
    }
}

/** Game inspection filters playable faces BEFORE entering the general deck/art inspector. */
@Composable
internal fun GameplayCardInspection(state: PlayState, viewer: String, inspection: ManualInspection, close: () -> Unit,
    names: Map<String, String> = emptyMap(), peek: Boolean = false, peekScroll: ScrollState? = null) {
    if (inspection.gameId != state.gameId || inspection.playId != state.id) return
    BackHandler(enabled = peek, onBack = close)
    val computed = LocalComputedBoard.current
    val card = inspection.snapshot ?: CombatStackRules.inspection(state.board, inspection.cardId) ?: return
    // TBL-111: holding a battlefield card that lies on others previews it and every card under it.
    val below = if (peek && inspection.snapshot == null) battlefieldPileBelow(state.board, inspection.cardId) else emptyList()
    if (below.isNotEmpty()) {
        val cards = remember(state, viewer, inspection, names) {
            listOf(pilePeekCard(state, viewer, card, names, inspection, computed)) + below.map { pilePeekCard(state, viewer, it, names, null, computed) }
        }
        PilePeek(cards, peekScroll ?: rememberScrollState())
        return
    }
    val definition = state.definitions.singleOrNull { it.digest == card.definition }?.definition
    // A face-down entry (STK-005) shows its holder the front marked "Face down", like an own face-down card.
    val faceDown = ManualDisclosure.faceDownOutsideHiddenZones(state.board, card) ||
        CombatStackRules.faceDownEntry(state.board, inspection.cardId)
    // Only the deliberate, logged "Inspect other side" shows an opponent a face-down front; ordinary inspects never do.
    val allowed = if (inspection.explicit) inspection.faceIds else if (ManualDisclosure.faceDownFrontHidden(state, card, viewer)) emptyList() else
        ManualDisclosure.permittedFaces(state, card, viewer, inspecting = true).map { it.faceId }.filter { it in inspection.faceIds }
    val faces = definition?.faces.orEmpty().filter { it.faceId in allowed }
    if (faces.isEmpty()) {
        if (peek) Popup(properties = PopupProperties(focusable = false)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface { Text(if (definition == null) "Card data unavailable" else "Covered card", Modifier.padding(24.dp)) }
            }
        } else AlertDialog(onDismissRequest = close, title = { Text(if (definition == null) "Card data unavailable" else "Covered card") },
            text = { Text(listOfNotNull("Owner ${names[card.owner] ?: "Player"}",
                // A face-down Battlefield card's values are public (rules v23); its face stays hidden.
                *(if (publicFaceDown(state.board, card)) arrayOf(computedCorner(computed, card, viewer) ?: inspectedCorner(card).takeIf(String::isNotBlank),
                    card.damage.takeIf(TablePresentation::nonzero)?.let { "Damage: $it" },
                    card.eotNote.takeIf(String::isNotBlank)?.let { "⌛ $it (until end of turn)" },
                    card.counters.entries.joinToString(" · ") { "${it.key.name.lowercase()}: ${it.value}" }.takeIf(String::isNotBlank),
                    card.note.takeIf(String::isNotBlank)) else emptyArray())).joinToString("\n")) },
            confirmButton = { TextButton(onClick = close) { Text("Close") } })
    } else {
        // The full name only when every face is permitted: a partial view never names a face it hides.
        val filtered = definition!!.copy(name = if (faces.size == definition.faces.size) definition.name else faces.first().name, faces = faces)
        val full = remember(card.definition, definition) { InspectedCard.retained(card.definition, definition) }
        val permitted = remember(full, filtered) { InspectedCard(filtered,
            faces.map { face -> full.artwork[definition.faces.indexOf(face)] }) }
        val marks = inspection.stackEntryId?.let { entry -> state.board.stackTextMarks[entry].orEmpty().sorted().mapNotNull { index ->
            StackText.options(state, entry).getOrNull(index)?.takeIf { it.faceId in allowed }?.let { index to it }
        } }.orEmpty()
        // Open on the face the card currently shows (a transformed card shows its back), among the permitted faces only.
        val shown = faces.indexOfFirst { it.faceId == card.faceId }
        val flipped = flippedOnTable(definition, card.faceId)
        val controller = remember(inspection) { CardInspectionController().apply {
            open(permitted, shown, flipped)
            marks.firstOrNull()?.second?.faceId?.let { id -> faces.indexOfFirst { it.faceId == id }.takeIf { it >= 0 }?.let(::face) }
        } }
        LaunchedEffect(permitted) { if (controller.state?.card != permitted) controller.open(permitted, shown, flipped) }
        val annotation = remember(card, names, inspection.provisional, computed, viewer, IdentityKey(state.board)) {
            listOfNotNull(inspectionAnnotation(card, names, inspection.provisional, computed, viewer).ifBlank { null },
                automatumReasons(state, viewer, card.id, names).joinToString("\n").ifBlank { null }).joinToString("\n") }
        if (controller.state != null) CardInspection(controller, annotation,
            markedOptions = marks,
            token = card.token, owner = if (card.owner == viewer) OwnTableColor else OpponentTableColor,
             projection = inspection.cardId in CombatStackRules.projectionIds(state.board), peek = peek, peekScroll = peekScroll,
             stackEntryId = inspection.stackEntryId, textOptions = inspection.stackEntryId?.let { StackText.options(state, it) }.orEmpty(),
             faceDown = faceDown)
        // Inner dialog's native Back/outside/close must also release the retained outer route.
        LaunchedEffect(controller.state) { if (controller.state == null) close() }
    }
}

/** The text above a game card's inspection: owner, tapped state and the card's current table values; with Automatum
 * on (I1), where its computed values come from instead of the manual corner. */
private fun inspectionAnnotation(card: PlayCard, names: Map<String, String>, provisional: Boolean,
    computed: ComputedBoard = ComputedBoard.NONE, viewer: String = ""): String = listOfNotNull(
    "Provisional inspection · awaiting shared result".takeIf { provisional },
    "Owner ${names[card.owner] ?: "Player"} · ${if (card.tapped) "tapped" else "untapped"}",
    computedCorner(computed, card, viewer) ?: inspectedCorner(card).takeIf(String::isNotBlank),
    card.damage.takeIf(TablePresentation::nonzero)?.let { "Damage: $it" },
    card.eotNote.takeIf(String::isNotBlank)?.let { "⌛ $it (until end of turn)" },
    card.counters.entries.joinToString(" · ") { "${it.key.name.lowercase()}: ${it.value}" }.takeIf(String::isNotBlank),
    card.note.takeIf(String::isNotBlank)).joinToString("\n")

/**
 * I-D46 (b): with Automatum on, why the viewer can't do now what Automatum offers for [cardId] (cast or play it, an
 * ability), one line each ("Cast — Only in your main phase while the Stack is empty."), from the plays already worked out
 * for this board (none while they aren't, and none for other players' cards).
 */
internal fun automatumReasons(state: PlayState, viewer: String, cardId: String, names: Map<String, String>): List<String> {
    if (!state.board.automatum) return emptyList()
    val plays = AutomatumBoards.cachedCardPlays(state, viewer) ?: return emptyList()
    val name = CastController.cardName(state, cardId)
    return plays.of(cardId).mapNotNull { offered ->
        val refusal = offered.refusal?.takeIf { it != org.asyncttwiz.automatum.CardPlay.Refusal.NotYours } ?: return@mapNotNull null
        val label = when (offered.kind) {
            org.asyncttwiz.automatum.CardPlay.Kind.Cast -> if (offered.text == "Cast") "Cast" else "Cast · ${offered.text}"
            else -> offered.text
        }
        "$label — ${engineRefusal(refusal, offered.kind, name, names)}"
    }
}

/** Automatum on (I1): where [card]'s computed values come from, one line each ("7/7: 1/1 printed, +1/+1 Elvish
 * Hierophant, +3/+3 Giant Growth until end of turn"), then its typed counters; null when nothing is computed for it. */
internal fun computedCorner(computed: ComputedBoard, card: PlayCard, viewer: String): String? {
    val values = computed.cards[card.id] ?: return null
    return (computed.explain(card.id, viewer) +
        listOfNotNull(values.counters.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Counters: ")))
        .joinToString("\n").ifBlank { null }
}

/** TBL-111: the cards under [cardId] in its battlefield pile (TBL-053), top first; empty when nothing lies under it. */
internal fun battlefieldPileBelow(board: BoardState, cardId: String): List<PlayCard> {
    val card = board.cards.singleOrNull { it.id == cardId } ?: return emptyList()
    val placement = card.placement ?: return emptyList()
    val field = board.containers.singleOrNull { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } ?: return emptyList()
    val byId = board.cards.associateBy { it.id }
    // The container order is the pile order, top first (TablePresentation.logicalPlacements).
    val pile = field.cards.mapNotNull { byId[it] }.filter { it.placement == placement }
    return pile.drop(pile.indexOfFirst { it.id == cardId } + 1)
}

/** One card of a pile's hold preview: what an ordinary hold preview of it shows, or only that it is covered. */
internal sealed interface PilePeekCard {
    val cardId: String
    data class Shown(override val cardId: String, val state: CardInspectionState, val annotation: String, val owner: Color,
        val faceDown: Boolean) : PilePeekCard
    data class Covered(override val cardId: String, val text: String) : PilePeekCard
}

/** [card] as its own hold preview shows it to [viewer] (a face-down front stays hidden from the opponent, TBL-109). */
private fun pilePeekCard(state: PlayState, viewer: String, card: PlayCard, names: Map<String, String>,
    inspection: ManualInspection?, computed: ComputedBoard): PilePeekCard {
    val definition = state.definitions.singleOrNull { it.digest == card.definition }?.definition
        ?: return PilePeekCard.Covered(card.id, "Card data unavailable")
    val allowed = if (ManualDisclosure.faceDownFrontHidden(state, card, viewer)) emptyList() else
        ManualDisclosure.permittedFaces(state, card, viewer, inspecting = true).map { it.faceId }
            .filter { inspection == null || it in inspection.faceIds }
    val faces = definition.faces.filter { it.faceId in allowed }
    if (faces.isEmpty()) return PilePeekCard.Covered(card.id, "Covered card")
    val filtered = definition.copy(name = if (faces.size == definition.faces.size) definition.name else faces.first().name, faces = faces)
    val full = InspectedCard.retained(card.definition, definition)
    val shown = InspectedCard(filtered, faces.map { face -> full.artwork[definition.faces.indexOf(face)] })
    val opened = CardInspectionController().apply {
        open(shown, faces.indexOfFirst { it.faceId == card.faceId }, flippedOnTable(definition, card.faceId))
    }.state!!
    return PilePeekCard.Shown(card.id, opened, inspectionAnnotation(card, names, inspection?.provisional == true, computed, viewer),
        if (card.owner == viewer) OwnTableColor else OpponentTableColor, ManualDisclosure.faceDownOutsideHiddenZones(state.board, card))
}

/** TBL-111: a pile's hold preview: the held card, then each card under it, one below the other in one scrolling column. */
@Composable
private fun PilePeek(cards: List<PilePeekCard>, scroll: ScrollState) {
    Popup(properties = PopupProperties(focusable = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth(.94f).fillMaxHeight(.94f)
                .semantics { paneTitle = "Card inspection" }, shape = MaterialTheme.shapes.large,
                color = Color.White, contentColor = MenuInk) {
                BoxWithConstraints(Modifier.padding(12.dp).fillMaxSize()) {
                    val wide = maxWidth > maxHeight && maxWidth >= 480.dp
                    val height = maxHeight
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).semantics { contentDescription = "Card inspection content" },
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.forEachIndexed { index, card -> key(card.cardId) {
                            if (index > 0) HorizontalDivider(color = MenuBorder)
                            when (card) {
                                is PilePeekCard.Covered -> Text(card.text, Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    textAlign = TextAlign.Center)
                                is PilePeekCard.Shown -> {
                                    val artwork: @Composable (Dp) -> Unit = { limit ->
                                        InspectionArtwork(card.state, limit, true, card.owner, false, card.faceDown, null)
                                    }
                                    val text: @Composable () -> Unit = {
                                        InspectionText(card.state, card.annotation, emptyList(), emptyList(), null, true)
                                    }
                                    // Like the single preview: side by side on a wide screen, the picture above the text otherwise.
                                    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Box(Modifier.weight(if (card.state.degrees % 180 != 0) 1.5f else 1f)) { artwork(height) }
                                        Box(Modifier.weight(1f)) { text() }
                                    } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        artwork(height * .45f)
                                        text()
                                    }
                                }
                            }
                        } }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
