package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal fun appVersionText(versionName: String = BuildConfig.VERSION_NAME, versionCode: Int = BuildConfig.VERSION_CODE,
    buildType: String = BuildConfig.BUILD_TYPE, commit: String = BuildConfig.GIT_COMMIT): String =
    "Version $versionName ($versionCode) $buildType · commit $commit"

@Composable
internal fun SettingsContent(catalog: CatalogViewModel?, accountId: String = "", logout: () -> Unit) {
    val preferences = rememberHistoryPlaybackPreferences(accountId)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PlaybackSettings(preferences, accountId)
        CardSettings()
        TextButton(onClick = logout, modifier = Modifier.heightIn(min = 48.dp).testTag("logout"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            EntryIcon(null, "logout"); Spacer(Modifier.width(8.dp)); Text("Log out")
        }
        SelectionContainer { Text(appVersionText(), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("app-version")) }
        // Catalog growth cannot displace preferences, account actions or version information.
        if (catalog != null) CatalogControls(catalog)
    }
    if (catalog != null) FloatingTransientError(catalog.state.status?.takeIf { catalog.state.error },
        catalog::dismissError, Modifier.align(Alignment.BottomCenter))
    }
}
