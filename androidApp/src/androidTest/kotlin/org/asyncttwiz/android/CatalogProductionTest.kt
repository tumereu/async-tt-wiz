package org.asyncttwiz.android

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.asyncttwiz.storage.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real provider network/parser/AndroidSqliteDriver in isolated PUBLIC test files, not game/action injection. */
class CatalogProductionTest {
    @Test fun actualAndroidCatalogInstallQueryAndReopen() {
        check(BuildConfig.DEBUG && BuildConfig.CATALOG_TEST_ORIGIN.isNotEmpty())
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val owned = File(original.noBackupFilesDir, "catalog-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val context = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = owned
        }
        try {
            val repository = AndroidCatalogStorage.repository(context)
            val source = HttpCatalogSource(BuildConfig.CATALOG_BASE, BuildConfig.CATALOG_TEST_ORIGIN)
            val metadata = source.metadata()
            val installed = CatalogInstaller(AndroidCatalogStorage.directory(context), repository, source).install(metadata, source.size())
            assertTrue(installed.records > 0)
            assertEquals(installed, AndroidCatalogStorage.repository(context).metadata())
            println("PASS actual Android catalog driver/network install, atomic publication, exact metadata reopen: $installed")
        } finally {
            owned.walkBottomUp().forEach { check(it.delete()) }
        }
    }
}
