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
import org.asyncttwiz.storage.StorageOpenFailure

/** The only normal application entry point. Storage failure still gates account entry. */
class MainActivity : ComponentActivity() {
    private fun consumeResetIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW) return
        val link = intent.dataString
        // Retain only the parsed volatile controller secret, never the raw URI/extras in Activity state.
        setIntent(android.content.Intent(android.content.Intent.ACTION_MAIN).setClass(this, MainActivity::class.java))
        ViewModelProvider(this)[AuthViewModel::class.java].reset.open(link)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
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
        window.decorView.post { fullscreen() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = ViewModelProvider(this)[StorageViewModel::class.java]
        val auth = ViewModelProvider(this)[AuthViewModel::class.java]
        consumeResetIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        fullscreen()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (storage.state == StorageState.Ready) AccountEntry(auth.controller, auth.reset)
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
