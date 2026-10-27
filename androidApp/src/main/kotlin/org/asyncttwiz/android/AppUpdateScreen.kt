package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/** The entry point replaces account/game UI with this screen while blocked. */
@Composable
fun AppUpdateScreen(updates: AppUpdateViewModel, install: (File) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(updates.error) {
        updates.error?.let { snackbar.showSnackbar(it); updates.dismissError() }
    }
    LaunchedEffect(updates.installation) {
        updates.installation?.let { updates.consumeInstallation(); install(it) }
    }
    when {
        updates.required -> {
            BackHandler { }
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Update required", style = MaterialTheme.typography.headlineMedium)
                        Text(updates.release?.versionName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Button(onClick = updates::update, enabled = !updates.downloading) { Text("Update") }
                        Box(Modifier.width(240.dp).height(24.dp), contentAlignment = Alignment.Center) {
                            if (updates.downloading) LinearProgressIndicator(progress = { updates.progress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        updates.initialCheck -> Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        else -> Unit
    }
}
