@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.TimeSource
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.Preferences
import org.asyncttwiz.protocol.ActivityHint
import org.asyncttwiz.protocol.PodSummary
import org.asyncttwiz.storage.CardCatalog

/**
 * The fonts the page falls back to for characters its default font (Roboto, the only one Compose brings to the browser)
 * has no glyph for, where Android draws them from its system fonts: the cards' type glyphs (♧ ♙ ϟ ◇ ⚙ ☆), the covered
 * library's ✧, the hourglass, arrows, the timeline's and table actions' symbols, mathematical signs and the pick mark's 🔥.
 * Noto (webApp's resources/fonts, SIL OFL 1.1 beside them), each cut down to the Unicode blocks the shared UI draws from:
 * Symbols, Symbols 2 and Math to arrows, technical and geometric shapes, enclosed numbers, box drawing, miscellaneous
 * symbols, dingbats and mathematical operators; Noto Sans to Greek; Noto Color Emoji to 🔥. A character takes the first
 * of them that has it.
 */
internal val WEB_FALLBACK_FONTS = listOf("fonts/NotoColorEmoji-Atw.ttf", "fonts/NotoSansSymbols-Atw.ttf",
    "fonts/NotoSansSymbols2-Atw.ttf", "fonts/NotoSansMath-Atw.ttf", "fonts/NotoSansGreek-Atw.ttf")

/**
 * WEB-064: every emoji, in colour (Noto Color Emoji, its COLRv1 vector font, about 5 MB): what players write in chats, game
 * and deck names and notes. Too big to hold up the page's start, it is fetched once the app shows ([loadEmojiFont]), from
 * the build's folder, so the browser keeps it with the build's other files. It falls back after the pick mark's 🔥 and
 * before the symbol fonts, so ❤ ⭐ ✨ show in colour as on a phone; the few symbols the app itself draws (the timeline's
 * ↔ ↗ ↘ ♥ ♨ ⚔, the hourglass ⌛, the artifact glyph ⚙ and the skull ☠) are left out of it and keep the symbol fonts' plain
 * glyphs. Made with fontTools' subsetter from Noto-COLRv1.ttf: every code point but those (U+2194 U+2197 U+2198 U+231B
 * U+2620 U+2665 U+2668 U+2694 U+2699), all layout features (the sequences: flags, skin tones, families), all names, no
 * hinting.
 */
internal const val WEB_EMOJI_FONT = "fonts/NotoColorEmojiFull-Atw.ttf"

/** The app's bundled files the web shell fetches before the app starts (the shared UI reads them synchronously). */
internal val WEB_ASSETS = listOf("release-notes.json", "fonts/keyrune.ttf", "fonts/keyrune-codes.txt") + WEB_FALLBACK_FONTS

/**
 * Preloads [WEB_FALLBACK_FONTS] into the page's font resolver ([org.asyncttwiz.appui.webFontResolver]) before anything
 * draws: the text it lays out (the screens' and the table's, [org.asyncttwiz.appui.drawNativeText]) falls back to the fonts
 * preloaded there for a character its own font lacks.
 */
internal suspend fun preloadFallbackFonts(platform: AppPlatform) {
    val fonts = WEB_FALLBACK_FONTS.map { path -> path to { platform.assetFont(path) } }
    val registered = registerFonts(org.asyncttwiz.appui.webFontResolver, fonts)
    AppLog.info("web-page", "Fallback fonts registered: ${registered.joinToString { it.substringAfterLast('/') }}")
}

/**
 * WEB-064: fetches [WEB_EMOJI_FONT] from [base] (the build's folder) and lays the page's text out again with it: a new font
 * resolver gets the fallback fonts with the emoji font after the first ([WEB_FALLBACK_FONTS]) and replaces the page's
 * ([org.asyncttwiz.appui.useWebFontResolver]). Until then, and if it can't be had, emojis other than 🔥 show as boxes.
 */
