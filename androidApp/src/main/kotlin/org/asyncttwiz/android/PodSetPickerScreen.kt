package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

@Composable
internal fun PodSourcePickerDialog(controller: PodController, manageCubes: () -> Unit) {
    val picker = controller.setPicker
    var tab by remember { mutableIntStateOf(0) }
    var cubeQuery by remember { mutableStateOf("") }
    ScreenDialog(picker::dismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Source", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            ScreenIconButton("Manage cubes", "decks", { picker.dismiss(); manageCubes() })
            ScreenIconButton("Close sources", "close", picker::dismiss)
        }
        controller.state.sourceHint?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TabRow(tab) {
            listOf("Cubes", "Sets").forEachIndexed { index, title -> Tab(tab == index, { tab = index }, text = { Text(title) }) }
        }
        if (tab == 0) {
            OutlinedTextField(cubeQuery, { cubeQuery = it }, Modifier.fillMaxWidth(), label = { Text("Search cubes") }, singleLine = true,
                keyboardOptions = imeOptions(ImeAction.Search), keyboardActions = hideKeyboardActions())
            Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                val cubes = controller.state.cubes.filter { it.name.contains(cubeQuery.trim(), true) }
                if (!controller.state.cubesLoaded && controller.state.loading && cubes.isEmpty()) CircularProgressIndicator()
                else if (cubes.isEmpty()) Text("No cubes found")
                else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(cubes, key = { it.id }) { cube ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ScreenOutlinedButton({ controller.chooseCube(cube.id) }, Modifier.weight(1f)) { Text(cube.name) }
                            // DRF-161
                            AutomatumSupportIcon(cube.automatum)
                        }
                    }
                }
            }
        } else if (!cardRecordsReady()) CardRecordsPlaceholder(Modifier.fillMaxWidth().height(320.dp)) // The sets are card records (3.6)
        else PodSetPickerContent(picker.state, picker::query, picker::choose, picker::retry)
    }
}

/** DRF-174: the set list for some packs of the pack grid, titled by them ("Set for Round 2"); only draftable sets can be
 * chosen, as in the Source dialog. */
@Composable
internal fun PodPackSetDialog(controller: PodController, target: PodPackTarget, settings: DraftSettings) {
    val picker = controller.setPicker
    ScreenDialog(picker::dismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Set for ${target.label(settings)}", Modifier.weight(1f).testTag("pack-set-title"), style = MaterialTheme.typography.titleMedium)
            ScreenIconButton("Close sets", "close", picker::dismiss)
        }
        if (!cardRecordsReady()) CardRecordsPlaceholder(Modifier.fillMaxWidth().height(320.dp))
        else PodSetPickerContent(picker.state, picker::query, picker::choose, picker::retry)
    }
}

@Composable
internal fun PodSetPickerContent(state: PodSetPickerState, query: (String) -> Unit, choose: (String) -> Unit,
    retry: () -> Unit) {
    OutlinedTextField(state.query, query, Modifier.fillMaxWidth(), label = { Text("Search name, code or release date") }, singleLine = true,
        keyboardOptions = imeOptions(ImeAction.Search), keyboardActions = hideKeyboardActions())
    val rows = state.filtered
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
    if (!state.loaded && state.busy && rows.isEmpty()) CircularProgressIndicator()
    else if (rows.isEmpty()) Text("No sets found")
    else {
    LazyColumn(Modifier.fillMaxSize(), rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(rows, key = { it.code }) { set ->
            // Section 3.9: a set Automatum can't play every card of is greyed and can't be chosen; holding its warning
            // sign names those cards (DRF-161)
            Row(Modifier.fillMaxWidth().testTag("set-${set.code}"), verticalAlignment = Alignment.CenterVertically) {
                ScreenOutlinedButton({ choose(set.code) }, Modifier.weight(1f), enabled = set.automatum) {
                    // DRF-172: the set's symbol, or its code when the font has none
                    SetSymbol(set.keyrune, set.code, Modifier.width(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(set.name, Modifier.weight(1f))
                }
                AutomatumSupportIcon(set.support())
            }
        }
    }
    }
    if (state.error != null) ScreenIconButton("Retry source", "refresh", retry, Modifier.align(Alignment.BottomEnd), enabled = !state.busy)
    FloatingTransientError(state.error, {}, Modifier.align(Alignment.BottomCenter))
    }
}
