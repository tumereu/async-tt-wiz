@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package webspike.table

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import kotlin.math.pow

// ---- Browser helpers (no kotlinx-browser dependency)

private fun now(): Double = js("performance.now()")
private fun query(): String = js("location.search")
private fun publish(key: String, value: String): Unit = js("globalThis[key] = value")
private fun fetchBytes(url: String, done: (JsAny?) -> Unit): Unit =
    js("fetch(url).then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.arrayBuffer(); }).then(b => done(new Int8Array(b))).catch(e => { console.log('fetch failed', url, e); done(null); })")
private fun fetchText(url: String, done: (String?) -> Unit): Unit =
    js("fetch(url, { headers: { 'Accept': 'application/json' } }).then(r => r.text()).then(t => done(t)).catch(e => { console.log('fetch failed', url, e); done(null); })")
private fun length(array: JsAny): Int = js("array.length")
private fun byteAt(array: JsAny, i: Int): Byte = js("array[i]")
private fun imageUrls(json: String, size: String, max: Int): String =
    js("JSON.parse(json).data.filter(c => c.image_uris).slice(0, max).map(c => c.image_uris[size]).join('\\n')")
private fun log(text: String): Unit = js("console.log(text)")

private fun toBytes(array: JsAny): ByteArray {
    val n = length(array)
    return ByteArray(n) { byteAt(array, it) }
}

// ---- The table model

private const val CARD_W = 63f
private const val CARD_H = 88f
private const val COLS = 15
private const val CARDS = 150

private class Card(val index: Int, val x: Float, val y: Float, val tapped: Boolean, val image: Int)

private val cards = List(CARDS) { i ->
    val row = i / COLS; val col = i % COLS
    // Two players' halves with a gap in the middle, tapped cards scattered
    val y = row * (CARD_H + 14f) + if (row >= 5) 60f else 0f
    Card(i, col * (CARD_W + 16f), y, tapped = (i * 7) % 5 == 0, image = i)
}

private class Camera(var x: Float = -40f, var y: Float = -40f, var zoom: Float = 1f)

private class FrameStats {
    val deltas = ArrayDeque<Double>()
    var drawMs = ArrayDeque<Double>()
    var last = 0L
    fun add(dt: Double) { deltas.addLast(dt); if (deltas.size > 120) deltas.removeFirst() }
    fun addDraw(ms: Double) { drawMs.addLast(ms); if (drawMs.size > 120) drawMs.removeFirst() }
    fun text(): String {
        if (deltas.isEmpty()) return "measuring…"
        val avg = deltas.average()
        val sorted = deltas.sorted()
        val p95 = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
        val draw = if (drawMs.isEmpty()) 0.0 else drawMs.average()
        return "fps ${f1(1000 / avg)}  frame avg ${f1(avg)} ms  p95 ${f1(p95)} ms  worst ${f1(sorted.last())} ms  draw (record) ${f1(draw)} ms"
    }
}

private fun f1(v: Double): String { val r = kotlin.math.round(v * 10) / 10; return r.toString() }

fun main() {
    val size = if (query().contains("size=small")) "small" else "normal"
    ComposeViewport("table") { Table(size) }
}

