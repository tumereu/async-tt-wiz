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

/** Normal application entry point. Account entry replaces this scaffold in I02. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = ViewModelProvider(this)[StorageViewModel::class.java]
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Async Tabletop Wizards", style = MaterialTheme.typography.headlineSmall)
                        Text("Specialized tabletop simulator for asynchronous wizardry")
                        when (val state = storage.state) {
                            StorageState.Opening -> Text("Opening local storage…")
                            StorageState.Ready -> Unit // Still the normal scaffold; account entry is I02.
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
