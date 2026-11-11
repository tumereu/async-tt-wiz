package org.asyncttwiz.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.ArtworkUrlPolicy
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** Robolectric creates this instead of [TabletopApplication] (its "Test" name convention), so every test starts with
 * [PlaceholderArtwork] as the process artwork service. */
class TestTabletopApplication : TabletopApplication() {
    override fun onCreate() {
        super.onCreate()
        PlaceholderArtwork.install(this)
        // Faults are logged in tests, never sent.
        FaultReports.install(send = {})
    }
}

/** Every card with an artwork source shows the shared placeholder picture (`placeholder-art.png`, a generated neutral
 * landscape, not card art): art crops show it as is; full-card sizes show it in the art box of a plain drawn card frame,
 * as a real card image would. Served at once on the requesting coroutine, so a screenshot taken when the UI is idle
 * already shows it. Nothing is downloaded: background prefetches only reach a closed local port. */
internal class PlaceholderArtwork(context: Context, private val available: Boolean = true) :
    CardArtworkService(context, File(context.cacheDir, "placeholder-artwork"), ArtworkUrlPolicy(OFFLINE), "$OFFLINE/cards/") {
    override val loader: ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(memoryBudget).weakReferencesEnabled(false).build() }
        .diskCache(null)
        .coroutineContext(EmptyCoroutineContext)
        .components {
            add(Keyer<CardArtworkRequest> { data, _ -> data.key })
            add(Fetcher.Factory<CardArtworkRequest> { data, _, _ -> Fetcher {
                check(available) { "Artwork unavailable" }
                ImageFetchResult(image(data.reference).asImage(), true, DataSource.MEMORY)
            } })
        }.build()

    companion object {
        private const val OFFLINE = "http://127.0.0.1:9"

        /** The committed picture, in the proportions of a provider art crop. */
        val art: Bitmap by lazy {
            checkNotNull(PlaceholderArtwork::class.java.classLoader!!.getResourceAsStream("placeholder-art.png")).use {
                checkNotNull(BitmapFactory.decodeStream(it))
            }
        }

        private val names = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val cards = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

        /** A stable provider printing id for a fixture card; its placeholder card images show [name] (then each of
         * [otherFaces] for the later faces) in the name bar. */
        fun printing(name: String, vararg otherFaces: String): String =
            java.util.UUID.nameUUIDFromBytes("printing:$name".toByteArray()).toString().also { id ->
                (listOf(name) + otherFaces).forEachIndexed { face, faceName -> names["$id|$face"] = faceName }
            }

        /** A plain card in printed 488×680 proportions: dark border, name bar (with the fixture card's name when known),
         * the art in its art box, type bar, text box. */
        fun card(name: String?): Bitmap = cards.getOrPut(name.orEmpty()) {
            val width = 488; val height = 680
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = 0xff1c1c1c.toInt()
                canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 22f, 22f, paint)
                fun box(left: Float, top: Float, right: Float, bottom: Float, color: Long) {
                    paint.color = color.toInt()
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                box(22f, 22f, width - 22f, height - 44f, 0xffbdb6a6)
                box(32f, 32f, width - 32f, 70f, 0xffe4ded1)
                drawArt(canvas, RectF(38f, 76f, width - 38f, 376f))
                box(32f, 382f, width - 32f, 418f, 0xffe4ded1)
                box(38f, 426f, width - 38f, 610f, 0xffece7dc)
                if (!name.isNullOrEmpty()) {
                    paint.color = 0xff1c1c1c.toInt()
                    paint.textSize = 26f
                    while (paint.measureText(name) > width - 92f && paint.textSize > 10f) paint.textSize -= 1f
                    canvas.drawText(name, 46f, 60f, paint)
                }
            }
        }

        fun image(reference: ArtworkReference): Bitmap =
            if (reference.size == "art_crop" && reference.customUrl == null) art
            else card(reference.faceName ?: names["${reference.printing}|${reference.face}"])

        /** Fills [box] with the placeholder picture, centred and cropped (never stretched), like a printed art box. */
        fun drawArt(canvas: Canvas, box: RectF) {
            val scale = maxOf(box.width() / art.width, box.height() / art.height)
            val width = (box.width() / scale).toInt(); val height = (box.height() / scale).toInt()
            val left = (art.width - width) / 2; val top = (art.height - height) / 2
            canvas.drawBitmap(art, Rect(left, top, left + width, top + height), box, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        /** Replaces the process artwork service (closing an earlier placeholder) with a fresh one for this test;
         * with [available] false every artwork load fails, as when a picture can't be downloaded. */
        fun install(context: Context, available: Boolean = true) {
            val provider = PublicArtwork::class.java.getDeclaredField("provider").apply { isAccessible = true }.get(PublicArtwork)
            val field = provider.javaClass.getDeclaredField("service").apply { isAccessible = true }
            (field.get(provider) as? PlaceholderArtwork)?.close()
            field.set(provider, PlaceholderArtwork(context.applicationContext, available))
        }
    }
}

/** A stable provider printing id for a fixture card, so it has an artwork source like every real card. */
internal fun placeholderPrinting(name: String, vararg otherFaces: String): String = PlaceholderArtwork.printing(name, *otherFaces)
