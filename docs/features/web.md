# Web client

The app in a browser, served by the ATW server beside its API (one address for both). Every other entry applies to the
web client too unless it is marked "(Android)" or an entry here says otherwise. The phone layout is the Android app in a
browser; the desktop layout comes later.

### Addresses and Back

- **WEB-001** Every screen has its own address: `/` the menu, `/games`, `/games/<id>` (a game), `/pods`, `/pods/<id>/<tab>` (draft, build, stats or games), `/decks`, `/decks/import`, `/decks/<id>`, `/decks/<id>/edit`, `/cubes/new`, `/cubes/<id>`, `/contacts`, `/settings` and `/stats`. Refreshing a page opens the same screen, and an address opened while signed out opens once you have signed in; an address of no screen opens the menu. (r71)
- **WEB-002** The browser's Back and Forward move between screens, never into dialogs or menus. Opening a screen adds a history entry; returning to the screen you came from (an on-screen Back) goes back an entry instead of adding one, so Back never loops; switching a pod's tabs keeps one entry. Back from a game goes straight to the screen before it, without SYS-050's second Back. (r71)
- **WEB-003** Every screen but the menu has a back arrow at the left of its header: it does what Android's Back does there (closes an open panel or the side menu first, steps back inside a section, such as Games → Choose opponent), and on a screen with nothing to step back from opens the screen it belongs to (a game's Games, a section's menu). (r71)
- **WEB-004** Esc closes the open dialog, popup, panel or side menu, never a screen, except on a game's table and the draft: there Esc with nothing open leaves the screen, as Back does (a game's table also closes the chat and leaves timeline mode first). On Android Chrome the phone's Back closes them too (where the browser supports it); with none open it is the browser's Back. (r72)

### Loading and updates

- **WEB-005** The first visit shows a loading screen (the wizard hat, "ATW", "Loading Async Tabletop Wizards…" and a moving bar) until the app draws; later visits load from the browser's cache. When the app can't load it says "ATW couldn't load. Check your connection and reload the page." (r71)
- **WEB-006** A tab left open on an older version reloads itself when the server has a newer one (checked when the page opens, when it comes back to the foreground and every 10 minutes): at once on the menu, the Games list or the sign-in page, or as a game is opened; never while a dialog, panel or menu is open or text is being typed. Elsewhere it waits until you reach one of those. After the reload a short note says "ATW was updated", and What's new (LOB-087) follows its usual rules; a first visit counts as a fresh install. (r71)

### Sign-in, card records and sound

- **WEB-007** A game opens in the browser as in the app: a started game's table with the hand, panels, combat band, timeline and chat, also from its address (a refresh opens it again); games still being set up open their lobby, and pods their draft, build, stats and games. Its Back leaves the table at once, without SYS-050's second Back. While the browser works out what you can play from your hand and with your cards, the table stays responsive and the hand and menus show the plays as they arrive. (r72)
- **WEB-008** The "Your move" sound (LOB-146, TRN-075) plays in the browser, also in a background tab, once you have tapped or typed on the page after opening it (browsers allow sound only then). (r71)
- **WEB-009** The card records (LOB-091) download into the browser's own storage, which is asked to keep them (persistent storage), and are used from there on later visits; if the browser deletes them (iOS Safari does after 7 days unvisited unless the site is installed), they download again. Sign-in is kept in the browser too, across reloads, until you log out. (r71)
- **WEB-010** "Import from link" (LOB-061) reads Archidekt decks through the ATW server, since Archidekt answers no browser directly. (r71)

### The table in the browser

- **WEB-011** On a game's table and the draft the browser's own gestures stay off: the page doesn't scroll, bounce or pull to refresh, a pinch, double tap, Ctrl+wheel or Ctrl with + or - doesn't zoom the page, and a long press or right-click shows no browser menu, callout or text selection; the table's own pan, pinch, hold and double tap get every touch. Other screens keep the browser's usual behaviour. (r72)
- **WEB-012** The table's haptic ticks (the move passing to you, TRN-075; a held quick action) vibrate the phone where the browser can (Android's Chrome), once the page has had a tap; elsewhere (iOS, desktops) there is none, and nothing fails. (r72)