@Composable
private fun Table(imageSize: String) {
    var images by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var font by remember { mutableStateOf<org.jetbrains.skia.Font?>(null) }
    var status by remember { mutableStateOf("loading card images…") }
    var tick by remember { mutableStateOf(0) }
    var hovered by remember { mutableStateOf(-1) }
    var rightClicked by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }
    var autoPan by remember { mutableStateOf(query().contains("auto")) }
    val camera = remember { Camera() }
    val frames = remember { FrameStats() }

    LaunchedEffect(Unit) {
        val t0 = now()
        // One batched lookup for image URLs (Scryfall search API), then the images from the CDN
        fetchText("https://api.scryfall.com/cards/search?q=set%3Am21+rarity%3Acommon&unique=art") { json ->
            if (json == null) { status = "Scryfall lookup failed"; return@fetchText }
            val urls = imageUrls(json, imageSize, 24).split('\n').filter { it.isNotEmpty() }
            val loaded = arrayOfNulls<ImageBitmap>(urls.size)
            var remaining = urls.size
            var bytes = 0
            urls.forEachIndexed { i, url ->
                fetchBytes(url) { array ->
                    if (array != null) {
                        val data = toBytes(array)
                        bytes += data.size
                        loaded[i] = org.jetbrains.skia.Image.makeFromEncoded(data).toComposeImageBitmap()
                    }
                    if (--remaining == 0) {
                        images = loaded.filterNotNull()
                        status = "${images.size} $imageSize images (${bytes / 1024} KB) in ${f1(now() - t0)} ms"
                        publish("imagesLoaded", status)
                    }
                }
            }
        }
        fetchBytes("https://cdn.jsdelivr.net/gh/googlefonts/roboto-2@main/src/hinted/Roboto-Regular.ttf") { array ->
            if (array != null) {
                val typeface = org.jetbrains.skia.FontMgr.default.makeFromData(org.jetbrains.skia.Data.makeFromBytes(toBytes(array)))
                font = org.jetbrains.skia.Font(typeface, 11f)
            }
        }
    }
    // The frame clock: every frame counted; with auto-pan the camera moves each frame so the whole table redraws
    LaunchedEffect(Unit) {
        var t = 0.0
        while (true) {
            withFrameNanos { nanos ->
                if (frames.last != 0L) frames.add((nanos - frames.last) / 1e6)
                frames.last = nanos
                if (autoPan) {
                    t += 0.016
                    camera.x = -40f + 300f * kotlin.math.sin(t).toFloat()
                    camera.zoom = 1f + 0.5f * kotlin.math.sin(t * 0.7).toFloat()
                    tick++
                }
                if (frames.deltas.size % 20 == 0) { stats = frames.text(); publish("frameStats", stats) }
                publish("camera", "${camera.zoom},${camera.x},${camera.y}")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xff2f4f3f))) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(Unit) {
                // Mouse: wheel zoom about the pointer, hover, right-click
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val p = e.changes.first().position
                        when (e.type) {
                            PointerEventType.Scroll -> {
                                val dy = e.changes.first().scrollDelta.y
                                val factor = 1.1f.pow(-dy.coerceIn(-3f, 3f))
                                val wx = p.x / camera.zoom + camera.x; val wy = p.y / camera.zoom + camera.y
                                camera.zoom = (camera.zoom * factor).coerceIn(0.2f, 6f)
                                camera.x = wx - p.x / camera.zoom; camera.y = wy - p.y / camera.zoom
                                tick++
                                e.changes.forEach { it.consume() }
                            }
                            PointerEventType.Move, PointerEventType.Enter -> {
                                val hit = hitTest(p, camera)
                                if (hit != hovered) { hovered = hit; publish("hovered", hit.toString()) }
                            }
                            PointerEventType.Exit -> hovered = -1
                            PointerEventType.Press -> if (e.buttons.isSecondaryPressed) {
                                val hit = hitTest(p, camera)
                                rightClicked = if (hit >= 0) "right-click on card #$hit" else "right-click on the table"
                                publish("rightClicked", rightClicked)
                            }
                            else -> Unit
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                // Drag pan (mouse or one finger), pinch zoom (two fingers)
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val wx = centroid.x / camera.zoom + camera.x; val wy = centroid.y / camera.zoom + camera.y
                    camera.zoom = (camera.zoom * zoom).coerceIn(0.2f, 6f)
                    camera.x = wx - centroid.x / camera.zoom - pan.x / camera.zoom
                    camera.y = wy - centroid.y / camera.zoom - pan.y / camera.zoom
                    tick++
                }
            }
        ) {
            @Suppress("UNUSED_EXPRESSION") tick
            val start = now()
            val z = camera.zoom
            val skiaFont = font
            val textPaint = org.jetbrains.skia.Paint().apply { color = 0xff222222.toInt(); isAntiAlias = true }
            withTransform({ scale(z, z, Offset.Zero); translate(-camera.x, -camera.y) }) {
                for (c in cards) {
                    val cx = c.x + CARD_W / 2; val cy = c.y + CARD_H / 2
                    rotate(if (c.tapped) 90f else 0f, Offset(cx, cy)) {
                        val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x, c.y, c.x + CARD_W, c.y + CARD_H, CornerRadius(4f))) }
                        drawPath(outline, Color.White)
                        val image = images.getOrNull(c.image % images.size.coerceAtLeast(1))
                        if (image != null) clipPath(outline) {
                            drawImage(image, IntOffset.Zero, IntSize(image.width, image.height),
                                IntOffset(c.x.toInt(), c.y.toInt()), IntSize(CARD_W.toInt(), CARD_H.toInt()))
                        }
                        if (skiaFont != null) {
                            // Title chip and P/T badge drawn as the app does: native canvas text
                            drawRoundRect(Color.White, Offset(c.x + 2f, c.y + 2f), Size(40f, 10f), CornerRadius(2f))
                            drawContext.canvas.nativeCanvas.drawString("Card ${c.index}", c.x + 4f, c.y + 10f, skiaFont.apply { this.size = 8f }, textPaint)
                            drawRoundRect(Color.White, Offset(c.x + CARD_W - 18f, c.y + CARD_H - 12f), Size(16f, 10f), CornerRadius(2f))
                            drawContext.canvas.nativeCanvas.drawString("${c.index % 5}/${c.index % 7}", c.x + CARD_W - 16f, c.y + CARD_H - 4f, skiaFont, textPaint)
                        }
                        val highlight = c.index == hovered
                        drawPath(outline, if (highlight) Color(0xffffd54f) else Color(0xff333333), style = Stroke(if (highlight) 3f else 1f))
                    }
                }
            }
            frames.addDraw(now() - start)
        }
        Column(Modifier.padding(8.dp).background(Color(0xcc000000))) {
            val style = TextStyle(color = Color.White, fontSize = 13.sp)
            BasicText("ATW web spike C: Compose Multiplatform (Kotlin/Wasm) table, $CARDS cards. $status", style = style)
            BasicText(stats, style = style)
            BasicText("wheel: zoom, drag: pan, pinch: zoom, right-click: detect. hovered: ${if (hovered >= 0) "#$hovered" else "-"}  $rightClicked", style = style)
            BasicText(if (autoPan) "[auto-pan on: tap here to stop]" else "[tap here for auto-pan: continuous full redraw]", style = style,
                modifier = Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); if (e.type == PointerEventType.Release) autoPan = !autoPan } } })
        }
    }
}

/** The topmost card under the screen point [p] (tapped cards hit-tested in their rotated rectangle). */
private fun hitTest(p: Offset, camera: Camera): Int {
    val wx = p.x / camera.zoom + camera.x; val wy = p.y / camera.zoom + camera.y
    for (c in cards.asReversed()) {
        val cx = c.x + CARD_W / 2; val cy = c.y + CARD_H / 2
        val (w, h) = if (c.tapped) CARD_H to CARD_W else CARD_W to CARD_H
        if (wx in cx - w / 2..cx + w / 2 && wy in cy - h / 2..cy + h / 2) return c.index
    }
    return -1
}
