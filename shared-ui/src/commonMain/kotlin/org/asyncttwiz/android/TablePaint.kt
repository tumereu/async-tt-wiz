package org.asyncttwiz.android

import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Web spike: the table's text paint, which the Android drawing took from android.graphics.Paint (drawn on the native
 * canvas). Android keeps exactly that Paint (the same pixels); the browser draws with Skia's Font and Paint.
 */
expect class TablePaint() {
    /** ARGB. */
    var color: Int
    var textSize: Float
    var isFakeBoldText: Boolean
    fun measureText(text: String): Float
    val fontMetrics: TableFontMetrics
}

/** Font metrics in pixels, as android.graphics.Paint.FontMetrics names them (ascent and top negative). */
class TableFontMetrics(val ascent: Float, val descent: Float, val top: Float, val bottom: Float)

/** [text] at the baseline ([x], [y]) in pixels, on the platform canvas. */
expect fun DrawScope.nativeText(text: String, x: Float, y: Float, paint: TablePaint)

/** The android.graphics.Color constants the table drawing uses. */
object TableInk {
    const val DKGRAY: Int = 0xFF444444.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
}

/** An access-ordered map that keeps the [max] most recently used entries (java.util.LinkedHashMap's access order and
 * removeEldestEntry, which common Kotlin lacks). */
class BoundedCache<K, V>(private val max: Int) {
    private val map = LinkedHashMap<K, V>()
    fun getOrPut(key: K, make: () -> V): V {
        val found = map.remove(key)
        if (found != null) { map[key] = found; return found }
        val made = make()
        map[key] = made
        if (map.size > max) map.remove(map.keys.first())
        return made
    }
}
