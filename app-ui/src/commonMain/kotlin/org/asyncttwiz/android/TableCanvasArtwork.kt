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

/**
 * The table's pictures of [items] (the cards drawn, as laid out now), by their artwork's key: each at the size its largest
 * card is drawn, rounded up to a step ([tableArtworkPixels]). Only this recomposes as those sizes change (a zoom passing a
 * step, a card coming into view); the map it returns is read while drawing, so a picture loading redraws the table only (web
 * frame rate, 2026-11-15).
 */
@Composable
fun tableArtwork(items: State<List<TableItem>>): Map<String, ImageBitmap> {
    val images = remember { TableArtworkImages() }
    val density = LocalDensity.current
    val sizes = remember(items, density) { derivedStateOf(structuralEqualityPolicy()) {
        val extents = LinkedHashMap<org.asyncttwiz.storage.ArtworkReference, Float>()
        for (item in items.value) if (item.visible.isNotEmpty() && !item.covered) item.artwork?.let { reference ->
            extents[reference] = maxOf(extents[reference] ?: 0f, item.bounds.width, item.bounds.height)
        }
        extents.mapValues { (_, extent) -> tableArtworkPixels(with(density) { extent.dp.roundToPx() }) }
    } }
    TableArtworkLoads(sizes, images)
    return images.map
}

/**
 * The table's loaded pictures by their artwork's key ([map], read while drawing). A holder of its own, so passing it on to
 * [TableArtworkLoads] never compares (reads) the map's pictures in the caller's composition: a picture loading then
 * recomposed the whole table.
 */
class TableArtworkImages { val map = mutableStateMapOf<String, ImageBitmap>() }

/** Loads each of [sizes]' pictures at its size into [images], each kept while its reference is drawn. */
@Composable
private fun TableArtworkLoads(sizes: State<Map<org.asyncttwiz.storage.ArtworkReference, Int>>, images: TableArtworkImages) {
    val context = LocalPlatformContext.current
    for ((reference, width) in sizes.value) key(reference) {
        val height = width
        // A new size (a zoom, a raised card) loads beside the picture already shown, which stays until it has
        LaunchedEffect(width, height) {
            val request = CardArtworkRequest(reference, width, height)
            val result = PublicArtwork.get(context).loader.execute(coil3.request.ImageRequest.Builder(context)
                .data(request).size(width, height).build()) as? coil3.request.SuccessResult
            result?.image?.let(::coilImageBitmap)?.let { images.map[reference.key] = it }
        }
        DisposableEffect(reference) { onDispose { images.map.remove(reference.key) } }
    }
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
