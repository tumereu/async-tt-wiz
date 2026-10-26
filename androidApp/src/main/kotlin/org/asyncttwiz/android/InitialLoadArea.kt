package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Unknown-size content is withheld until its first accepted read. Refreshes keep it mounted. */
@Composable
internal fun InitialLoadArea(loaded: Boolean, modifier: Modifier = Modifier.fillMaxSize(),
    failed: Boolean = false, retry: (() -> Unit)? = null, loading: Boolean = false, content: @Composable () -> Unit) {
    var hadFailure by remember { mutableStateOf(false) }
    LaunchedEffect(failed) { if (failed) hadFailure = true }
    Box(modifier) {
        if (loaded) content()
        else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(32.dp))
            Box(Modifier.height(48.dp)) {
                if ((failed || hadFailure) && retry != null) TextButton(onClick = retry, enabled = !loading) { Text("Retry") }
            }
        }
    }
}
