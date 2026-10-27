package org.asyncttwiz.android

import android.content.Context
import androidx.work.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.asyncttwiz.protocol.*
import kotlinx.serialization.encodeToString

object ActivityPushConfiguration {
    val enabled get() = BuildConfig.FCM_APPLICATION_ID.isNotEmpty() && BuildConfig.FCM_PROJECT.isNotEmpty() &&
        BuildConfig.FCM_API_KEY.isNotEmpty() && BuildConfig.FCM_SENDER.isNotEmpty()
    fun configure(context: Context) {
        if (!enabled) return
        runCatching { Class.forName("org.asyncttwiz.android.ActivityPushService")
            .getMethod("configure", Context::class.java).invoke(null, context) }
    }
}

class ActivityWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val engine = AndroidActivityDelivery.get(applicationContext)
        val binding = inputData.getString("binding") ?: return@withContext Result.success()
        val failure = engine.poll(binding, inputData.getString("pushGeneration"))
        if (failure?.transient == true) {
            val attempt = inputData.getInt("attempt", 0)
            ActivityPolling.retrySeconds(attempt, failure.retryAfter)?.let { seconds ->
                engine.retry(binding, attempt + 1, seconds)
            }
        }
        Result.success() // Permanent/expired owners stop; periodic work is independent of bounded retries.
    }
}

/** Process-wide owner fence shared by UI, WorkManager and optional FCM callbacks.
 * Persist no credential here; workers load the existing Keystore-protected session. */
class AndroidActivityDelivery private constructor(private val context: Context) {
    private val lock = Any()
    private val fetchLock = Any()
    private val preferences = context.getSharedPreferences("activity-delivery", Context.MODE_PRIVATE)
    private val podNames = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val notifications = ActivityNotifications(context, podNames::get)
    private val jobs get() = WorkManager.getInstance(context)
    private var owner: ActivityOwner? = preferences.getString("account", null)?.let { account ->
        preferences.getString("binding", null)?.let { ActivityOwner(account, it) }
    }
    private var ownerFingerprint = preferences.getString("credentialFingerprint", null)
    private val delivery = ActivityDelivery(object : ActivityWindowStore {
        override fun cursor() = preferences.getString("cursor", null)
        override fun watermark(key: String): ActivityWatermark? {
            val identity = preferences.getString("identity:$key", null) ?: return null
            return ActivityWatermark(preferences.getLong("watermark:$key", 0), identity,
                if (preferences.contains("move:$key")) preferences.getBoolean("move:$key", false) else null)
        }
        override fun save(cursor: String, marks: Map<String, ActivityWatermark>): Boolean = preferences.edit().apply {
            putString("cursor", cursor)
            marks.forEach { (key, mark) -> putLong("watermark:$key", mark.activity); putString("identity:$key", mark.identity)
                mark.yourMove?.let { putBoolean("move:$key", it) } }
        }.commit()
        override fun pending(): Map<String, PendingActivity> = preferences.getString("pending", null)?.let {
            ActivityWire.json.decodeFromString<Map<String, PendingActivity>>(it)
        } ?: emptyMap()
        override fun saveWindow(cursor: String, marks: Map<String, ActivityWatermark>, pending: Map<String, PendingActivity>): Boolean =
            preferences.edit().apply {
                putString("cursor", cursor); putString("pending", ActivityWire.json.encodeToString(pending))
                marks.forEach { (key, mark) -> putLong("watermark:$key", mark.activity); putString("identity:$key", mark.identity)
                    mark.yourMove?.let { putBoolean("move:$key", it) } }
            }.commit()
    }) { notice -> owner?.let { notifications.post(it, notice) } }
    @Volatile var authenticationRequired: ((String) -> Unit)? = null
    @Volatile var activityHints: ((List<ActivityHint>) -> Unit)? = null
    fun rememberPods(pods: List<PodSummary>) { pods.forEach { podNames[it.id] = it.name } }
    init { delivery.bind(owner); notifications.channels() }

