package org.asyncttwiz.android

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
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

private fun writeCaption(path: String, caption: String) {
    require(path.endsWith(".png")) { "Screenshot path must end with .png: $path" }
    require(caption.isNotBlank() && '\n' !in caption) { "Caption must be one non-empty line: $path" }
    File(path.removeSuffix(".png") + ".txt").apply { parentFile?.mkdirs() }.writeText(caption.trim() + "\n")
}

/** Press ripples don't run on the test clock, so a capture right after a tap could differ run to run. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NoRipples(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
