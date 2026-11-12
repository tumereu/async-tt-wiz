# Web client

The app in a browser, served by the ATW server beside its API (one address for both). Every other entry applies to the
web client too unless it is marked "(Android)" or an entry here says otherwise. The phone layout is the Android app in a
browser; a game's table and the desktop layout come later.

### Addresses and Back

- **WEB-001** Every screen has its own address: `/` the menu, `/games`, `/games/<id>` (a game), `/pods`, `/pods/<id>/<tab>` (draft, build, stats or games), `/decks`, `/decks/import`, `/decks/<id>`, `/decks/<id>/edit`, `/cubes/new`, `/cubes/<id>`, `/contacts`, `/settings` and `/stats`. Refreshing a page opens the same screen, and an address opened while signed out opens once you have signed in; an address of no screen opens the menu. (r71)
- **WEB-002** The browser's Back and Forward move between screens, never into dialogs or menus. Opening a screen adds a history entry; returning to the screen you came from (an on-screen Back) goes back an entry instead of adding one, so Back never loops; switching a pod's tabs keeps one entry. Back from a game goes straight to the screen before it, without SYS-050's second Back. (r71)
- **WEB-003** Every screen but the menu has a back arrow at the left of its header: it does what Android's Back does there (closes an open panel or the side menu first, steps back inside a section, such as Games → Choose opponent), and on a screen with nothing to step back from opens the screen it belongs to (a game's Games, a section's menu). (r71)
- **WEB-004** Esc closes the open dialog, popup, panel or side menu, never a screen. On Android Chrome the phone's Back closes them too (where the browser supports it); with none open it is the browser's Back. (r71)

### Loading and updates

- **WEB-005** The first visit shows a loading screen (the wizard hat, "ATW", "Loading Async Tabletop Wizards…" and a moving bar) until the app draws; later visits load from the browser's cache. When the app can't load it says "ATW couldn't load. Check your connection and reload the page." (r71)
- **WEB-006** A tab left open on an older version reloads itself when the server has a newer one (checked when the page opens, when it comes back to the foreground and every 10 minutes): at once on the menu, the Games list or the sign-in page, or as a game is opened; never while a dialog, panel or menu is open or text is being typed. Elsewhere it waits until you reach one of those. After the reload a short note says "ATW was updated", and What's new (LOB-087) follows its usual rules; a first visit counts as a fresh install. (r71)

### Sign-in, card records and sound

- **WEB-007** A game's table doesn't open in the browser yet: opening a started game shows its name, "Games open in the app for now", "Playing in the browser comes soon. Open this game in the Android app." and "Back to Games". Games still being set up open their lobby as in the app. (r71)
- **WEB-008** The "Your move" sound (LOB-146, TRN-075) plays in the browser, also in a background tab, once you have tapped or typed on the page after opening it (browsers allow sound only then). (r71)
- **WEB-009** The card records (LOB-091) download into the browser's own storage, which is asked to keep them (persistent storage), and are used from there on later visits; if the browser deletes them (iOS Safari does after 7 days unvisited unless the site is installed), they download again. Sign-in is kept in the browser too, across reloads, until you log out. (r71)
- **WEB-010** "Import from link" (LOB-061) reads Archidekt decks through the ATW server, since Archidekt answers no browser directly. (r71)
