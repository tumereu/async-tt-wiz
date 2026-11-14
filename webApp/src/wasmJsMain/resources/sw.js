// ATW's service worker (docs/features/web.md WEB-052, WEB-054), served at /sw.js for the whole site. It shows the
// notifications the server sends through the browser's push service and opens their game or pod when clicked. It caches
// nothing: the app's files are cached for good by the browser under their build's folder, and there is no offline play.

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

/** The site's open tabs. */
const tabs = () => self.clients.matchAll({ type: 'window', includeUncontrolled: true });

/** Whether [tab] shows the app address [path] (a game, or a pod on any of its tabs). */
const shows = (tab, path) => {
    const at = new URL(tab.url).pathname.replace(/\/$/, '');
    return at === path || (path.indexOf('/pods/') === 0 && at.indexOf(path + '/') === 0);
};

// WEB-052: a notice from the server (WebPushNotice): shown, replacing the earlier one of its game, pod or invitation, unless
// that game or pod is open in a tab in front
self.addEventListener('push', (event) => {
    let notice = null;
    try { notice = event.data ? event.data.json() : null; } catch (e) { notice = null; }
    if (!notice || typeof notice.title !== 'string' || typeof notice.path !== 'string') return;
    event.waitUntil(tabs().then((open) => {
        if (open.some((tab) => tab.visibilityState === 'visible' && tab.focused !== false && shows(tab, notice.path))) return undefined;
        return self.registration.showNotification(notice.title, {
            body: notice.body || undefined,
            tag: notice.tag,
            renotify: true,
            icon: '/icons/icon-192.png',
            badge: '/icons/badge-96.png',
            timestamp: typeof notice.time === 'number' ? notice.time : undefined,
            data: { path: notice.path },
        });
    }));
});

// WEB-052: a click opens its game or pod in the tab already open (brought to the front; the app moves there itself) or a
// new one
self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const path = (event.notification.data && typeof event.notification.data.path === 'string') ? event.notification.data.path : '/';
    event.waitUntil(tabs().then((open) => {
        const tab = open.find((candidate) => shows(candidate, path)) || open.find((candidate) => candidate.focused) || open[0];
        if (!tab) return self.clients.openWindow(path);
        return tab.focus().then((focused) => (focused || tab).postMessage({ atw: 'open', path }));
    }));
});
