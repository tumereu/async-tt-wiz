package org.asyncttwiz.android

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType

/** Committed reference images. Every run compares each capture against its image here and fails on any
 * difference; `-Pscreenshots=record` rewrites them for an intended visual change (see AGENTS.md). */
const val SCREENSHOTS = "src/test/screenshots"

/** For a capture the test only reads back for its own pixel checks: written, never compared or committed. */
val ScratchCapture = RoborazziOptions(taskType = RoborazziTaskType.Record)

/** Press ripples don't run on the test clock, so a capture right after a tap could differ run to run. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NoRipples(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
