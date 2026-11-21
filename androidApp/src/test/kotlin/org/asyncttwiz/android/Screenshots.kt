package org.asyncttwiz.android

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import java.io.File

/** Committed reference images. Every run compares each capture against its image here and fails on any
 * difference; `-Pscreenshots=record` rewrites them for an intended visual change (see AGENTS.md). */
const val SCREENSHOTS = "src/test/screenshots"

/** For a capture the test only reads back for its own pixel checks: written, never compared or committed. */
val ScratchCapture = RoborazziOptions(taskType = RoborazziTaskType.Record)

/** Captures the committed reference image [path] of this node like [captureRoboImage] and writes [caption]
 * to its sidecar (`foo.png` → `foo.txt`) for the owner's screenshot review. The sidecar is written on every
 * run, so it always matches the code. [caption] is one plain sentence: what the picture shows and how the
 * test got there. */
fun SemanticsNodeInteraction.captureShot(path: String, caption: String,
    options: RoborazziOptions = provideRoborazziContext().options) {
    writeCaption(path, caption)
    captureRoboImage(path, options)
}

/** [captureShot] for the whole screen, dialogs and popups included, like [captureScreenRoboImage]. */
@OptIn(ExperimentalRoborazziApi::class)
fun captureShot(path: String, caption: String, options: RoborazziOptions = provideRoborazziContext().options) {
    writeCaption(path, caption)
    captureScreenRoboImage(path, options)
}

private fun writeCaption(path: String, caption: String, extension: String = ".png") {
    require(path.endsWith(extension)) { "Screenshot path must end with $extension: $path" }
    require(caption.isNotBlank() && '\n' !in caption) { "Caption must be one non-empty line: $path" }
    File(path.removeSuffix(extension) + ".txt").apply { parentFile?.mkdirs() }.writeText(caption.trim() + "\n")
}

/** A clip's frames are this far apart on the test clock: three 16 ms Compose frames, about 21 frames a second. */
const val CLIP_STEP_MILLIS = 48L

/** How long a clip holds its last frame before it loops, so its end state can be read. */
private const val CLIP_END_HOLD_MILLIS = 1_200L

/**
 * Captures the committed animated clip [path] (`.webp`, looping) of a key animation for the owner's gallery review beside
 * the screenshots, and writes [caption] to its sidecar like [captureShot]. Its frames are taken [stepMillis] apart on the
 * test clock from now until [millis] have passed (the test sets `mainClock.autoAdvance = false` first), so the clock has
 * moved on by [millis] when it returns; [onFrame] runs before each frame with the time since the first, for what the test
 * does during the clip (a tap, a new board). Each frame is [node] (the content by default) or, with [screen], the whole
 * screen with its dialogs and popups, as [captureShot] without a node takes it. ImageMagick (`magick`, with WebP) encodes
 * the frames into a lossy animated WebP, identical frames merged. Like the screenshots, a run compares the clip with the
 * committed one and fails on any difference; `-Pscreenshots=record` rewrites it.
 */
fun ComposeTestRule.captureClip(path: String, caption: String, millis: Long, stepMillis: Long = CLIP_STEP_MILLIS,
    node: SemanticsNodeInteraction = onRoot(), screen: Boolean = false, onFrame: (elapsedMillis: Long) -> Unit = {}) {
    require(!mainClock.autoAdvance) { "A clip runs on the test clock: set mainClock.autoAdvance = false first ($path)" }
    require(millis > 0 && stepMillis > 0) { "A clip needs a length and a frame step: $path" }
    writeCaption(path, caption, ".webp")
    val record = System.getProperty("roborazzi.test.record") == "true"
    val verify = System.getProperty("roborazzi.test.verify") == "true"
    val frames = File("build/outputs/clips/frames/" + path.substringAfterLast("screenshots/").removeSuffix(".webp"))
    frames.deleteRecursively()
    frames.mkdirs()
    val inputs = ArrayList<List<String>>()
    var elapsed = 0L
    while (true) {
        onFrame(elapsed)
        waitForIdle()
        inputs += if (screen) captureScreenFrame(File(frames, "%04d.png".format(inputs.size)))
            else captureNodeFrame(node, File(frames, "%04d.rgba".format(inputs.size)))
        if (elapsed >= millis) break
        mainClock.advanceTimeBy(stepMillis)
        elapsed += stepMillis
    }
    if (!record && !verify) return
    val encoded = File(frames.parentFile, frames.name + ".webp")
    val command = listOf("magick", "-delay", "${stepMillis}x1000") + inputs.dropLast(1).flatten() +
        listOf("-delay", "${CLIP_END_HOLD_MILLIS}x1000") + inputs.last() +
        listOf("-loop", "0", "-quality", "70", "-define", "webp:method=6", "-strip", encoded.path)
    val process = try { ProcessBuilder(command).redirectErrorStream(true).start() }
        catch (e: java.io.IOException) { throw AssertionError("Clips need ImageMagick's magick with WebP on the PATH ($path)", e) }
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0 && encoded.isFile) { "magick failed to encode $path: $output" }
    val committed = File(path)
    if (record) {
        committed.parentFile?.mkdirs()
        encoded.copyTo(committed, overwrite = true)
    } else if (!committed.isFile || !committed.readBytes().contentEquals(encoded.readBytes())) {
        throw AssertionError("Clip $path differs from the committed one (new one: ${encoded.path}); " +
            "re-record it with -Pscreenshots=record for an intended change")
    }
}

/** [node]'s pixels written raw to [file]: the frame's ImageMagick input (its size and format given with it). */
private fun captureNodeFrame(node: SemanticsNodeInteraction, file: File): List<String> {
    val bitmap = node.captureToImage().asAndroidBitmap()
    val (width, height) = bitmap.width to bitmap.height
    val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
    val bytes = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { i, argb ->
        bytes[4 * i] = (argb shr 16).toByte(); bytes[4 * i + 1] = (argb shr 8).toByte()
        bytes[4 * i + 2] = argb.toByte(); bytes[4 * i + 3] = (argb ushr 24).toByte()
    }
    file.writeBytes(bytes)
    return listOf("-size", "${width}x$height", "-depth", "8", "rgba:${file.path}")
}

/** The whole screen, dialogs and popups included, written to [file] as a PNG: the frame's ImageMagick input. */
@OptIn(ExperimentalRoborazziApi::class)
private fun captureScreenFrame(file: File): List<String> {
    captureScreenRoboImage(file.path, ScratchCapture)
    return listOf("png:${file.path}")
}


/** Press ripples don't run on the test clock, so a capture right after a tap could differ run to run. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NoRipples(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
