package org.asyncttwiz.android

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.asyncttwiz.protocol.ActivityPushHint

/** Optional, explicitly configured SDK adapter. Never accept auto-displayed notification payloads. */
class ActivityPushService : FirebaseMessagingService() {
    override fun handleIntent(intent: android.content.Intent) {
        // Firebase's default background path can auto-display before onMessageReceived.
        // Reject notification-shaped payloads BEFORE entering that SDK path.
        if (!ActivityPushConfiguration.enabled || intent.extras?.keySet()?.any {
                it.startsWith("gcm.n.") || it.startsWith("gcm.notification.")
            } == true) return
        super.handleIntent(intent)
    }
    override fun onNewToken(token: String) {
        if (ActivityPushConfiguration.enabled) AndroidActivityDelivery.get(this).pushToken(token)
    }
    override fun onMessageReceived(message: RemoteMessage) {
        if (!ActivityPushConfiguration.enabled || message.notification != null) return
        val data = message.data
        if (data.keys != setOf("generation", "gameId", "activityId", "identity")) return
        AndroidActivityDelivery.get(this).push(ActivityPushHint(data.getValue("generation"), data.getValue("gameId"),
            data.getValue("activityId"), data.getValue("identity")))
    }
    companion object {
        @JvmStatic fun configure(context: Context) {
            // Failure/unavailable Play services leaves WorkManager polling intact. No startup gate.
            runCatching {
                if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FCM_APPLICATION_ID).setProjectId(BuildConfig.FCM_PROJECT)
                    .setApiKey(BuildConfig.FCM_API_KEY).setGcmSenderId(BuildConfig.FCM_SENDER).build())
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    AndroidActivityDelivery.get(context).pushToken(token)
                }
            }
        }
    }
}
