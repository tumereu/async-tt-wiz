package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.DraftableSet

/** What the setup form shows of a set pod's pack grid (DRF-173): each pack's set read row by row (normalized), the card
 * records' sets for names and symbols, and [choose] (null: read-only) to pick a set for some of its packs (DRF-174). */
internal class PodPackGridUi(val packs: List<String>, val set: (String) -> DraftableSet?, val choose: ((PodPackTarget) -> Unit)?)

/**
 * DRF-173: by default one set for the whole pod, with a "Mix sets" button that opens the pod's pack grid; a grid that mixes
 * sets is always shown (read-only for members). Modes without a grid (Rotisserie) show nothing.
 */
@Composable
internal fun PodPackGridSection(settings: DraftSettings, grid: PodPackGridUi) {
    val rows = PodPackGrid.rows(settings) ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    val mixed = PodPackGrid.mixed(grid.packs)
    if (!open && !mixed) {
        if (grid.choose != null) ScreenOutlinedButton({ open = true; AppLog.info("pods", "Pack grid opened: ${grid.packs}") },
            Modifier.testTag("mix-sets")) {
            EntryIcon(null, "pack-grid"); Spacer(Modifier.width(8.dp)); Text("Mix sets")
        }
        return
    }
    PodPackGridView(settings, rows, PodPackGrid.cells(grid.packs, settings), grid)
}

/**
 * The pack grid (DRF-173, DRF-174): a column label row ("Pack 1", "Pack 2"…), then one row per round under its label
 * ("Round 1"…; "All packs" for a grid of one row), each cell a set chip with the set's symbol (DRF-172) over its code.
 * Tapping a cell, a row label or a column label chooses a set for those packs. Scrolls sideways when wide.
 */
@Composable
private fun PodPackGridView(settings: DraftSettings, rows: List<Int>, cells: List<String>, grid: PodPackGridUi) {
    val labelWidth = 64.dp
    val cellWidth = 48.dp
    val columns = rows.max()
    Column(Modifier.fillMaxWidth().testTag("pack-grid").horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(labelWidth))
            repeat(columns) { column ->
                PodPackLabel(podPackColumnLabel(column), Modifier.width(cellWidth).testTag("pack-column-$column"),
                    grid.choose?.let { choose -> { choose(PodPackTarget.Column(column)) } })
            }
        }
        var index = 0
        rows.forEachIndexed { row, length ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PodPackLabel(podPackRowLabel(settings, row), Modifier.width(labelWidth).testTag("pack-row-$row"),
                    grid.choose?.let { choose -> { choose(PodPackTarget.Row(row)) } })
                repeat(length) { column ->
                    val cell = index++
                    val code = cells[cell]
                    PodPackCell(code, grid.set(code), Modifier.width(cellWidth).testTag("pack-cell-$cell"),
                        grid.choose?.let { choose -> { choose(PodPackTarget.Cell(cell, row, column)) } },
                        "${PodPackTarget.Cell(cell, row, column).label(settings)}: ${grid.set(code)?.name ?: code}")
                }
            }
        }
    }
}

/** A row or column label; tapping it (when [onClick]) chooses a set for every pack it names. */
@Composable
private fun PodPackLabel(text: String, modifier: Modifier, onClick: (() -> Unit)?) {
    if (onClick == null) Text(text, modifier, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    else TextButton(onClick, modifier, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** A cell: its set's symbol over its code, or the code alone when the font has no symbol for it (DRF-172). */
@Composable
private fun PodPackCell(code: String, set: DraftableSet?, modifier: Modifier, onClick: (() -> Unit)?, description: String) {
    Surface(onClick = onClick ?: {}, enabled = onClick != null, modifier = modifier.height(52.dp).semantics { contentDescription = description },
        shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, ScreenBorder), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            val keyrune = set?.keyrune.orEmpty()
            val glyph = keyrune.isNotEmpty() && Keyrune.glyph(androidx.compose.ui.platform.LocalContext.current.assets, keyrune) != null
            if (glyph) {
                SetSymbol(keyrune, code, size = 22.sp)
                Text(code, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            } else Text(code, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
