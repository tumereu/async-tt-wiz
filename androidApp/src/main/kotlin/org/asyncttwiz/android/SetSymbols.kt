package org.asyncttwiz.android

import android.content.res.AssetManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Set symbols (DRF-176): the Keyrune icon font bundled with the app (assets/fonts: keyrune.ttf, SIL OFL 1.1, its licence
 * beside it) draws a set's symbol offline from the card catalog's MTGJSON `keyruneCode`; keyrune-codes.txt says which
 * character of the font each code is. A code the font has no symbol for shows the set's code as text instead.
 */
internal object Keyrune {
    private const val FONT = "fonts/keyrune.ttf"
    private const val CODES = "fonts/keyrune-codes.txt"
    @Volatile private var codes: Map<String, Char>? = null
    @Volatile private var family: FontFamily? = null

    /** The font's character for [keyrune] (a keyruneCode, any case), or null when it has no symbol for it. */
    fun glyph(assets: AssetManager, keyrune: String): Char? = codes(assets)[keyrune.lowercase()]

    private fun codes(assets: AssetManager): Map<String, Char> = codes ?: synchronized(this) {
        codes ?: runCatching {
            assets.open(CODES).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
                    val (code, hex) = line.trim().split(' ', limit = 2)
                    code to hex.trim().toInt(16).toChar()
                }
            }
        }.onFailure { AppLog.warn("sets", "Reading the set symbols failed", it) }.getOrDefault(emptyMap()).also { codes = it }
    }

    fun family(assets: AssetManager): FontFamily = family ?: synchronized(this) {
        family ?: FontFamily(Font(FONT, assets)).also { family = it }
    }
}

/**
 * A set's symbol (DRF-176) at [size], or its [code] as small text when the font has no symbol for its [keyrune] (or the
 * card records give none). Announced as "<code> symbol" either way.
 */
@Composable
internal fun SetSymbol(keyrune: String, code: String, modifier: Modifier = Modifier, size: TextUnit = 20.sp) {
    val assets = LocalContext.current.assets
    val glyph = remember(keyrune) { keyrune.takeIf { it.isNotEmpty() }?.let { Keyrune.glyph(assets, it) } }
    Box(modifier.semantics { contentDescription = "$code symbol" }, contentAlignment = Alignment.Center) {
        if (glyph != null) Text(glyph.toString(), Modifier.testTag("set-symbol-$code"), fontFamily = remember { Keyrune.family(assets) },
            fontSize = size, lineHeight = size, color = LocalContentColor.current, textAlign = TextAlign.Center)
        else Text(code, Modifier.testTag("set-code-$code"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
