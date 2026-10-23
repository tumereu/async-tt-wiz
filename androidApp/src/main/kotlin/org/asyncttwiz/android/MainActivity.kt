package org.asyncttwiz.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.storage.StorageOpenFailure

/** The only normal application entry point. Storage failure still gates account entry. */
class MainActivity : ComponentActivity() {
    private var resumed by mutableStateOf(false)
    private var notificationRoute by mutableStateOf<ActivityRoute?>(null)
    private fun consumeActivityIntent(intent: android.content.Intent?) {
        if (intent?.action != "org.asyncttwiz.ACTIVITY") return
        val route = ActivityRoute.parse(intent.getStringExtra("account"), intent.getStringExtra("binding"),
            intent.getStringExtra("kind"), intent.getStringExtra("game"), intent.getStringExtra("identity"))
        setIntent(android.content.Intent(android.content.Intent.ACTION_MAIN).setClass(this, MainActivity::class.java))
        notificationRoute = route
    }
    private fun consumeResetIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW) return
        val link = intent.dataString
        // Retain only the parsed volatile controller secret, never the raw URI/extras in Activity state.
        setIntent(android.content.Intent(android.content.Intent.ACTION_MAIN).setClass(this, MainActivity::class.java))
        ViewModelProvider(this)[AuthViewModel::class.java].reset.open(link)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        consumeActivityIntent(intent)
        consumeResetIntent(intent)
    }
    private fun fullscreen() {
        // API29 can retain a visible status bar after rotation despite the decor FULLSCREEN bit.
        if (android.os.Build.VERSION.SDK_INT < 30)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // IME/system dialogs may reveal bars. Returning to the app restores immersive presentation.
        if (hasFocus) window.decorView.post { fullscreen() }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        window.decorView.post { fullscreen() }
        ViewModelProvider(this)[AuthViewModel::class.java].catalog.foreground()
        val auth = ViewModelProvider(this)[AuthViewModel::class.java]
        if (auth.controller.state.destination == ShellDestination.DECKS) auth.decks.sync()
        // AuthViewModel owns socket/reconnect maintenance across configuration changes.
        // Resume only nudges that owner; it must never create a UI-owned connection.
        if (auth.controller.state.account != null) auth.lobby.foreground()
        auth.activity.foreground()
    }

    override fun onPause() {
        resumed = false
        AndroidActivityDelivery.get(this).display(null, false)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = ViewModelProvider(this)[StorageViewModel::class.java]
        val auth = ViewModelProvider(this)[AuthViewModel::class.java]
        val catalog = auth.catalog
        consumeActivityIntent(intent)
        consumeResetIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        fullscreen()
        setContent {
            MaterialTheme {
                val state = auth.controller.state
                val token = auth.controller.sessionToken()
                SideEffect {
                    if (!state.restoring && !state.busy) {
                        if (auth.activity.session(state.account?.accountId, token)) auth.activity.foreground()
                    }
                    val displayed = ActivityDisplay.game(state.destination == ShellDestination.GAMES,
                        auth.lobby.state.selected, auth.lobby.state.detail)
                    auth.activity.display(displayed, resumed)
                    val route = notificationRoute
                    if (route != null && !state.restoring && !state.busy) {
                        notificationRoute = null
                        auth.openActivity(route)
                    }
                }
                val permissionPrefs = remember { getSharedPreferences("notification-permission", 0) }
                var asked by remember { mutableStateOf(permissionPrefs.getBoolean("asked", false)) }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { auth.activity.foreground() }
                if (android.os.Build.VERSION.SDK_INT >= 33 && state.account != null &&
                    state.destination == ShellDestination.GAMES && auth.lobby.state.selected == null && !asked &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    fun dismiss() { asked = true; permissionPrefs.edit().putBoolean("asked", true).apply() }
                    AlertDialog(onDismissRequest = { dismiss() }, title = { Text("Invitation and game activity alerts") },
                        text = { Text("Enable Android notifications for new invitations and opponent activity? You can change the two channels in Android settings.") },
                        confirmButton = { TextButton(onClick = { dismiss(); permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable alerts") } },
                        dismissButton = { TextButton(onClick = { dismiss() }) { Text("Not now") } })
                }
                Surface(Modifier.fillMaxSize()) {
                    if (storage.state == StorageState.Ready) {
                        AccountEntry(auth.controller, auth.reset, catalog, auth.deckImport, auth.decks, auth.lobby)
                    }
                    else Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Async Tabletop Wizards", style = MaterialTheme.typography.headlineSmall)
                        Text("Specialized tabletop simulator for asynchronous wizardry")
                        when (val state = storage.state) {
                            StorageState.Opening -> Text("Opening local storage…")
                            StorageState.Ready -> Unit
                            is StorageState.Failed -> {
                                Text(if (state.reason == StorageOpenFailure.UNSUPPORTED_VERSION)
                                    "Local data needs a newer app. Update the app without uninstalling."
                                else "Local storage could not be opened. Your data has not been erased.")
                                Button(onClick = storage::retry) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }
}
