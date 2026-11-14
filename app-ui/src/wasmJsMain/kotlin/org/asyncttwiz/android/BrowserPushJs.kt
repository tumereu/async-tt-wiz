@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.protocol.ActivityWire
import org.asyncttwiz.protocol.RegisterInstallation
import org.asyncttwiz.protocol.WebPushSubscription

// The browser's notifications, push subscription and install prompt (web client step 7; WEB-050 to WEB-055), as small
// JavaScript functions over the service worker index.html registers (/sw.js) and the install prompt it keeps.

/** The notification permission: 'granted', 'denied', 'default', or 'unsupported' where the page can't get push at all. */
private fun jsNotificationPermission(): String = js("""(() => {
    if (typeof Notification === 'undefined' || !('serviceWorker' in navigator) || typeof PushManager === 'undefined' || !window.isSecureContext) return 'unsupported';
    return Notification.permission;
})()""")

private fun jsRequestPermission(): Promise<JsString> = js("""(async () => {
    try { return await Notification.requestPermission(); } catch (e) { return Notification.permission; }
})()""")

/** This browser's push subscription for [key] (base64url) as JSON {endpoint, p256dh, auth}, "" when it can't make one
 * (the reason goes to the console). */
private fun jsPushSubscribe(key: String): Promise<JsString> = js("""(async () => {
    try {
        let registration = await navigator.serviceWorker.getRegistration('/');
        if (!registration) registration = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
        registration = await Promise.race([navigator.serviceWorker.ready,
            new Promise((_, reject) => setTimeout(() => reject(new Error('The service worker did not start')), 10000))]);
        const padded = key.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - key.length % 4) % 4);
        const serverKey = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
        let subscription = await registration.pushManager.getSubscription();
        if (subscription) {
            const own = subscription.options && subscription.options.applicationServerKey;
            const same = own && new Uint8Array(own).length === serverKey.length && new Uint8Array(own).every((b, i) => b === serverKey[i]);
            if (!same) { await subscription.unsubscribe(); subscription = null; }
        }
        if (!subscription) subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: serverKey });
        const text = (buffer) => btoa(String.fromCharCode(...new Uint8Array(buffer))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        return JSON.stringify({ endpoint: subscription.endpoint, p256dh: text(subscription.getKey('p256dh')), auth: text(subscription.getKey('auth')) });
    } catch (e) {
        console.warn('ATW push subscription failed: ' + e);
        return '';
    }
})()""")

private fun jsPushUnsubscribe(): Promise<JsAny?> = js("""(async () => {
    try {
        const registration = await navigator.serviceWorker.getRegistration('/');
        const subscription = registration && await registration.pushManager.getSubscription();
        if (subscription) await subscription.unsubscribe();
    } catch (e) { console.warn('ATW push unsubscribe failed: ' + e); }
    return null;
})()""")

/** Closes this site's notifications whose tag is one of [tags] (comma-separated) or starts with [prefix] ("" for none). */
private fun jsCloseNotifications(tags: String, prefix: String): Unit = js("""{
    if ('serviceWorker' in navigator) navigator.serviceWorker.getRegistration('/').then((registration) => {
        if (!registration || !registration.getNotifications) return;
        const wanted = tags.split(',').filter((t) => t.length > 0);
        registration.getNotifications().then((list) => list.forEach((n) => {
            if (wanted.indexOf(n.tag) >= 0 || (prefix.length > 0 && n.tag.indexOf(prefix) === 0)) n.close();
        }));
    }).catch(() => {});
}""")

/** 'installed' (running as the installed app), 'prompt' (index.html kept the browser's install prompt), 'ios' or 'menu'. */
private fun jsInstallState(): String = js("""(() => {
    const standalone = (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) || navigator.standalone === true;
    if (standalone || globalThis.atwInstalled === true) return 'installed';
    if (globalThis.atwInstallPrompt) return 'prompt';
    const ios = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    return ios ? 'ios' : 'menu';
})()""")

