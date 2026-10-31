package org.asyncttwiz.android

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun tableArtwork(scene: TableScene): Map<String, ImageBitmap> {
    val images = mutableMapOf<String, ImageBitmap>()
    val context = LocalContext.current
    val extents = mutableMapOf<org.asyncttwiz.storage.ArtworkReference, Float>()
    for (item in scene.items) if (item.visible.isNotEmpty() && !item.covered) item.artwork?.let { reference ->
         extents[reference] = maxOf(extents[reference] ?: 0f, item.bounds.width, item.bounds.height)
     }
    for (label in scene.combatLabels) label.artwork?.let { extents[it] = maxOf(extents[it] ?: 0f, label.bounds.height) }
    for ((reference, extent) in extents) key(reference) {
        val width = with(LocalDensity.current) { extent.dp.roundToPx() }.coerceIn(1, 4096)
        val height = width
        val image by produceState<ImageBitmap?>(null, reference, width, height) {
            val request = CardArtworkRequest(reference, width, height)
            val result = PublicArtwork.get(context).loader.execute(coil3.request.ImageRequest.Builder(context)
                .data(request).size(width, height).build()) as? coil3.request.SuccessResult
            value = (result?.image as? coil3.BitmapImage)?.bitmap?.asImageBitmap()
        }
        image?.let { images[reference.key] = it }
    }
    return images
}

/** Uniform scale and centered clipping, never independent width/height stretching. */
internal fun DrawScope.drawTableArtwork(image: ImageBitmap, bounds: TableBounds) {
    val left = bounds.x * density; val top = bounds.y * density
    val width = bounds.width * density; val height = bounds.height * density
    val factor = maxOf(width / image.width, height / image.height)
    clipRect(left, top, left + width, top + height) {
        translate(left + (width - image.width * factor) / 2, top + (height - image.height * factor) / 2) {
            scale(factor, factor, Offset.Zero) { drawImage(image) }
        }
    }
}
