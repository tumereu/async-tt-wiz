package org.asyncttwiz.android

// The engine's background thread becomes a Web Worker with the web table (docs/specs/web-client.md §11 step 4).
private fun later(): Nothing = throw NotImplementedError("web client step 4")

actual object BackgroundEngineWorker : EngineWorker {
    actual override fun run(work: () -> Unit): Unit = later()
    actual override fun <T> call(work: () -> T): T = later()
    actual override fun main(work: () -> Unit): Unit = later()
    actual override fun frame(work: () -> Unit): Unit = later()
    actual override fun early(work: (release: (() -> Unit)?) -> Unit): Unit = later()
}
