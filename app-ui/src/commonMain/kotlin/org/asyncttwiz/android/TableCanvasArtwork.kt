package org.asyncttwiz.android

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.coilImageBitmap

@Composable
fun tableArtwork(scene: TableScene): Map<String, ImageBitmap> {
    val images = mutableMapOf<String, ImageBitmap>()
    val context = LocalPlatformContext.current
    val extents = mutableMapOf<org.asyncttwiz.storage.ArtworkReference, Float>()
    for (item in scene.items) if (item.visible.isNotEmpty() && !item.covered) item.artwork?.let { reference ->
         extents[reference] = maxOf(extents[reference] ?: 0f, item.bounds.width, item.bounds.height)
     }
    for ((reference, extent) in extents) key(reference) {
        val width = tableArtworkPixels(with(LocalDensity.current) { extent.dp.roundToPx() })
        val height = width
        // A new size (a zoom, a raised card) loads beside the picture already shown, which stays until it has
        var image by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(width, height) {
            val request = CardArtworkRequest(reference, width, height)
            val result = PublicArtwork.get(context).loader.execute(coil3.request.ImageRequest.Builder(context)
                .data(request).size(width, height).build()) as? coil3.request.SuccessResult
            result?.image?.let(::coilImageBitmap)?.let { image = it }
        }
        image?.let { images[reference.key] = it }
    }
    return images
}

/**
 * The size a table picture is asked for at [pixels] drawn: rounded up to a step of [TABLE_ARTWORK_STEP] pixels, so a zoom
 * or a card growing a little asks again only when a step is passed, not on every change (web frame rate, 2026-11-14: each
 * step of a wheel zoom decoded every picture on the table anew).
 */
fun tableArtworkPixels(pixels: Int): Int =
    ((pixels.coerceAtLeast(1) + TABLE_ARTWORK_STEP - 1) / TABLE_ARTWORK_STEP * TABLE_ARTWORK_STEP).coerceIn(1, 4096)

const val TABLE_ARTWORK_STEP = 32

/** Uniform scale and centered clipping, never independent width/height stretching. */
fun DrawScope.drawTableArtwork(image: ImageBitmap, bounds: TableBounds) {
    val left = bounds.x * density; val top = bounds.y * density
    val width = bounds.width * density; val height = bounds.height * density
    val factor = maxOf(width / image.width, height / image.height)
    clipRect(left, top, left + width, top + height) {
        translate(left + (width - image.width * factor) / 2, top + (height - image.height * factor) / 2) {
            scale(factor, factor, Offset.Zero) { drawImage(image) }
        }
    }
}
