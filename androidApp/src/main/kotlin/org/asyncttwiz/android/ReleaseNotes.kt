package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal enum class ReleaseNoteKind(val prefix: String, val title: String) {
    ADD("add", "New"), MOD("mod", "Changed"), FIX("fix", "Fixed")
}

/** One packaged `release/notes/<id>.txt` line; [id] starts with its `YYYY-MM-DD` date. */
internal data class ReleaseNote(val id: String, val kind: ReleaseNoteKind, val text: String)

internal object ReleaseNotes {
    const val ASSET = "release-notes.json"
    /** Phones updating from before release notes existed already know the initial release. */
    const val INITIAL_RELEASE = "2026-10-27-initial-release"

    fun parse(json: String): List<ReleaseNote> = Json.parseToJsonElement(json).jsonArray.map { element ->
        val note = element.jsonObject
        val kind = note.getValue("kind").jsonPrimitive.content
        ReleaseNote(note.getValue("id").jsonPrimitive.content,
            ReleaseNoteKind.entries.first { it.prefix == kind }, note.getValue("text").jsonPrimitive.content)
    }

    /** New, Changed, Fixed (empty groups omitted); newest date first, then by id. */
    fun grouped(notes: List<ReleaseNote>): List<Pair<ReleaseNoteKind, List<ReleaseNote>>> =
        ReleaseNoteKind.entries.mapNotNull { kind ->
            notes.filter { it.kind == kind }
                .sortedWith(compareByDescending<ReleaseNote> { it.id.take(10) }.thenBy { it.id })
                .takeIf { it.isNotEmpty() }?.let { kind to it }
        }

    /** The seen record to keep: the stored one, else everything on a fresh install, else only the initial release. */
    fun seenRecord(stored: Set<String>?, all: List<ReleaseNote>, freshInstall: Boolean): Set<String> =
        stored ?: if (freshInstall) all.mapTo(mutableSetOf()) { it.id } else setOf(INITIAL_RELEASE)

    fun unseen(all: List<ReleaseNote>, seen: Set<String>): List<ReleaseNote> = all.filter { it.id !in seen }

    @Volatile private var loaded: List<ReleaseNote>? = null
    fun load(context: Context): List<ReleaseNote> = loaded ?: runCatching {
        parse(context.assets.open(ASSET).use { it.readBytes().decodeToString() })
    }.getOrDefault(emptyList()).also { loaded = it }
}

/** Seen note ids per installation (not per account). */
internal class ReleaseNotePreferences(private val context: Context) {
    private val preferences = context.getSharedPreferences("release-notes", Context.MODE_PRIVATE)

    private fun freshInstall(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    /** Settles the record on first run so a later update cannot be mistaken for a fresh install. */
    fun unseen(all: List<ReleaseNote> = ReleaseNotes.load(context)): List<ReleaseNote> {
        val stored = preferences.getStringSet(SEEN, null)?.toSet()
        val seen = ReleaseNotes.seenRecord(stored, all, if (stored == null) freshInstall() else false)
        if (stored == null) preferences.edit().putStringSet(SEEN, seen).apply()
        return ReleaseNotes.unseen(all, seen)
    }

    fun markSeen(notes: List<ReleaseNote>) {
        val seen = preferences.getStringSet(SEEN, null).orEmpty() + notes.map { it.id }
        preferences.edit().putStringSet(SEEN, seen).apply()
    }

    private companion object { const val SEEN = "seen" }
}

@Composable
internal fun WhatsNewDialog(notes: List<ReleaseNote>, dismiss: () -> Unit) {
    ScreenDialog(dismiss, scroll = false) {
        Text("What's new", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("whats-new-notes"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((kind, group) in ReleaseNotes.grouped(notes)) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kind.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                for (note in group) Row {
                    Text("•", Modifier.width(16.dp))
                    Text(note.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (notes.isEmpty()) Text("No release notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(dismiss, Modifier.heightIn(min = 48.dp).testTag("whats-new-ok")) { Text("OK") }
        }
    }
}
