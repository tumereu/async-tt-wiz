package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*
import kotlin.math.abs
import kotlin.math.roundToInt

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
    onBasics: (PodDeckBasics) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val orientation = LocalConfiguration.current.orientation
    val preferences = remember(context) { context.getSharedPreferences("pod-build", Context.MODE_PRIVATE) }
    val preferenceKey = "columns-$orientation"
    var columns by remember(preferences, preferenceKey) { mutableIntStateOf(preferences.getInt(preferenceKey, 7).coerceIn(2, 10)) }
    val issues = remember(build, copies, sideboardLimit) { PodDeckRules.validate(build, copies.keys.toList(), sideboardLimit) }
    val submitEnabled = podBuildSubmitEnabled(build, copies, sideboardLimit, editable, canSubmit)
    var confirmSubmit by remember { mutableStateOf(false) }
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
        LazyColumn(Modifier.fillMaxSize().testTag("pod-build"), contentPadding = PaddingValues(bottom = 16.dp)) {
            item("columns") {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text("Columns $columns", style = MaterialTheme.typography.labelLarge)
                    Slider(value = columns.toFloat(), onValueChange = { value ->
                        columns = value.roundToInt().coerceIn(2, 10)
                        preferences.edit().putInt(preferenceKey, columns).apply()
                    }, valueRange = 2f..10f, steps = 7, modifier = Modifier.semantics { contentDescription = "Columns" })
                    if (!editable) Text("Deck submitted", style = MaterialTheme.typography.titleMedium)
                }
            }
            for (category in PodDeckCategory.entries) {
                val count = podBuildCount(build, category)
                val buckets = podBuildColumns(build, category, copies, definitions, columns)
                stickyHeader(key = "header-$category") {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val title = if (category == PodDeckCategory.MAIN)
                                    "Main $count + ${build.basics.total} basics = ${count + build.basics.total}"
                                else "${category.podBuildLabel()} $count"
                                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                if (category == PodDeckCategory.MAIN && canSubmit && editable) {
                                    Button(onClick = { confirmSubmit = true }, enabled = submitEnabled) { Text("Submit") }
                                }
                            }
                            if (category == PodDeckCategory.MAIN && canSubmit && editable && issues.isNotEmpty())
                                Text(podBuildIssueText(issues.first(), sideboardLimit), color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                            if (category == PodDeckCategory.SIDE)
                                Text("Sideboard limit: ${sideboardLimit?.toString() ?: "unlimited"}",
                                    color = if (sideboardLimit != null && count > sideboardLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (category == PodDeckCategory.MAIN) item("basics") { PodBuildBasics(build.basics, editable, onBasics) }
                item("labels-$category") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        PodDeckColumns.buckets(columns).forEach { bucket ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(bucket.label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
                val rows = buckets.maxOf { it.size }
                if (rows == 0) item("empty-$category") {
                    Text("No cards", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                items(rows, key = { "$category-row-$it" }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        buckets.forEach { bucket ->
                            Box(Modifier.weight(1f)) {
                                bucket.getOrNull(row)?.let { tile ->
                                    key(tile.digest, tile.copyIds.first()) {
                                        PodBuildCard(tile, category, editable, move) {
                                            tile.definition?.takeIf { it.faces.isNotEmpty() }?.let {
                                                inspector.open(InspectedCard.retained(tile.digest, it))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
        text = { Text("Submit your deck? It is locked afterwards.") },
        confirmButton = { TextButton(onClick = { confirmSubmit = false; onSubmit() }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Cancel") } },
    )
    LaunchedEffect(submitEnabled) { if (!submitEnabled) confirmSubmit = false }
    DeckCardInspection(inspector)
}

@Composable
private fun PodBuildBasics(basics: PodDeckBasics, editable: Boolean, onBasics: (PodDeckBasics) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text("Basics", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth()) {
            listOf("Plains", "Island", "Swamp", "Mountain", "Forest").forEachIndexed { index, name ->
                val count = basics.podBuildValues()[index]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, fontSize = 11.sp, maxLines = 1)
                    Text(count.toString(), style = MaterialTheme.typography.labelLarge)
                    if (editable) Row {
                        TextButton(onClick = { onBasics(basics.podBuildAdjust(index, -1)) }, enabled = count > 0,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Remove $name" },
                            contentPadding = PaddingValues(0.dp)) { Text("−") }
                        TextButton(onClick = { onBasics(basics.podBuildAdjust(index, 1)) }, enabled = count < Int.MAX_VALUE,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Add $name" },
                            contentPadding = PaddingValues(0.dp)) { Text("+") }
                    }
                }
            }
        }
    }
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
                ?.copy(size = if (width <= 146) "small" else if (width <= 488) "normal" else "large")
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
            CardArtwork(reference, name, width, height, Modifier.fillMaxSize())
            if (tile.copyIds.size > 1) Surface(Modifier.align(Alignment.TopEnd), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("×${tile.copyIds.size}", Modifier.padding(2.dp), fontSize = 11.sp)
            }
            if (drag != 0f) Surface(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("→ ${(if (drag > 0) right else left).podBuildLabel()}", Modifier.padding(2.dp), fontSize = 11.sp)
            }
        }
        DropdownMenu(expanded = menu && editable, onDismissRequest = { menu = false }) {
            listOf(right, left).forEach { destination ->
                DropdownMenuItem(text = { Text("→ ${destination.podBuildLabel()}") }, onClick = {
                    menu = false
                    latestMove(tile.copyIds.first(), destination)
                })
            }
        }
    }
}
