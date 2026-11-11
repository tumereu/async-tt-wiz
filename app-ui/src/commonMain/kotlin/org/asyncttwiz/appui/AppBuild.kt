package org.asyncttwiz.appui

/**
 * The values a build of the app is made with (Android: the app's BuildConfig fields of the same names). The platform
 * finds its own ([platformBuildInfo]) the first time one is read, so plain unit tests read them as the app does.
 */
interface BuildInfo {
    val accountOrigin: String
    val resetOrigin: String
    val loopbackApi: Boolean
    val versionName: String
    val versionCode: Int
    val buildType: String
    val gitCommit: String
    val debug: Boolean
    val diagnostics: Boolean
    val accountSwitcher: Boolean
    val catalogChecksum: String
    val artTestOrigin: String
    val artBase: String
    val artContact: String
}

/** The build's values, named as the Android app's BuildConfig names them. */
@Suppress("PropertyName")
object AppBuild {
    private val info: BuildInfo by lazy { platformBuildInfo() }
    val ACCOUNT_ORIGIN: String get() = info.accountOrigin
    val RESET_ORIGIN: String get() = info.resetOrigin
    val LOOPBACK_API: Boolean get() = info.loopbackApi
    val VERSION_NAME: String get() = info.versionName
    val VERSION_CODE: Int get() = info.versionCode
    val BUILD_TYPE: String get() = info.buildType
    val GIT_COMMIT: String get() = info.gitCommit
    val DEBUG: Boolean get() = info.debug
    val DIAGNOSTICS: Boolean get() = info.diagnostics
    val ACCOUNT_SWITCHER: Boolean get() = info.accountSwitcher
    val CATALOG_CHECKSUM: String get() = info.catalogChecksum
    val ART_TEST_ORIGIN: String get() = info.artTestOrigin
    val ART_BASE: String get() = info.artBase
    val ART_CONTACT: String get() = info.artContact
}

/** The app's [BuildInfo] (Android: the one the app module registers as a Java service). */
internal expect fun platformBuildInfo(): BuildInfo
