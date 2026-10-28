package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Overlay in the caller's Box, above table controls; never consumes table layout space. */
@Composable
internal fun FloatingTransientError(message: String?, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    val onDismiss by rememberUpdatedState(dismiss)
    LaunchedEffect(message) { if (message != null) { delay(7_000); onDismiss() } }
    if (message != null) Snackbar(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 560.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = MaterialTheme.shapes.medium,
        dismissAction = { IconButton(onClick = dismiss) { EntryIcon("Dismiss message", "close") } },
    ) { Text(message, style = MaterialTheme.typography.bodyMedium) }
}

/** Calm, non-dismissable status while automatic refreshes keep failing; it disappears on the next success.
 * Drawn without a Surface so taps reach the controls underneath (e.g. a draft's Confirm). */
@Composable
internal fun ServerUnreachableNote(visible: Boolean, modifier: Modifier = Modifier) {
    if (visible) Row(modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        .shadow(2.dp, MaterialTheme.shapes.extraLarge)
        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraLarge)
        .padding(horizontal = 14.dp, vertical = 8.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text("Can't reach the server · retrying", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
