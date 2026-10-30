package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.DeckCover
import org.asyncttwiz.storage.ArtworkReference

@Composable
internal fun DeckMetadataHeader(name: String, cover: DeckCover?, onName: ((String) -> Unit)? = null,
    chooseCover: (() -> Unit)? = null, enabled: Boolean = true,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp), trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(padding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).then(if (chooseCover != null) Modifier.clickable(enabled = enabled,
            onClickLabel = "Choose deck cover", onClick = chooseCover).semantics { contentDescription = "Choose deck cover" }
            else Modifier), contentAlignment = Alignment.Center) {
            DeckThumbnail(cover?.let { ArtworkReference(it.scryfallId, 0, it.definition, "small", it.imageUri) },
                cover?.name ?: "No deck cover", 32.dp, 44.dp)
        }
        if (onName == null) Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        else ScreenTextField(name, onName, Modifier.weight(1f).testTag("deck-name")
            .semantics { contentDescription = "Deck name" }, enabled = enabled, placeholder = "Deck name",
            keyboardOptions = imeOptions(ImeAction.Next))
        trailing?.invoke()
    }
}
