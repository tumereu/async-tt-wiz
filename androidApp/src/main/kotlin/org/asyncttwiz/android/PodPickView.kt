package org.asyncttwiz.android

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import kotlin.math.roundToInt

private val PodPickGreen = Color(0xFF237A38)
private val PodPickRed = Color(0xFFB3261E)

@Composable
fun PodPickView(
    pack: DraftPrivatePack?,
    packsBehind: Int,
    settings: DraftSettings,
    definitions: Map<String, CardDefinition>,
    waitingOn: List<AccountIdentity>,
    busy: Boolean,
    onConfirm: (keep: List<String>, burn: List<String>) -> Unit,
    onOpenBuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var marks by remember(pack?.id) { mutableStateOf(PodPickMarks().forPack(pack)) }
    val inspection = remember(pack?.id) { CardInspectionController() }
    val inspect: (DraftCopy) -> Unit = { copy ->
        definitions[copy.definition]?.takeIf { it.faces.isNotEmpty() }?.let {
            inspection.open(InspectedCard.retained(copy.definition, it))
        }
    }
    if (pack == null) {
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                waitingOn.forEach { Avatar(it) }
            }
            Text(if (waitingOn.isEmpty()) "Waiting for the next round"
                else "Waiting for ${waitingOn.joinToString { it.username }}", Modifier.padding(vertical = 12.dp))
            Button(onClick = onOpenBuild) { Text("Build") }
        }
        return
    }

    val rules = remember(pack, settings) { PodPickRules(pack, settings) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val preferences = rememberPodPickPreferences()
    val preferenceKey = if (landscape) "columns-landscape" else "columns-portrait"
    var preferredColumns by remember(preferences, preferenceKey) {
        mutableIntStateOf(preferences.getInt(preferenceKey, if (landscape) 5 else 3))
    }
    val maxColumns = pack.cards.size.coerceAtLeast(1)
    val columns = preferredColumns.coerceIn(1, maxColumns)
    val burnIds = rules.burnIds(marks)
    val copies = remember(pack) { pack.cards.associateBy { it.id } }
    val mark: (String, PodPickMark) -> Unit = { id, kind ->
        if (!busy) marks = marks.toggle(id, kind, rules)
    }
    val unmark: (String) -> Unit = { id -> if (!busy) marks = marks.unmark(id) }

    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pack ${pack.number}/${settings.packsPerPlayer} · ${pack.cards.size} cards · +$packsBehind waiting",
                Modifier.weight(1.8f), style = MaterialTheme.typography.bodySmall)
            Slider(columns.toFloat(), onValueChange = {
                preferredColumns = it.roundToInt().coerceIn(1, maxColumns)
                preferences.edit().putInt(preferenceKey, preferredColumns).apply()
            }, modifier = Modifier.weight(1f).semantics { contentDescription = "Cards per row" },
                valueRange = 1f..maxColumns.toFloat(), steps = (maxColumns - 2).coerceAtLeast(0), enabled = maxColumns > 1)
            Text("$columns", style = MaterialTheme.typography.bodySmall)
        }
        key(pack.id) {
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f).fillMaxWidth().testTag("pod-pick-list"),
                state = rememberLazyGridState(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(pack.cards, key = { it.id }) { copy ->
                    PodPickCard(copy, definitions[copy.definition], marks.markOf(copy.id),
                        automaticBurn = rules.automaticBurns && copy.id in burnIds,
                        burnEnabled = rules.manualBurns > 0, enabled = !busy,
                        onMark = { mark(copy.id, it) }, onUnmark = { unmark(copy.id) }, onInspect = { inspect(copy) })
                }
            }
        }
        PodPickTray("Keep", marks.keeps, rules.requiredKeeps, copies, definitions, PodPickGreen,
            landscape, automatic = false, enabled = !busy, onUnmark = unmark, onInspect = inspect)
        if (settings.burn > 0) PodPickTray("Burn", burnIds, rules.requiredBurns, copies, definitions, PodPickRed,
            landscape, automatic = rules.automaticBurns, enabled = !busy, onUnmark = unmark, onInspect = inspect)
        Button(onClick = { if (rules.canConfirm(marks, busy)) onConfirm(marks.keeps.toList(), rules.burnIds(marks).toList()) },
            enabled = rules.canConfirm(marks, busy), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (busy) "Confirming…" else "Confirm")
        }
    }
    // Reuse the responsive full-size inspector, including face navigation and artwork zoom.
    CardInspection(inspection)
}

@Composable
private fun rememberPodPickPreferences() = LocalContext.current.applicationContext.let { context ->
    remember(context) { context.getSharedPreferences("pod-pick", Context.MODE_PRIVATE) }
}

