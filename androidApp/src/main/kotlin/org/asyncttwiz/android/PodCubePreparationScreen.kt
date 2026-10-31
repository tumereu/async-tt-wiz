package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/**
 * Prepare cube (DRF-141, DRF-142): the deck preparation screen (LOB-047–LOB-051, [PreparationLayout]) for a pod's cube,
 * with Main and Maybe as its sections, the cube's column defaults and no basic land steppers, titled
 * "Prepare cube · <cube name>" with Done at the top right. Only this pod's cube changes.
 */
@Composable
internal fun PodCubePreparationScreen(preparation: PodCubePreparation) {
    val state = preparation.state
    BackHandler { if (preparation.inspection.state != null) preparation.inspection.close() else preparation.close() }
    val sections = state.cube?.let { cube ->
        SavedDeckSection.CUBE.map { section ->
            val opposite = if (section == SavedDeckSection.MAINBOARD) SavedDeckSection.MAYBEBOARD else SavedDeckSection.MAINBOARD
            val cards = cube.entries.filter { it.section == section }
            PreparationSection(section, opposite, cards.sumOf { it.quantity.toLong() },
                preparationGroups(cards.map { it.definition to it.quantity.toLong() }, state.definitions))
        }
    }
    PreparationLayout(state.cube?.cubeName?.let { "Prepare cube · $it" } ?: "Prepare cube", "prepare-cube",
        DeckViewScreen.CUBE_PREPARATION, state.connected, state.feedback, sections, object : PreparationMoves {
            override fun capture(digest: String, source: SavedDeckSection): () -> Boolean = { preparation.move(digest, source, false) }
            override fun move(digest: String, source: SavedDeckSection, all: Boolean) = preparation.move(digest, source, all)
            override fun inspect(digest: String) = preparation.inspect(digest)
        }, undo = if (state.undo.isEmpty()) null else preparation::undo, undoEnabled = state.connected,
        inspection = preparation.inspection,
        actions = {
            ScreenOutlinedButton(preparation::close, Modifier.testTag("prepare-cube-done")) {
                EntryIcon(null, "ready"); Spacer(Modifier.width(6.dp)); Text("Done")
            }
        },
        load = PreparationLoad(state.cube != null, state.error != null && state.cube == null,
            { state.pod?.let(preparation::open) }, state.loading),
        overlay = { if (state.cube != null) FloatingTransientError(state.error, preparation::dismissError, Modifier.align(Alignment.BottomCenter)) })
}
