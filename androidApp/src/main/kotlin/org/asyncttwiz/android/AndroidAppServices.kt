package org.asyncttwiz.android

import android.content.Context
import coil3.PlatformContext
import org.asyncttwiz.appui.postToMain
import org.asyncttwiz.storage.AndroidCatalogStorage
import org.asyncttwiz.storage.CatalogRepository
import org.asyncttwiz.storage.HttpCatalogSource
import java.util.concurrent.Executors

/** The app's [AppServices] on Android: the application's catalog, artwork, Keystore session, account data and
 * notifications. Installed by [TabletopApplication]. */
internal class AndroidAppServices(context: Context) : AppServices {
    private val application = context.applicationContext

    override fun catalogRepository(): CatalogRepository = AndroidCatalogStorage.repository(application)

    override fun catalogController(): CatalogController = CatalogController(AndroidCatalogStorage.directory(application),
        catalogRepository(), { HttpCatalogSource(BuildConfig.ACCOUNT_ORIGIN) }, BuildConfig.CATALOG_CHECKSUM.ifEmpty { null },
        Executors.newSingleThreadExecutor { Thread(it, "public-catalog") }, { action -> postToMain(action) })

    override fun artworkService(context: PlatformContext): ArtworkService = CardArtworkService(context.applicationContext)

    override val artworkContext: PlatformContext get() = application

    override fun sessionStore(): SessionStore = ProtectedSessionStore(application, BuildConfig.ACCOUNT_ORIGIN)

    override fun accountLocalData(): AppLocalData = AndroidAccountLocalData(application)

    override val activityDelivery: PlatformActivityDelivery get() = AndroidActivityDelivery.get(application)
}