internal suspend fun loadEmojiFont(platform: AppPlatform, base: String) {
    val started = TimeSource.Monotonic.markNow()
    AppLog.info("web-page", "Emoji font: fetching ${WEB_EMOJI_FONT.substringAfterLast('/')}")
    val bytes = try {
        val response = fetchBytes(base + WEB_EMOJI_FONT, 16.0 * 1024 * 1024, "*/*").await<JsAny>()
        check(responseStatus(response) == 200) { "HTTP ${responseStatus(response)}" }
        readBytes(responseBytes(response))
    } catch (failure: Throwable) {
        AppLog.warn("web-page", "Emoji font not loaded after ${started.elapsedNow().inWholeMilliseconds} ms: emojis other " +
            "than 🔥 show as boxes until the page is opened again", failure)
        return
    }
    val fetched = started.elapsedNow().inWholeMilliseconds
    val emoji = androidx.compose.ui.text.platform.Font(WEB_EMOJI_FONT, bytes)
    val fonts = WEB_FALLBACK_FONTS.map { path -> path to { platform.assetFont(path) } }
    val resolver = createFontFamilyResolver()
    val registered = registerFonts(resolver, fonts.take(1) + (WEB_EMOJI_FONT to { emoji }) + fonts.drop(1))
    if (WEB_EMOJI_FONT !in registered) return
    org.asyncttwiz.appui.useWebFontResolver(resolver)
    AppLog.info("web-page", "Emoji font registered (${bytes.size / 1024} KB, fetched in $fetched ms, ready in " +
        "${started.elapsedNow().inWholeMilliseconds} ms): the page's text is laid out again with " +
        registered.joinToString { it.substringAfterLast('/') })
}

/**
 * Preloads [fonts] (path and font, in fallback order) into [resolver], where text it lays out falls back to them; the paths
 * of those it took. Preloading loads only fonts fetched later: resolving each registers its in-memory font with the
 * resolver's font cache, where text falls back to it. Text laid out by another resolver would not see it.
 */
private suspend fun registerFonts(resolver: FontFamily.Resolver, fonts: List<Pair<String, () -> Font>>): List<String> =
    fonts.filter { (path, font) ->
        try {
            val family = FontFamily(font())
            resolver.preload(family)
            resolver.resolve(family)
            true
        } catch (failure: Throwable) {
            AppLog.warn("web-page", "Fallback font $path not registered: its symbols may show as boxes", failure)
            false
        }
    }.map { it.first }

/** A fetched body's bytes ([fetchBytes]), read four at a time: a large one in a quarter of the calls into the page. */
private fun readBytes(body: JsAny): ByteArray {
    val size = bytesLength(body)
    val bytes = ByteArray(size)
    var index = 0
    while (index + 4 <= size) {
        val word = wordAt(body, index)
        bytes[index] = word.toByte(); bytes[index + 1] = (word shr 8).toByte()
        bytes[index + 2] = (word shr 16).toByte(); bytes[index + 3] = (word shr 24).toByte()
        index += 4
    }
    while (index < size) { bytes[index] = byteAt(body, index).toByte(); index++ }
    return bytes
}

private fun wordAt(bytes: JsAny, index: Int): Int =
    js("(bytes[index] | (bytes[index + 1] << 8) | (bytes[index + 2] << 16) | (bytes[index + 3] << 24))")

/**
 * The shared UI's platform in the browser ([AppPlatform]): small settings in the page's local storage, the bundled files
 * fetched before the app starts ([assets]), and the app's two pictures drawn from the Android app's vector paths.
 */
internal class WebAppPlatform(private val assets: Map<String, ByteArray>, private val firstVisit: Boolean) : AppPlatform {
    private val files = mutableMapOf<String, WebPreferences>()

    override fun preferences(name: String): Preferences = files.getOrPut(name) { WebPreferences(name) }

    override fun readAsset(path: String): ByteArray = assets[path] ?: throw NoSuchElementException("No bundled file $path")

    override fun assetFont(path: String): Font = androidx.compose.ui.text.platform.Font(path, readAsset(path))

    /** The browser's first visit counts as a fresh install: What's new shows nothing then (LOB-087). */
    override fun freshInstall(): Boolean = firstVisit

    @Composable override fun swordsOutlined(): Painter = rememberVectorPainter(Swords)

    @Composable override fun atwLogo(): Painter = rememberVectorPainter(Logo)

    /** The page has one input stream: the shell counts every tap and key as game input already. */
    @Composable override fun ObserveDialogInput() {
        androidx.compose.runtime.DisposableEffect(Unit) {
            org.asyncttwiz.appui.WebBack.dialogs++
            onDispose { org.asyncttwiz.appui.WebBack.dialogs-- }
        }
    }

