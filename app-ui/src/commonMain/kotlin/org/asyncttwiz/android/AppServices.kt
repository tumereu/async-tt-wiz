package org.asyncttwiz.android

import coil3.PlatformContext
import org.asyncttwiz.protocol.ActivityHint
import org.asyncttwiz.protocol.PodSummary
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.storage.CardCatalog
import org.asyncttwiz.storage.ParsedCard

/**
 * The platform services the app's view models and screens take whose types still live in this module (web client step
 * 2b; they join [org.asyncttwiz.appui.AppPlatform] when the code moves, docs/specs/web-client.md §11 step 2). Android:
 * [AndroidAppServices], installed by the Application before anything else runs.
 */
interface AppServices {
    /** The card catalog's database on this device. */
    fun catalogRepository(): CardCatalog

    /** A new installer of the card catalog into this device's catalog, from the app's server. */
    fun catalogController(): CardRecordsController

    /** A new artwork service with its files where [context] keeps them (blocking: it repairs its disk cache). */
    fun artworkService(context: PlatformContext): ArtworkService

    /** What artwork requests are made with outside the UI (Android: the application). */
    val artworkContext: PlatformContext

    /** The signed-in session's store, protected on this device. */
    fun sessionStore(): SessionStore

    /** The accounts' data on this device. */
    fun accountLocalData(): AppLocalData

    /** Alerts about the account's activity: notifications and checks while the app is closed. One per process. */
    val activityDelivery: PlatformActivityDelivery

    companion object {
        private var installed: AppServices? = null

        /** The installed services; the shell installs them before any UI or app code runs. */
        val current: AppServices get() = checkNotNull(installed) { "No AppServices installed" }

        fun install(services: AppServices) { installed = services }
    }
}

/** The accounts' data on this device (blocking; the auth worker's), with the card definitions Automatum reads. */
interface AppLocalData : AccountLocalData, AutoCloseable {
    /** The game's card definitions for Automatum (I-D2), parsed or read from the device cache. Blocking. */
    fun cardDefinitions(submitted: Collection<SubmittedDefinition>): Map<String, ParsedCard>
}

/** Process-wide owner fence for activity alerts (notifications, background checks) shared by the UI and the platform's
 * background work (Android: [AndroidActivityDelivery]). */
interface PlatformActivityDelivery {
    var authenticationRequired: ((String) -> Unit)?
    var activityHints: ((List<ActivityHint>) -> Unit)?
    fun rememberPods(pods: List<PodSummary>)
    fun session(account: String?, token: String?): Boolean
    fun clear(expectedBinding: String? = null)
    fun display(screen: ActivityScreen, foreground: Boolean)
    fun foreground()
    fun accepts(account: String?, binding: String?): Boolean
    fun rejected(token: String)
}
