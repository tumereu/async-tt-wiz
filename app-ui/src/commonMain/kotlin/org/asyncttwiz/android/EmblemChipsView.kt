package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.storage.ArtworkReference
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.ioDispatcher
import org.asyncttwiz.appui.concurrentMapOf
import org.asyncttwiz.appui.getOrPutConcurrently

/**
 * TRN-053 (owner gallery review 2026-11-06): each player's emblems as small round chips by their life square
 * ([TableItem.emblemChips]): the emblem's art, or an emblem icon when the card records have none; more emblems than chips
 * and the last chip reads "+N" ([EmblemChips.shown]). A tap does nothing; holding any of them inspects all that player's
 * emblems with their full text ([EmblemInspect]) until the finger lifts.
 */
@Composable
fun EmblemChipsOverlay(scene: TableScene, viewer: String) {
    var holding by remember { mutableStateOf<String?>(null) }
    val lives = scene.items.filter { it.kind == TableItemKind.LIFE && it.emblemChips.isNotEmpty() && it.visible.isNotEmpty() && it.owner != null }
    for (life in lives) key(life.id) {
        val owner = life.owner.orEmpty()
        val texts = life.emblemTexts
        val shown = EmblemChips.shown(texts.size, life.emblemChips.size)
        val more = texts.size - shown
        for ((index, chip) in life.emblemChips.withIndex()) {
            val plus = more > 0 && index == shown
            EmblemChip(if (plus) null else texts.getOrNull(index), if (plus) "+$more" else null,
                if (plus) "$more more emblems" else "Emblem: ${texts.getOrNull(index).orEmpty()}",
                Modifier.offset { IntOffset(chip.x.dp.roundToPx(), chip.y.dp.roundToPx()) }.size(chip.width.dp, chip.height.dp),
                held = { down -> holding = if (down) owner else holding?.takeIf { it != owner } })
        }
    }
    val holder = holding ?: return
    val texts = lives.firstOrNull { it.owner == holder }?.emblemTexts ?: return
    EmblemInspect(texts, holder == viewer)
}

/** One round emblem chip: [text]'s art or the emblem icon, or [plus] ("+2") for the emblems beyond the first ones. Holding
 * it calls [held] with true, its release with false. */
@Composable
private fun EmblemChip(text: String?, plus: String?, description: String, modifier: Modifier, held: (Boolean) -> Unit) {
    val art = text?.let { rememberEmblemArt(it) }
    Box(modifier.testTag("emblem-chip").semantics { contentDescription = description }.clip(CircleShape).background(if (plus != null) Color.White else EmblemArtBackground)
        .border(1.5.dp, AutomatumViolet, CircleShape)
        .pointerInput(Unit) {
            detectTapGestures(onPress = { tryAwaitRelease(); held(false) }, onLongPress = { held(true) })
        }, contentAlignment = Alignment.Center) {
        when {
            plus != null -> Text(plus, color = AutomatumViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp,
                maxLines = 1, softWrap = false)
            art != null -> CardArtwork(art, "", 64, 64, Modifier.fillMaxSize(), crop = true,
                placeholder = { EmblemIcon(Modifier.size(16.dp)) })
            else -> EmblemIcon(Modifier.size(16.dp))
        }
    }
}

/** The emblem icon of an emblem without art (a rosette). */
@Composable
fun EmblemIcon(modifier: Modifier, tint: Color = Color.White) {
    Icon(Icons.Outlined.WorkspacePremium, null, modifier, tint = tint)
}

/** An emblem chip's fill under its art (and behind the icon). */
private val EmblemArtBackground = Color(0xff6c4fbf)

/**
 * TRN-053: Inspect of a player's emblems ([texts], oldest first; [own] the viewer's): one tile each, as an ability on the
 * Stack shows (STK-050), with the emblem's art (or icon) and its full text, over the dimmed screen; closed by the
 * release.
 */
@Composable
fun EmblemInspect(texts: List<String>, own: Boolean) {
    val arts = texts.map { rememberEmblemArt(it) }
    InspectPopup(texts.mapIndexed { n, text -> InspectGroup("emblem/$n", listOf(InspectTile.Emblem("emblem/$n", text, arts[n], own))) },
        rememberScrollState())
}

/** The picture of the emblem with rules [text] ([AndroidEmblemArt]), null while it is looked up or when there is none. */
@Composable
fun rememberEmblemArt(text: String): ArtworkReference? {
    val services = AppServices.current
    val art by produceState(AndroidEmblemArt.known(text), text) {
        value = withContext(ioDispatcher) { AndroidEmblemArt.find(services, text) }
    }
    return art?.let { ArtworkReference(it, 0, "art_crop") }
}

/**
 * TRN-053: an emblem's picture from the installed card records ([org.asyncttwiz.storage.CatalogRepository.emblemArt]),
 * looked up once per card records generation and text; none without card records or when reading them fails.
 */
object AndroidEmblemArt {
    private val found = concurrentMapOf<Pair<String, String>, String>()
    @Volatile private var generation: String? = null

    /** What is already known of [text] in the last generation looked at (no reading). */
    fun known(text: String): String? = generation?.let { found[it to text] }?.ifEmpty { null }

    fun find(services: AppServices, text: String): String? = try {
        val catalog = services.catalogRepository()
        val current = catalog.tokenGeneration()
        generation = current
        if (current == null) null else found.getOrPutConcurrently(current to text) { catalog.emblemArt(text).orEmpty() }.ifEmpty { null }
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Emblem picture lookup failed: ${problem.message?.take(200)}")
        null
    }
}