    companion object {
        /** Fetches [WEB_ASSETS] from [base] (the build's own folder), each tried again a couple of times when it fails (WEB-005). */
        suspend fun fetchAssets(base: String): Map<String, ByteArray> = WEB_ASSETS.associateWith { path ->
            loadBuiltIn("Bundled file $path") {
                val response = fetchBytes(base + path, 8.0 * 1024 * 1024, "*/*").await<JsAny>()
                check(responseStatus(response) == 200) { "Bundled file $path: HTTP ${responseStatus(response)}" }
                responseBytes(response).toByteArray()
            }
        }

        // The Android app's res/drawable atw_logo.xml and swords_outlined.xml, path for path.
        private val Logo: ImageVector by lazy {
            ImageVector.Builder("atw_logo", 96.dp, 80.dp, 96f, 80f).apply {
                fun path(color: Long, data: String) = addPath(addPathNodes(data), fill = SolidColor(Color(color)))
                path(0xFF244C46, "M24,58 C14,58 8,61 5,64 C0,69 8,73 20,75 C43,80 73,77 88,71 C95,68 94,65 88,62 C84,60 79,59 73,58 Z")
                path(0xFF365F53, "M23,61 C29,46 30,30 40,18 C48,8 60,7 74,12 C62,12 55,18 57,25 C60,35 66,44 74,61 C58,68 39,68 23,61 Z")
                path(0xFF244C46, "M74,12 C62,12 55,18 57,25 C60,35 66,44 74,61 L61,63 C59,46 47,32 51,23 C54,17 63,13 74,12 Z")
                path(0xFFD7BB6D, "M27,50 C40,55 55,56 68,50 L73,60 C58,67 39,67 23,61 Z")
                path(0xFFEAD99D, "M43,27 L46,34 L53,37 L46,40 L43,47 L40,40 L33,37 L40,34 Z")
                path(0xFF365F53, "M8,65 C26,72 65,75 88,64 C79,73 49,79 20,73 C11,71 7,68 8,65 Z")
            }.build()
        }

        private val Swords: ImageVector by lazy {
            ImageVector.Builder("swords_outlined", 24.dp, 24.dp, 960f, 960f).apply {
                group(translationY = 960f) {
                    addPath(addPathNodes("M762,-96L645,-212l-88,88 -28,-28q-23,-23 -23,-57t23,-57l169,-169q23,-23 57,-23t57,23l28,28 " +
                        "-88,88 116,117q12,12 12,28t-12,28l-50,50q-12,12 -28,12t-28,-12ZM880,-724L426,-270l5,4q23,23 23,57t-23,57l-28,28 " +
                        "-88,-88L198,-96q-12,12 -28,12t-28,-12l-50,-50q-12,-12 -12,-28t12,-28l116,-117 -88,-88 28,-28q23,-23 57,-23t57,23l4,5 " +
                        "454,-454h160v160ZM334,-583l24,-23 23,-24 -23,24 -24,23ZM278,-526L80,-724v-160h160l198,198 -57,56 -174,-174h-47v47l174,174 " +
                        "-56,57ZM370,-327l430,-430v-47h-47L323,-374l47,47ZM370,-327l-24,-23 -23,-24 23,24 24,23Z"), fill = SolidColor(Color.Black))
                }
            }.build()
        }
    }
}

/** A settings file kept as one JSON object in the page's local storage under `atw.prefs.<name>`; writes are immediate. */
internal class WebPreferences(name: String) : Preferences {
    private val key = "atw.prefs.$name"
    private var values: MutableMap<String, JsonElement> = runCatching {
        jsLocalGet(key)?.let { (Json.parseToJsonElement(it) as JsonObject).toMutableMap() }
    }.getOrNull() ?: mutableMapOf()
    private val listeners = mutableListOf<(String?) -> Unit>()

    private fun primitive(key: String) = values[key] as? JsonPrimitive

