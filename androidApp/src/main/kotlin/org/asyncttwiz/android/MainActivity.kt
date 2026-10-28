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
import androidx.compose.ui.draw.drawWithContent
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
    private val updates get() = ViewModelProvider(this)[AppUpdateViewModel::class.java]
    // ComponentActivity result APIs; the old Fragment that Firebase pulls in transitively is never used here.
    @android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.permissionReturned(packageManager.canRequestPackageInstalls())
    }
    @android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private fun installUpdate(apk: java.io.File) {
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                updates.awaitingPermission(true)
                installPermission.launch(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:$packageName")))
            } else {
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.updates", apk)
                installer.launch(android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        } catch (_: RuntimeException) { updates.awaitingPermission(false); updates.installerFailed() }
    }
    internal fun gameInput() = ViewModelProvider(this)[AuthViewModel::class.java].heartbeat.input()
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        gameInput()
        return super.dispatchTouchEvent(event)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        gameInput()
        return super.dispatchKeyEvent(event)
    }
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        gameInput()
        return super.dispatchGenericMotionEvent(event)
    }
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
        if (hasFocus) fullscreen()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        fullscreen()
        updates.foreground()
        updates.resumePermission(packageManager.canRequestPackageInstalls())
        ViewModelProvider(this)[AuthViewModel::class.java].catalog.foreground()
        val auth = ViewModelProvider(this)[AuthViewModel::class.java]
        auth.controller.foreground()
        // AuthViewModel owns socket/reconnect maintenance across configuration changes.
        // Resume only nudges that owner; it must never create a UI-owned connection.
        auth.lobby.foreground()
        auth.activity.foreground()
    }

    override fun onPause() {
        resumed = false
        ViewModelProvider(this)[AuthViewModel::class.java].lobby.background()
        AndroidActivityDelivery.get(this).display(ActivityScreen.NONE, false)
        ViewModelProvider(this)[AuthViewModel::class.java].heartbeat.display(null, null)
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
                LaunchedEffect(auth.lobby.state.connectionMessage) {
                    if (auth.lobby.state.connectionMessage == "Update the app to continue this game") updates.requireForRules()
                }
                if (updates.required || updates.initialCheck) {
                    AppUpdateScreen(updates, ::installUpdate)
                    return@MaterialTheme
                }
                val state = auth.controller.state
                val token = auth.controller.sessionToken()
                // Read in composition, not inside SideEffect, so opening a game/pod or resuming
                // reruns the effect below and clears that screen's notifications.
                val shown by remember { derivedStateOf {
                    ActivityScreen.of(auth.controller.state.destination, auth.lobby.state.selected, auth.pods.state)
                } }
                val activityScreen = shown
                val foreground = resumed
                SideEffect {
                    if (auth.lobby.state.detail == null) DebugJank.state("screen" to state.destination.name.lowercase())
                    if (!state.restoring && !state.busy) {
                        DebugCrashes.authenticated(if (state.account != null) token else null)
                        if (auth.activity.session(state.account?.accountId, token)) auth.activity.foreground()
                    }
                    val gameScreen = activityScreen.game
                    auth.activity.display(activityScreen, foreground)
                    auth.heartbeat.display(gameScreen?.takeIf { foreground }, token)
                    if (state.account != null) auth.activity.rememberPods(auth.pods.state.pods)
                    val route = notificationRoute
                    if (route != null && !state.restoring && !state.busy) {
                        notificationRoute = null
                        auth.openActivity(route)
                    }
                }
                val catalogBlocked = catalog.state.busy || catalog.needsInitialDownloadConsent || catalog.promptPolicy.shouldPrompt(catalog.state)
                val releaseNotes = remember { ReleaseNotePreferences(this@MainActivity) }
                var whatsNew by remember { mutableStateOf(releaseNotes.unseen()) }
                // Only on the shell (never over an open game or pod), and not under another startup dialog.
                val showWhatsNew = whatsNew.isNotEmpty() && state.account != null && !state.restoring && !state.busy &&
                    notificationRoute == null && activityScreen.game == null && activityScreen.pod == null && !catalogBlocked
                if (showWhatsNew) WhatsNewDialog(whatsNew) { releaseNotes.markSeen(whatsNew); whatsNew = emptyList() }
                val permissionPrefs = remember { getSharedPreferences("notification-permission", 0) }
                var asked by remember { mutableStateOf(permissionPrefs.getBoolean("asked", false)) }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { auth.activity.foreground() }
                if (whatsNew.isEmpty() && ActivityPermissionPrompt.shouldPrompt(android.os.Build.VERSION.SDK_INT, state.account != null,
                    state.destination, auth.lobby.state.selected, asked, catalogBlocked,
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
                    fun dismiss() { asked = true; permissionPrefs.edit().putBoolean("asked", true).apply() }
                    ScreenDialog({ dismiss() }, title = "Invitation, game and draft alerts", actions = {
                        DialogCancelButton("Not now", { dismiss() })
                        DialogMainButton("Enable alerts", { dismiss(); permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) })
                    }) {
                        Text("Enable Android notifications for invitations, opponent activity and drafts? You can change each channel in Android settings.")
                    }
                }
                Surface(Modifier.fillMaxSize().then(if (BuildConfig.DEBUG) Modifier.drawWithContent {
                    drawContent()
                    // A completed content draw, not Choreographer's pre-draw frame timestamp.
                    DebugLatency.drawn()
                } else Modifier)) {
                    if (storage.state == StorageState.Ready) {
                        AccountEntry(auth.controller, auth.reset, catalog, auth.deckImport, auth.decks, auth.lobby, auth.cubes, exit = ::finish, pods = auth.pods)
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
