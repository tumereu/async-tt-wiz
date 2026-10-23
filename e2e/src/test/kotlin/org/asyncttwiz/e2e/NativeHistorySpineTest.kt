package org.asyncttwiz.e2e

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Authored against normal production labels. Compile is not a device execution or full journey.
 * The supervisor's native owner runs this under the existing whole device/routing/backend wrapper. */
@Tag("device")
class NativeHistorySpineTest {
    @Test fun normalTableExactReviewArmCancelAndCurrentUseExistingPhaseAdapters() {
        check(System.getenv("ATW_START_STAGE").orEmpty() in setOf("", "setup"))
        NativeGameStartTest().twoPlayersReadyToActualInitialTableAndRecoverOriginalStart()
        val run = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val peer = Path.of(Files.readString(run.resolve("peer-run.txt")))
        NativeLobbyCreationTest.Device(run, run.resolve("runner.properties")).use { first ->
            NativeLobbyCreationTest.Device(peer, peer.resolve("runner.properties")).use { second ->
                first.ui.reopen("Native Initial Spark", "Shared table")
                second.ui.reopen("Native Initial Spark", "Shared table")
                first.named("Start First, life 20").click()
                first.named("Increase life").click()
                first.named("Close player actions").click()
                second.named("Start First, life 21")
                first.ui.button("‹ Previous")
                first.ui.text("Paused")
                first.named("Start First, life 20")
                first.ui.button("Continue playing from here")
                first.ui.button("Cancel continuation")
                first.named("History").click()
                first.ui.text("Make current")
                first.driver.navigate().back()
                first.ui.button("Return to current")
                first.named("Start First, life 21")
                first.named("Undo").click()
                second.named("Start First, life 20")
                first.named("Interaction mode").click()
                first.ui.button("Redo")
                second.named("Start First, life 21")
                first.capture("normal-history-review-arm-cancel-current-redo")
            }
        }
    }
}