/** The browser's install prompt (once: a browser offers it again later); whether the site was installed. */
private fun jsPromptInstall(): Promise<JsBoolean> = js("""(async () => {
    const prompt = globalThis.atwInstallPrompt;
    if (!prompt) return false;
    globalThis.atwInstallPrompt = null;
    try {
        prompt.prompt();
        const choice = await prompt.userChoice;
        if (choice && choice.outcome === 'accepted') { globalThis.atwInstalled = true; return true; }
    } catch (e) { console.warn('ATW install prompt failed: ' + e); }
    return false;
})()""")

/** Calls [changed] when the browser's install state changes (its prompt offered, the site installed). */
private fun jsOnInstallChange(changed: () -> Unit): Unit = js("""{
    window.addEventListener('atw-install', () => changed());
}""")

/** Calls [open] with the app address a notification click asks this tab to open (the service worker's message). */
internal fun jsOnNotificationOpen(open: (String) -> Unit): Unit = js("""{
    if ('serviceWorker' in navigator) navigator.serviceWorker.addEventListener('message', (event) => {
        const data = event.data;
        if (data && data.atw === 'open' && typeof data.path === 'string' && data.path.charAt(0) === '/') open(data.path);
    });
}""")

/** WEB-055: the tab's title. */
internal fun jsSetTitle(title: String): Unit = js("{ if (document.title !== title) document.title = title; }")

/** The browser's side of [WebNotifications]: the functions above. */
internal class BrowserPlatformJs : BrowserPlatform {
    override fun permission(): BrowserPermission = permissionOf(jsNotificationPermission())

    override suspend fun requestPermission(): BrowserPermission = permissionOf(jsRequestPermission().await<JsString>().toString())

    override suspend fun subscribe(serverKey: String): WebPushSubscription? {
        val text = jsPushSubscribe(serverKey).await<JsString>().toString()
        if (text.isEmpty()) return null
        val json = Json.parseToJsonElement(text).jsonObject
        val subscription = WebPushSubscription(json.getValue("endpoint").jsonPrimitive.content, json.getValue("p256dh").jsonPrimitive.content,
            json.getValue("auth").jsonPrimitive.content)
        if (!ActivityWire.valid(subscription)) {
            AppLog.warn(WebNotifications.TAG, "The browser's push subscription isn't one the server takes (${subscription.endpoint.substringBefore('/', "")})")
            return null
        }
        return subscription
    }

    override suspend fun unsubscribe() { jsPushUnsubscribe().await<JsAny?>() }

    override fun install(): BrowserInstall = when (jsInstallState()) {
        "installed" -> BrowserInstall.Installed
        "prompt" -> BrowserInstall.Prompt
        "ios" -> BrowserInstall.Ios
        else -> BrowserInstall.Menu
    }

    override suspend fun promptInstall(): Boolean = jsPromptInstall().await<JsBoolean>().toBoolean()

    /** LOB-078, DRF-138 (WEB-052): the notifications of what [screen] shows are closed. */
    fun cancelShown(screen: ActivityScreen) {
        val tags = listOfNotNull(screen.game?.let { "game:$it" }, screen.pod?.let { "pod:$it" }).joinToString(",")
        val prefix = if (screen.podList) "pod-invite:" else ""
        if (tags.isNotEmpty() || prefix.isNotEmpty()) jsCloseNotifications(tags, prefix)
    }

    fun onInstallChange(changed: () -> Unit) = jsOnInstallChange(changed)

    private fun permissionOf(value: String): BrowserPermission = when (value) {
        "granted" -> BrowserPermission.Granted
        "denied" -> BrowserPermission.Denied
        "default" -> BrowserPermission.Undecided
        else -> BrowserPermission.Unsupported
    }
}

/** The server's side of [WebNotifications], through the activity API. */
internal class BrowserPushServer(private val origin: String) : WebPushServer {
    override suspend fun key(token: String): String? = ActivityApi(origin).use { it.webPushKey(token) }
    override suspend fun register(token: String, installation: RegisterInstallation) {
        ActivityApi(origin).use { it.register(token, installation) }
    }
    override suspend fun unregister(token: String, installationId: String) {
        ActivityApi(origin).use { it.unregister(token, installationId) }
    }
}
