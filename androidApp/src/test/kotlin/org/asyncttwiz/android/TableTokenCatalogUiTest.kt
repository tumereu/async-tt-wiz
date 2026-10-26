package org.asyncttwiz.android

import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableTokenCatalogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun productionTableMountSharesStartupSnapshotAndRefreshesOpenDialogAfterImports() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // TabletopApplication has started the real process source, with no installed records.
        val source = AndroidTokenCatalog.source(context)
        runBlocking { withTimeout(5_000) { source.snapshots.first { it != null } } }
        assertTrue(source.cachedError()!!.contains("not installed"))
        val play = LiveTableFixture.menuPlay()
        val sent = mutableListOf<PlayAction>()
        val controller = TokenLinkController({ play }) { sent += it; true }
        controller.catalog()
        var mount: TableTokenCatalog? = null
        compose.setContent { ScreenTheme {
            // Exactly the mount used by TableScreen, without injecting a test search source.
            val catalog = rememberTableTokenCatalog("a", play.gameId, play.id, 0,
                CatalogUiState(busy = true))
            SideEffect { mount = catalog }
            TokenCatalogDialog(controller, emptyList(), loading = catalog.loading, catalogError = catalog.error,
                catalogIdentity = catalog.identity, searchSource = catalog.source)
        } }
        compose.onNodeWithText(CatalogNotInstalledException().message!!).assertIsDisplayed()
        assertNoLoading()
        compose.onNodeWithContentDescription("Create Blank token").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertSame(source, mount!!.source)
            assertEquals(listOf<PlayAction>(PlayAction.CreateToken(TokenCatalog.blank)), sent)
        }

        publish(context, "Food")
        compose.runOnIdle { controller.catalog() }
        compose.onNodeWithContentDescription("Create Food token").assertIsDisplayed()
        assertNoLoading()
        var generation: String? = null
        compose.runOnIdle { generation = mount!!.identity.installedGeneration; assertNotNull(generation) }

        // Same provider metadata, different installed file. An already open dialog must update.
        publish(context, "Treasure")
        compose.waitUntil(5_000) { mount?.identity?.installedGeneration != generation }
        compose.onNodeWithContentDescription("Create Treasure token").assertIsDisplayed()
        compose.onNodeWithContentDescription("Create Food token").assertDoesNotExist()
        assertNoLoading()
        compose.runOnIdle { controller.close() }
        compose.runOnIdle { controller.catalog() }
        compose.onNodeWithContentDescription("Create Treasure token").assertIsDisplayed()
        assertNoLoading()
    }

    private fun publish(context: Context, name: String) {
        // Real Android repository + importer, just without a network download.
        runBlocking(Dispatchers.IO) {
            val metadata = CatalogMetadata("2026-10-26", "5.2.2")
            val json = """{"meta":{"date":"${metadata.date}","version":"${metadata.version}"},"data":{"SYN":{"cards":[],"tokens":[{"uuid":"00000000-0000-4000-8000-000000000001","name":"$name","number":"1","type":"Token Artifact","text":"","colors":[]}]}}}"""
            AndroidCatalogStorage.repository(context).stage { MtgJsonCatalog().import(json.byteInputStream(), it, metadata) }
        }
        // The same non-blocking publication hook as CatalogViewModel, invoked on the main thread.
        lateinit var refresh: Job
        compose.runOnIdle { refresh = AndroidTokenCatalog.refresh(context) }
        runBlocking { withTimeout(5_000) { refresh.join() } }
        assertEquals(listOf(name), AndroidTokenCatalog.source(context).cached()!!.definitions.map { it.definition.name })
    }

    private fun assertNoLoading() {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
    }
}
