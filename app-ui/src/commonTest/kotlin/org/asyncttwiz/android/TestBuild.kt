package org.asyncttwiz.android

import org.asyncttwiz.appui.BuildInfo

/** A debug build's values for the common tests ([org.asyncttwiz.appui.AppBuild]), which no app module provides here: the
 * servers are unreachable, diagnostics on. */
class TestBuildInfo : BuildInfo {
    override val accountOrigin = "http://127.0.0.1:9"
    override val resetOrigin = "http://127.0.0.1:9"
    override val loopbackApi = true
    override val versionName = "0.0.0-test"
    override val versionCode = 1
    override val buildType = "debug"
    override val gitCommit = "unknown"
    override val debug = true
    override val diagnostics = true
    override val accountSwitcher = true
    override val catalogChecksum = ""
    override val artTestOrigin = ""
    override val artBase = "http://127.0.0.1:9/cards/"
    override val artContact = ""
}

/** Makes [TestBuildInfo] the build's values before a test reads them: on the JVM it is found as a Java service
 * (androidHostTest's resources), in the browser it is installed as the web shell installs its own. */
expect fun installTestBuild()