@Composable
private fun PodPickCard(
    copy: DraftCopy,
    definition: CardDefinition?,
    mark: PodPickMark?,
    automaticBurn: Boolean,
    burnEnabled: Boolean,
    enabled: Boolean,
    onMark: (PodPickMark) -> Unit,
    onUnmark: () -> Unit,
    onInspect: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val currentOnMark by rememberUpdatedState(onMark)
    val flingThreshold = with(LocalDensity.current) { 800.dp.toPx() }
    val effectiveMark = mark ?: PodPickMark.BURN.takeIf { automaticBurn }
    val color = when (effectiveMark) { PodPickMark.KEEP -> PodPickGreen; PodPickMark.BURN -> PodPickRed; null -> null }
    Box {
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).onSizeChanged { width = it.width }
            .testTag("pod-pick-card-${copy.id}").semantics {
                contentDescription = definition?.name ?: "Card data unavailable"
                stateDescription = when (effectiveMark) {
                    PodPickMark.KEEP -> "Keep"
                    PodPickMark.BURN -> if (automaticBurn) "Automatic burn" else "Burn"
                    null -> "Unmarked"
                }
            }
            .pointerInput(enabled, burnEnabled, flingThreshold) {
                if (!enabled) return@pointerInput
                val velocity = VelocityTracker()
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f; velocity.resetTracking() },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        drag += amount
                        offset = if (burnEnabled) drag else drag.coerceAtLeast(0f)
                        velocity.addPosition(change.uptimeMillis, change.position)
                    },
                    onDragEnd = {
                        podPickSwipe(drag, width, velocity.calculateVelocity().x, flingThreshold)?.let {
                            if (it != PodPickMark.BURN || burnEnabled) currentOnMark(it)
                        }
                        offset = 0f
                    },
                    onDragCancel = { offset = 0f },
                )
            }
            .combinedClickable(onClick = { if (enabled) menu = true }, onLongClick = onInspect)
            .graphicsLayer { translationX = offset }
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .then(if (color != null) Modifier.border(3.dp, color, RoundedCornerShape(4.dp)) else Modifier)
            .padding(3.dp)) {
            PodPickArtwork(copy, definition, Modifier.fillMaxSize())
            if (offset != 0f) Box(Modifier.fillMaxSize().background(
                (if (offset > 0) PodPickGreen else PodPickRed).copy(alpha = .35f)))
            if (effectiveMark != null) Text(if (effectiveMark == PodPickMark.KEEP) "✓" else "🔥",
                Modifier.align(Alignment.TopEnd).background(color!!).padding(4.dp), color = Color.White)
        }
        DropdownMenu(menu && enabled, onDismissRequest = { menu = false }) {
            if (mark != PodPickMark.KEEP) DropdownMenuItem(text = { Text("Keep") }, onClick = { menu = false; onMark(PodPickMark.KEEP) })
            if (burnEnabled && mark != PodPickMark.BURN) DropdownMenuItem(text = { Text("Burn") }, onClick = { menu = false; onMark(PodPickMark.BURN) })
            if (mark != null) DropdownMenuItem(text = { Text("Unmark") }, onClick = { menu = false; onUnmark() })
        }
    }
}

@Composable
private fun PodPickTray(
    label: String, ids: List<String>, required: Int, copies: Map<String, DraftCopy>,
    definitions: Map<String, CardDefinition>, color: Color, landscape: Boolean, automatic: Boolean,
    enabled: Boolean, onUnmark: (String) -> Unit, onInspect: (DraftCopy) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).background(color.copy(alpha = .16f), RoundedCornerShape(4.dp))
        .padding(4.dp).testTag("pod-pick-${label.lowercase()}-tray"), verticalAlignment = Alignment.CenterVertically) {
        Text("${if (label == "Keep") "✓" else "🔥"} $label ${ids.size}/$required${if (automatic) " · auto" else ""}",
            Modifier.width(if (automatic) 128.dp else 96.dp), style = MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.weight(1f).height(if (landscape) 32.dp else 48.dp)) {
            val cardWidth = minOf(maxHeight / 1.4f, maxWidth / ids.size.coerceAtLeast(1))
            Row {
                ids.forEach { id -> copies[id]?.let { copy ->
                    key(id) {
                        PodPickArtwork(copy, definitions[copy.definition], Modifier.width(cardWidth).fillMaxHeight()
                            .testTag("pod-pick-tray-$id").semantics {
                                contentDescription = "${definitions[copy.definition]?.name ?: "Card"}, ${if (automatic) "automatic burn" else label.lowercase()} tray"
                            }.combinedClickable(onClick = { if (enabled && !automatic) onUnmark(id) }, onLongClick = { onInspect(copy) }))
                    }
                } }
            }
        }
    }
}

@Composable
private fun PodPickArtwork(copy: DraftCopy, definition: CardDefinition?, modifier: Modifier) {
    val card = remember(copy.definition, definition) {
        definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(copy.definition, it) }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        CardArtwork(card?.artwork?.firstOrNull()?.takeIf { it.printing != null || it.customUrl != null },
            definition?.name ?: "Card data unavailable",
            with(density) { maxWidth.roundToPx() }.coerceIn(1, 4096),
            with(density) { maxHeight.roundToPx() }.coerceIn(1, 4096), Modifier.fillMaxSize())
    }
}
