@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.web

import org.asyncttwiz.android.pageOrigin
import org.asyncttwiz.android.runEngineBench
import org.asyncttwiz.android.startWebApp
import org.asyncttwiz.appui.BuildInfo

/** The web client's entry point (docs/specs/web-client.md §1 item 1): the build's values, then the app (:app-ui). */
fun main() {
    // Dev only: a test build opened at #engine-bench measures the engine's Web Worker in place of the app
    if (!WebBuildValues.RELEASE && pageHash() == "#engine-bench") { runEngineBench(assetBase()); return }
    val origin = pageOrigin()
    startWebApp(WebBuildInfo(origin), assetBase())
}

/**
 * This web build's values, as the Android app's BuildConfig names them. The API is the page's own origin (the server
 * serves both); plain HTTP only on the loopback address, for local runs and the end-to-end tests.
 */
private class WebBuildInfo(origin: String) : BuildInfo {
    override val accountOrigin = origin
    override val resetOrigin = origin
    override val loopbackApi = origin.startsWith("http://127.0.0.1:") || origin == "http://127.0.0.1"
    override val versionName = WebBuildValues.VERSION_NAME
    override val versionCode = WebBuildValues.VERSION_CODE
    override val buildType = if (WebBuildValues.RELEASE) "release" else "debug"
    override val gitCommit = WebBuildValues.GIT_COMMIT
    override val debug = !WebBuildValues.RELEASE
    override val diagnostics = !WebBuildValues.RELEASE
    override val accountSwitcher = !WebBuildValues.RELEASE
    override val catalogChecksum = WebBuildValues.CATALOG_CHECKSUM
    override val artTestOrigin = ""
    override val artBase = "https://api.scryfall.com/cards/"
    override val artContact = WebBuildValues.ART_CONTACT
}

/** Where this build's own files are (index.html says: the server's /web/<build>/, or the page's folder). */
private fun assetBase(): String = js("(typeof globalThis.atwBase === 'string') ? globalThis.atwBase : '/'")

/** The page address's fragment ("#..."), "" without one. */
private fun pageHash(): String = js("(typeof location !== 'undefined') ? location.hash : ''")
