package org.asyncttwiz.android

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*
import kotlin.math.abs
import kotlin.math.roundToInt

private val PodBuildSidePadding = 48.dp

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PodBuildView(
    build: PodDeckBuild,
    copies: Map<String, DraftCopy>,
    definitions: Map<String, CardDefinition>,
    sideboardLimit: Int?,
    editable: Boolean,
    canSubmit: Boolean,
    onMove: (copyId: String, category: PodDeckCategory) -> Unit,
    onAdjustBasic: (index: Int, delta: Int) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    deckName: String = "Deck",
    onRename: (String) -> Unit = {},
    submitted: Boolean = !editable,
) {
    val context = LocalContext.current
    val orientation = LocalConfiguration.current.orientation
    val preferences = remember(context) { context.getSharedPreferences("pod-build", Context.MODE_PRIVATE) }
    val preferenceKey = "columns-$orientation"
    var columns by remember(preferences, preferenceKey) { mutableIntStateOf(preferences.getInt(preferenceKey, 7).coerceIn(2, 10)) }
    val submitEnabled = podBuildSubmitEnabled(build, copies, sideboardLimit, editable, canSubmit)
    var confirmSubmit by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val inspector = remember { CardInspectionController() }
    var feedback by remember { mutableStateOf<Pair<Int, PodDeckCategory>?>(null) }
    var feedbackId by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedback) { if (feedback != null) { delay(850); feedback = null } }
    val move: (String, PodDeckCategory) -> Unit = { id, destination ->
        if (editable) {
            feedback = ++feedbackId to destination
            onMove(id, destination)
        }
    }
    Box(modifier) {
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = PodBuildSidePadding),
            verticalAlignment = Alignment.CenterVertically) {
            Text(deckName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton({ naming = true }) { EntryIcon("Name deck", "edit") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("pod-build"), contentPadding = PaddingValues(bottom = 12.dp)) {
            for (category in PodDeckCategory.entries) {
                val count = podBuildCount(build, category)
                val buckets = podBuildColumns(build, category, copies, definitions, columns)
                stickyHeader(key = "header-$category") {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = PodBuildSidePadding).heightIn(min = 40.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (category == PodDeckCategory.MAIN) podBuildMainTitle(build) else "${category.podBuildLabel()} $count",
                                Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                            if (category == PodDeckCategory.MAIN) PodColumnControl("$columns", "Columns", columns, 2..10) { value ->
                                columns = value
                                preferences.edit().putInt(preferenceKey, columns).apply()
                            }
                        }
                    }
                }
                if (category == PodDeckCategory.MAIN) item("basics") { PodBuildBasics(build.basics, editable, onAdjustBasic) }
                item("columns-$category") {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = PodBuildSidePadding, vertical = 12.dp)) {
                        val width by animateDpAsState((maxWidth - (columns - 1).dp * 2) / columns, label = "column-width")
                        val tint = MaterialTheme.colorScheme.primary.copy(alpha = .045f)
                        Row(Modifier.fillMaxWidth().animateContentSize().drawBehind {
                            repeat(columns) { index -> drawRect(tint, Offset(index * (width.toPx() + 2.dp.toPx()), 0f), Size(width.toPx(), size.height)) }
                        }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            buckets.forEachIndexed { index, bucket ->
                                Column(Modifier.width(width)) {
                                    Text(PodDeckColumns.buckets(columns)[index].label,
                                        Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = TextAlign.Center, fontSize = 11.sp)
                                    AnimatedContent(bucket, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "column-cards") { tiles ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            tiles.forEach { tile -> key(tile.digest, tile.copyIds.first()) {
                                                PodBuildCard(tile, category, editable, move) {
                                                    tile.definition?.takeIf { it.faces.isNotEmpty() }?.let {
                                                        inspector.open(InspectedCard.retained(tile.digest, it))
                                                    }
                                                }
                                            } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = PodBuildSidePadding).height(48.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            if (submitted) Box(contentAlignment = Alignment.Center) { Text("✓ Submitted", style = MaterialTheme.typography.labelLarge) }
            else Button({ confirmSubmit = true }, Modifier.fillMaxSize(), enabled = submitEnabled,
                shape = MaterialTheme.shapes.extraSmall) { Text("Submit deck (${podBuildCount(build, PodDeckCategory.MAIN) + build.basics.total})") }
        }
      }
        feedback?.let { (_, category) ->
            Surface(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small, shadowElevation = 4.dp) {
                Text("→ ${category.podBuildLabel()}", Modifier.padding(12.dp))
            }
        }
    }
    if (confirmSubmit && submitEnabled) AlertDialog(
        onDismissRequest = { confirmSubmit = false },
        title = { Text("Submit deck?") },
        confirmButton = { Button(onClick = { confirmSubmit = false; onSubmit() }) { Text("Submit") } },
        dismissButton = { OutlinedButton(onClick = { confirmSubmit = false }) { Text("Cancel") } },
    )
    if (naming) {
        var name by remember { mutableStateOf(deckName) }
        AlertDialog(onDismissRequest = { naming = false }, title = { Text("Deck name") },
            text = { OutlinedTextField(name, { name = it.take(120) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { FilledIconButton({ onRename(name.trim()); naming = false }, enabled = name.isNotBlank()) { EntryIcon("Save name", "ready") } },
            dismissButton = { OutlinedIconButton({ naming = false }) { EntryIcon("Cancel", "close") } })
    }
    LaunchedEffect(submitEnabled) { if (!submitEnabled) confirmSubmit = false }
    inspector.state?.card?.let { PodCardArtPreview(it, it.definition.name, onDismiss = inspector::close) }
}

@Composable
private fun PodBuildBasics(basics: PodDeckBasics, editable: Boolean, onAdjust: (Int, Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = PodBuildSidePadding, vertical = 4.dp)) {
        Surface(onClick = { open = true }, enabled = editable, shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()
                .height(48.dp).testTag("pod-build-basics").semantics { contentDescription = "Edit basic lands" }) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DraftColor.entries.forEachIndexed { index, color ->
                    CardText("{${color.name}} ${basics.podBuildValues()[index]}", Modifier.weight(1f), maxLines = 1)
                }
            }
        }
    }
    if (open && editable) AlertDialog(onDismissRequest = { open = false }, title = { Text("Basic lands") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf("Plains", "Island", "Swamp", "Mountain", "Forest").forEachIndexed { index, name ->
                val count = basics.podBuildValues()[index]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CardText("{${DraftColor.entries[index].name}}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    OutlinedIconButton({ onAdjust(index, -1) }, enabled = count > 0) { Text("−", Modifier.semantics { contentDescription = "Remove $name" }) }
                    Text("$count", Modifier.width(48.dp), textAlign = TextAlign.Center)
                    OutlinedIconButton({ onAdjust(index, 1) }, enabled = count < Int.MAX_VALUE) { Text("+", Modifier.semantics { contentDescription = "Add $name" }) }
                }
            }
        } }, confirmButton = { FilledIconButton({ open = false }) { EntryIcon("Done", "ready") } })
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PodBuildCard(
    tile: PodBuildTile,
    category: PodDeckCategory,
    editable: Boolean,
    onMove: (String, PodDeckCategory) -> Unit,
    onInspect: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(editable) { if (!editable) { menu = false; drag = 0f } }
    val latestMove by rememberUpdatedState(onMove)
    val threshold = with(LocalDensity.current) { 20.dp.toPx() }
    val right = PodDeckRules.swipe(category, PodDeckSwipe.RIGHT)
    val left = PodDeckRules.swipe(category, PodDeckSwipe.LEFT)
    val name = tile.definition?.name ?: "Card details unavailable"
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("pod-build-card-${tile.copyIds.first()}")) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val height = (width * 1.4f).roundToInt().coerceAtLeast(1)
        val reference = remember(tile.digest, tile.definition, width) {
            tile.definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(tile.digest, it).artwork.firstOrNull() }
                ?.takeIf { it.printing != null || it.customUrl != null }
                ?.copy(size = "normal")
        }
        val gesture = if (!editable) Modifier else Modifier.pointerInput(tile.copyIds.first(), category, threshold) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                onDragCancel = { drag = 0f },
                onDragEnd = {
                    if (abs(drag) >= threshold) latestMove(tile.copyIds.first(), if (drag > 0) right else left)
                    drag = 0f
                },
            )
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f)
            .offset { IntOffset(drag.coerceIn(-threshold, threshold).roundToInt(), 0) }
            .then(gesture)
            .combinedClickable(onClick = { if (editable) menu = true }, onLongClick = onInspect,
                onLongClickLabel = "Inspect $name", onClickLabel = if (editable) "Move $name" else "${name} (read-only)")
            .semantics { contentDescription = "$name, ${tile.copyIds.size} copies, ${category.podBuildLabel()}" }) {
            CardArtwork(reference, name, width, height, Modifier.fillMaxSize(), placeholder = { PodCardPlaceholder(name) })
            if (tile.copyIds.size > 1) Surface(Modifier.align(Alignment.TopEnd), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("×${tile.copyIds.size}", Modifier.padding(2.dp), fontSize = 11.sp)
            }
            if (drag != 0f) Surface(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("→ ${(if (drag > 0) right else left).podBuildLabel()}", Modifier.padding(2.dp), fontSize = 11.sp)
            }
        }
        PodCardMenu(expanded = menu && editable, dismiss = { menu = false }) {
            listOf(right, left).forEach { destination ->
                DropdownMenuItem(text = { Text("→ ${destination.podBuildLabel()}") }, onClick = {
                    menu = false
                    latestMove(tile.copyIds.first(), destination)
                })
            }
        }
    }
}
