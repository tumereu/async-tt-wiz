package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                        ScreenOutlinedButton({ controller.chooseCube(cube.id) }, Modifier.fillMaxWidth()) { Text(cube.name) }
                    }
                }
            }
        } else PodSetPickerContent(picker.state, picker::query, picker::choose, picker::redownload, picker::retry)
    }
}

@Composable
internal fun PodSetPickerContent(state: PodSetPickerState, query: (String) -> Unit, choose: (String) -> Unit,
    redownload: (String) -> Unit, retry: () -> Unit) {
    OutlinedTextField(state.query, query, Modifier.fillMaxWidth(), label = { Text("Search name, code or release date") }, singleLine = true,
        keyboardOptions = imeOptions(ImeAction.Search), keyboardActions = hideKeyboardActions())
    val rows = state.filtered
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
    if (!state.loaded && state.busy && rows.isEmpty()) CircularProgressIndicator()
    else if (rows.isEmpty()) Text("No sets found")
    else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(rows, key = { it.set.code }) { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScreenOutlinedButton({ choose(row.set.code) }, Modifier.weight(1f)) {
                    Text(row.set.name)
                }
                if (row.cached) ScreenIconButton("Redownload ${row.set.code}", "refresh", { redownload(row.set.code) }, enabled = !state.busy)
            }
        }
    }
    if (state.error != null) ScreenIconButton("Retry source", "refresh", retry, Modifier.align(Alignment.BottomEnd), enabled = !state.busy)
    FloatingTransientError(state.error, {}, Modifier.align(Alignment.BottomCenter))
    }
}
