package org.asyncttwiz.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.UUID

/** Authored/compiled only in this lane; full OS/WorkManager journey belongs to I31. */
@RunWith(AndroidJUnit4::class)
class ActivityRuntimeTest {
    @Test fun coldAndWarmIntentFieldsRetainExactBoundaryIdentityWithoutPreviewContent() {
        val account = UUID.randomUUID().toString(); val binding = UUID.randomUUID().toString()
        val game = UUID.randomUUID().toString(); val group = UUID.randomUUID().toString()
        val intent = Intent("org.asyncttwiz.ACTIVITY").putExtra("account", account).putExtra("binding", binding)
            .putExtra("kind", "GAME").putExtra("game", game).putExtra("identity", group)
        for (delivery in listOf(intent, Intent(intent))) {
            val route = ActivityRoute.parse(delivery.getStringExtra("account"), delivery.getStringExtra("binding"),
                delivery.getStringExtra("kind"), delivery.getStringExtra("game"), delivery.getStringExtra("identity"))
            assertNotNull(route); assertEquals(group, route!!.identity); assertEquals(binding, route.binding)
            assertEquals(setOf("account", "binding", "kind", "game", "identity"), delivery.extras!!.keySet())
        }
    }
}
