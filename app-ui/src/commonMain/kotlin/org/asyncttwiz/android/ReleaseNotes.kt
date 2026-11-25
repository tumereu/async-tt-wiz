package org.asyncttwiz.android

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
import org.asyncttwiz.appui.AppPlatform
import kotlin.concurrent.Volatile

/** What a release note is (LOB-087): a set Automatum newly supports, or a change of some importance. */
sealed interface ReleaseNoteKind {
    /** The note file's prefix ("set", "1", "2", "3"). */
    val prefix: String

    /** LOB-087 (owner 2026-11-10): a set Automatum newly supports, listed first under its own heading. */
    data object Set : ReleaseNoteKind { override val prefix = "set" }

    /** LOB-087 (owner 2026-11-25): a change, [importance] 1 (the most important) to 3; What's new shows no levels, only the order. */
    data class Change(val importance: Int) : ReleaseNoteKind {
        init { require(importance in 1..3) { "Importance $importance isn't 1, 2 or 3" } }
        override val prefix get() = importance.toString()
    }

    companion object {
        fun of(prefix: String): ReleaseNoteKind = if (prefix == Set.prefix) Set
            else Change(requireNotNull(prefix.toIntOrNull()?.takeIf { it in 1..3 }) { "Unknown release note kind $prefix" })
    }
}

/** One packaged `release/notes/<id>.txt` line; [id] starts with its `YYYY-MM-DD` date. */
data class ReleaseNote(val id: String, val kind: ReleaseNoteKind, val text: String)

object ReleaseNotes {
    const val ASSET = "release-notes.json"
    /** Phones updating from before release notes existed already know the initial release. */
    const val INITIAL_RELEASE = "2026-10-27-initial-release"

    fun parse(json: String): List<ReleaseNote> = Json.parseToJsonElement(json).jsonArray.map { element ->
        val note = element.jsonObject
        ReleaseNote(note.getValue("id").jsonPrimitive.content,
            ReleaseNoteKind.of(note.getValue("kind").jsonPrimitive.content), note.getValue("text").jsonPrimitive.content)
    }

    const val NEW_SETS = "New sets"
    const val CHANGES = "Changes"

    /** LOB-087 (owner 2026-11-25): "New sets" (newest date first, then by id), then "Changes", the most important first, then
     * newest date first, then by id; an empty group is left out. */
    fun grouped(notes: List<ReleaseNote>): List<Pair<String, List<ReleaseNote>>> {
        val newest = compareByDescending<ReleaseNote> { it.id.take(10) }.thenBy { it.id }
        val sets = notes.filter { it.kind == ReleaseNoteKind.Set }.sortedWith(newest)
        val changes = notes.filter { it.kind is ReleaseNoteKind.Change }
            .sortedWith(compareBy<ReleaseNote> { (it.kind as ReleaseNoteKind.Change).importance }.then(newest))
        return listOfNotNull(sets.takeIf { it.isNotEmpty() }?.let { NEW_SETS to it }, changes.takeIf { it.isNotEmpty() }?.let { CHANGES to it })
    }

    /** The seen record to keep: the stored one, else everything on a fresh install, else only the initial release. */
    fun seenRecord(stored: Set<String>?, all: List<ReleaseNote>, freshInstall: Boolean): Set<String> =
        stored ?: if (freshInstall) all.mapTo(mutableSetOf()) { it.id } else setOf(INITIAL_RELEASE)

    fun unseen(all: List<ReleaseNote>, seen: Set<String>): List<ReleaseNote> = all.filter { it.id !in seen }

    /** Shown from the first frame, whatever the sign-in or catalog state; never over an open game or pod,
     *  nor while a notification is about to open one. */
    fun showDialog(unseen: List<ReleaseNote>, pendingNotificationRoute: Boolean, screen: ActivityScreen): Boolean =
        unseen.isNotEmpty() && !pendingNotificationRoute && screen.game == null && screen.pod == null

    @Volatile private var loaded: List<ReleaseNote>? = null
    fun load(platform: AppPlatform): List<ReleaseNote> = loaded ?: runCatching {
        parse(platform.readAsset(ASSET).decodeToString())
    }.getOrDefault(emptyList()).also { loaded = it }
}

/** Seen note ids per installation (not per account). */
class ReleaseNotePreferences(private val platform: AppPlatform) {
    private val preferences = platform.preferences("release-notes")

    private fun freshInstall(): Boolean = platform.freshInstall()

    /** Settles the record on first run so a later update cannot be mistaken for a fresh install. */
    fun unseen(all: List<ReleaseNote> = ReleaseNotes.load(platform)): List<ReleaseNote> {
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

/** LOB-265 (owner playtest 2026-11-21): What's new on the desktop layout is twice the phone's dialog width. */
val WhatsNewDesktopWidth = 760.dp

@Composable
fun WhatsNewDialog(notes: List<ReleaseNote>, dismiss: () -> Unit) {
    ScreenDialog(dismiss, title = "What's new", maxWidth = if (desktopLayout()) WhatsNewDesktopWidth else 380.dp,
        actions = { DialogMainButton("OK", dismiss, Modifier.testTag("whats-new-ok")) }) {
        Column(Modifier.testTag("whats-new-notes"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((title, group) in ReleaseNotes.grouped(notes)) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                for (note in group) Row {
                    Text("•", Modifier.width(16.dp))
                    Text(note.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (notes.isEmpty()) Text("No release notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
