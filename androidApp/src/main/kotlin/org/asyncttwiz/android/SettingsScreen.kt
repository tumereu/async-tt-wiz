package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsContent(catalog: CatalogViewModel?, logout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (catalog != null) CatalogControls(catalog)
        TextButton(onClick = logout, modifier = Modifier.heightIn(min = 48.dp).testTag("logout"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            EntryIcon(null, "logout"); Spacer(Modifier.width(8.dp)); Text("Log out")
        }
    }
}