    fun session(account: String?, token: String?): Boolean = synchronized(lock) {
        if (account == null || token == null) { clear(); return false }
        val fingerprint = fingerprint(token)
        if (owner?.accountId == account && ownerFingerprint == fingerprint) return false
        clear()
        val next = ActivityOwner(account, UUID.randomUUID().toString())
        check(preferences.edit().putString("account", account).putString("binding", next.binding)
            .putString("credentialFingerprint", fingerprint).commit())
        ownerFingerprint = fingerprint; owner = next; delivery.bind(next)
        periodic(next.binding)
        ActivityPushConfiguration.configure(context) // A no-op in the normal non-FCM configuration.
        true
    }
    fun clear(expectedBinding: String? = null): Unit = synchronized(lock) {
        if (expectedBinding != null && expectedBinding != owner?.binding) return
        // Fence already-running callbacks first, not just scheduler cancellation.
        delivery.bind(null); owner = null; ownerFingerprint = null
        podNames.clear()
        notifications.cancelAll(); jobs.cancelAllWorkByTag(TAG)
        check(preferences.edit().clear().commit())
    }
    fun display(screen: ActivityScreen, foreground: Boolean) = synchronized(lock) {
        delivery.display(screen, foreground)
        if (foreground) owner?.let { notifications.cancelShown(it, screen) }
    }
    fun foreground() = synchronized(lock) { owner?.let { enqueue(it.binding) } }
    fun accepts(account: String?, binding: String?) = synchronized(lock) { account != null && binding != null && owner == ActivityOwner(account, binding) }
    fun rejected(token: String) = synchronized(lock) { if (ownerFingerprint == fingerprint(token)) clear() }
    fun push(hint: ActivityPushHint): Unit = synchronized(lock) {
        if (!ActivityWire.valid(hint) || hint.generation != preferences.getString("registration", null)) return
        owner?.let { enqueue(it.binding, hint.generation) }
    }
    fun pushToken(token: String): Unit = synchronized(lock) {
        if (token.length !in 1..4096) return
        val current = owner ?: return
        check(preferences.edit().putString("pushToken", token).commit())
        enqueue(current.binding)
    }
    /** One bounded page batch. Foreground is ordinary account refresh, not a notification dependency. */
    fun poll(binding: String, pushGeneration: String? = null): ActivityFetchFailure? = synchronized(fetchLock) {
        val expected = synchronized(lock) {
            owner?.takeIf { it.binding == binding && (pushGeneration == null || pushGeneration == preferences.getString("registration", null)) }
        } ?: return null
        val stored = ProtectedSessionStore(context, BuildConfig.ACCOUNT_ORIGIN).load()
        val token = (stored as? StoredSession.Present)?.token ?: run { clear(binding); return null }
        if (synchronized(lock) { owner != expected || ownerFingerprint != fingerprint(token) }) return null
        try {
            runBlocking { ActivityApi(BuildConfig.ACCOUNT_ORIGIN).use { api ->
                val installation = context.getSharedPreferences("installation", 0).let { prefs ->
                    synchronized(INSTALLATION_LOCK) { prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
                        check(prefs.edit().putString("id", it).commit())
                    } }
                }
                val pushToken = synchronized(lock) { preferences.getString("pushToken", null) }
                val refresh = ActivityRefresh({ delivery.current(expected) }, { delivery.cursor(expected) }, { page ->
                    synchronized(lock) {
                        if (owner != expected) false else {
                            activityHints?.invoke(page.entries)
                            delivery.apply(expected, page, notifications::allowed)
                        }
                    }
                })
                val more = refresh.run(register = {
                    val registration = api.register(token, RegisterInstallation(installation, binding, pushToken))
                    synchronized(lock) {
                        if (owner == expected) check(preferences.edit().putString("registration", registration.generation).commit())
                    }
                }, fetch = { cursor -> api.page(token, cursor) })
                synchronized(lock) {
                    if (owner == expected) delivery.nextDue(expected)?.let { due -> window(expected.binding, due) }
                }
                // Bounded execution: continue a long account collection with another network-constrained job.
                if (more) retry(binding, 0, 30)
            } }
            null
        } catch (failure: ActivityFetchFailure) {
            if (failure.status == 401) { clear(binding); authenticationRequired?.invoke(token) }
            failure
        } catch (_: Exception) { ActivityFetchFailure(0) }
    }
    private fun periodic(binding: String) {
        jobs.enqueueUniquePeriodicWork("activity-periodic", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ActivityWorker>(ActivityPolling.PERIOD_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints()).setInputData(workDataOf("binding" to binding)).addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    private fun enqueue(binding: String, pushGeneration: String? = null) {
        jobs.enqueueUniqueWork("activity-refresh:$binding", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ActivityWorker>().setConstraints(constraints()).addTag(TAG)
                .setInputData(workDataOf("binding" to binding, "pushGeneration" to pushGeneration)).build())
    }
    private fun window(binding: String, due: Long) {
        // Durable local deadline, shared by push-triggered and periodic polls. No exact alarm/SLA.
        jobs.enqueueUniqueWork("activity-window:$binding:$due", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ActivityWorker>().setConstraints(constraints()).addTag(TAG)
                .setInitialDelay((due - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("binding" to binding)).build())
    }
    fun retry(binding: String, attempt: Int, seconds: Long): Unit = synchronized(lock) {
        if (owner?.binding != binding) return
        jobs.enqueueUniqueWork("activity-retry:$binding:$attempt", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ActivityWorker>().setConstraints(constraints()).addTag(TAG)
                .setInitialDelay(seconds, TimeUnit.SECONDS).setInputData(workDataOf("binding" to binding, "attempt" to attempt)).build())
        Unit
    }
    companion object {
        const val TAG = "account-activity"
        private val INSTALLATION_LOCK = Any()
        @Volatile private var instance: AndroidActivityDelivery? = null
        fun get(context: Context): AndroidActivityDelivery = instance ?: synchronized(this) {
            instance ?: AndroidActivityDelivery(context.applicationContext).also { instance = it }
        }
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        private fun fingerprint(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