    override fun getString(key: String, default: String?): String? = primitive(key)?.takeIf { it.isString }?.content ?: default
    override fun getStringSet(key: String, default: Set<String>?): Set<String>? =
        (values[key] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: default
    override fun getBoolean(key: String, default: Boolean) = primitive(key)?.booleanOrNull ?: default
    override fun getInt(key: String, default: Int) = primitive(key)?.intOrNull ?: default
    override fun getLong(key: String, default: Long) = primitive(key)?.longOrNull ?: default
    override fun getFloat(key: String, default: Float) = primitive(key)?.floatOrNull ?: default
    override fun contains(key: String) = key in values
    override fun edit(): Preferences.Editor = Editor()

    override fun observe(listener: (String?) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private inner class Editor : Preferences.Editor {
        private val changes = mutableMapOf<String, JsonElement?>()
        private var cleared = false
        private fun put(key: String, value: JsonElement?): Editor { changes[key] = value; return this }
        override fun putString(key: String, value: String?) = put(key, value?.let(::JsonPrimitive))
        override fun putStringSet(key: String, value: Set<String>?) = put(key, value?.let { set -> JsonArray(set.map(::JsonPrimitive)) })
        override fun putBoolean(key: String, value: Boolean) = put(key, JsonPrimitive(value))
        override fun putInt(key: String, value: Int) = put(key, JsonPrimitive(value))
        override fun putLong(key: String, value: Long) = put(key, JsonPrimitive(value))
        override fun putFloat(key: String, value: Float) = put(key, JsonPrimitive(value))
        override fun remove(key: String) = put(key, null)
        override fun clear(): Editor { cleared = true; return this }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            if (cleared) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            jsLocalSet(key, JsonObject(values).toString())
            if (cleared) listeners.toList().forEach { it(null) }
            changes.keys.forEach { changed -> listeners.toList().forEach { it(changed) } }
            return true
        }
    }
}

/**
 * Activity alerts in the browser: its notifications come from the server through the browser's push service (WEB-052), so
 * this keeps the signed-in owner (notification routes and rejected sessions are told apart as on Android), hands it to
 * [notifications] (WEB-050), and closes the notifications of what the page shows (LOB-078, DRF-138).
 */
internal class BrowserActivityDelivery(origin: String) : PlatformActivityDelivery {
    private data class Owner(val account: String, val binding: String, val token: String)
    private var owner: Owner? = null
    private val browser = BrowserPlatformJs()
    /** Browser notifications and the Games list's hint (WEB-050, WEB-053), kept in this browser; their kinds are the
     * account's (LOB-276, WEB-051). */
    val notifications = WebNotifications(browser, org.asyncttwiz.appui.AppPlatform.current.preferences("web-notifications"),
        BrowserPushServer(origin)) {
        // The activity installation's id, the one the game connection names too (AuthViewModel's)
        org.asyncttwiz.appui.AppPlatform.current.preferences("installation").let { preferences ->
            preferences.getString("id", null) ?: org.asyncttwiz.appui.randomUuid().also { preferences.edit().putString("id", it).apply() }
        }
    }
    override var authenticationRequired: ((String) -> Unit)? = null
    override var activityHints: ((List<ActivityHint>) -> Unit)? = null

    init { browser.onInstallChange { notifications.refresh() } }

    override fun rememberPods(pods: List<PodSummary>) {}
    override fun session(account: String?, token: String?): Boolean {
        if (account == null || token == null) { clear(); return false }
        if (owner?.account == account && owner?.token == token) return false
        val next = Owner(account, org.asyncttwiz.appui.randomUuid(), token)
        owner = next
        org.asyncttwiz.appui.pageScope.launch { notifications.session(WebPushOwner(next.account, next.binding, next.token)) }
        return true
    }
    override fun clear(expectedBinding: String?) {
        if (expectedBinding != null && expectedBinding != owner?.binding) return
        owner = null
        org.asyncttwiz.appui.pageScope.launch { notifications.session(null) }
    }
    override fun display(screen: ActivityScreen, foreground: Boolean) { if (foreground) browser.cancelShown(screen) }
    override fun foreground() { org.asyncttwiz.appui.pageScope.launch { notifications.foreground() } }
    override fun accepts(account: String?, binding: String?): Boolean =
        account != null && binding != null && owner?.account == account && owner?.binding == binding
    override fun rejected(token: String) { if (owner?.token == token) clear() }
}

/** The app's services in the browser ([AppServices]): the storage the shell opened, the card records and pictures. */
internal class BrowserAppServices(private val storage: BrowserStorage, private val catalog: BrowserCatalogController,
    origin: String) : AppServices {
    private val artwork by lazy { BrowserArtworkService() }
    override fun catalogRepository(): CardCatalog = catalog.repository
    override fun catalogController(): CardRecordsController = catalog
    override fun artworkService(context: PlatformContext): ArtworkService = artwork
    override val artworkContext: PlatformContext get() = PlatformContext.INSTANCE
    override fun sessionStore(): SessionStore = storage.sessionStore()
    override fun accountLocalData(): AppLocalData = storage.accountLocalData()
    override val activityDelivery: BrowserActivityDelivery = BrowserActivityDelivery(origin)
}
