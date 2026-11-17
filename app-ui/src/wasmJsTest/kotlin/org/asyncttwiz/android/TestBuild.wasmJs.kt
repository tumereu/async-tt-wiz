package org.asyncttwiz.android

import org.asyncttwiz.appui.installBuildInfo

actual fun installTestBuild() = installBuildInfo(TestBuildInfo())
