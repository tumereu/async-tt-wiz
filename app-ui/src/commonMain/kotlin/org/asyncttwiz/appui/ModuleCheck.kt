package org.asyncttwiz.appui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Proves the module compiles Compose (runtime, foundation, ui, Material3) for Android and Kotlin/Wasm until the app's UI
 * moves in (docs/specs/web-client.md §11 step 2); goes away then.
 */
@Composable
internal fun ModuleCheck(text: String) {
    MaterialTheme { Box { Text(text) } }
}
