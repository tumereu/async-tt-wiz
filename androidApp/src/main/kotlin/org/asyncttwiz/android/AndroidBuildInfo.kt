package org.asyncttwiz.android

import org.asyncttwiz.appui.BuildInfo

/** This build's BuildConfig for the shared UI ([org.asyncttwiz.appui.AppBuild]), registered as a Java service
 * (resources/META-INF/services) so it is found without the app having started (plain unit tests too). */
class AndroidBuildInfo : BuildInfo {
    override val accountOrigin = BuildConfig.ACCOUNT_ORIGIN
    override val resetOrigin = BuildConfig.RESET_ORIGIN
    override val loopbackApi = BuildConfig.LOOPBACK_API
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val buildType = BuildConfig.BUILD_TYPE
    override val gitCommit = BuildConfig.GIT_COMMIT
    override val debug = BuildConfig.DEBUG
    override val diagnostics = BuildConfig.DIAGNOSTICS
    override val accountSwitcher = BuildConfig.ACCOUNT_SWITCHER
    override val catalogChecksum = BuildConfig.CATALOG_CHECKSUM
    override val artTestOrigin = BuildConfig.ART_TEST_ORIGIN
    override val artBase = BuildConfig.ART_BASE
    override val artContact = BuildConfig.ART_CONTACT
}
