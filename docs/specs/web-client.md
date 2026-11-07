# Web client: desktop and phone browsers (owner, 2026-11-07)

Status: **in discussion, not started.** Roadmap item 2 ("Desktop web client"), started before the Android client is
finished because Automatum shrank the set of player actions. Decisions marked **(owner)** are agreed; everything marked
**(proposal)** is Claude's and waits for the owner. Section 1 is **provisional until the feasibility test** (section 11,
step 0) reports. Wireframes: the "ATW Web Wireframes" design canvas, https://claude.ai/artifact/FhqdRe7Er5PHv86t8u2rXL (private to the
owner): D1–D10 desktop, P1–P4 phone web.
Building a part moves its behaviour into the catalog (new `WEB` area, changed entries listed per part) and deletes the
matching part of this file.

## Why

Players want to play on a computer with a big screen, mouse and keyboard, and friends without Android (iPhone users)
can't play at all. A browser client served by our own server reaches both. Today's Android app stays the reference: the
web client is the same app in a browser, with a desktop layout made for mouse, keyboard and a large screen.

## Decisions so far (owner, 2026-11-07)

1. A Kotlin Multiplatform single-page app, served by the same Spring Boot server as the API (the Pi).
2. **Shared UI**: the Android app's Compose UI moves into a shared Compose Multiplatform module that also builds for the
   browser (Kotlin/Wasm). A separate HTML client is the fallback only if the feasibility test shows the shared route
   can't work. Compose for web being Beta is acceptable for a private friend-group app.
3. Scope: everything the Android app does (lobby, contacts, decks, cubes, pods and drafts, deck building, chat,
   settings, games). Built piece by piece; **deployed to prod only when complete**.
4. Modern browsers, as well as we can: desktop Chrome, Edge, Firefox, Safari; phone Chrome and iOS Safari. No iPhone
   is available for testing; iOS is the least important target.
5. Two layouts: **phone** (looks and works like the Android app) and **desktop** (the same parts arranged for a large
   screen, mouse and keyboard). Chosen automatically by window size and pointer type, with a manual override in
   Settings.
6. Desktop controls start from MTG Arena's and deviate where ATW differs, as the phone app did. Click acts like a tap,
   hover inspects, the mouse wheel zooms, chat/timeline/Stack can be docked, and there are hotkeys (Space, Ctrl+Z, Esc,
   Enter, T, C, F, 1–9 as a start).
7. Back: on-screen Back/close controls everywhere on the web. Screens get real URLs, so the browser's Back moves
   between screens. Dialogs and popups get **no** history entries; Esc closes them on desktop, and on Android Chrome the
   system back closes them through the browser's CloseWatcher where it is supported.
8. **Server-built card catalog** (both clients): the server fetches MTGJSON every night, builds a trimmed catalog and
   serves it; its checksum is its version. When the version changes, Android and web must install it before playing.
   Android switches to it once serving works and drops its own MTGJSON download and incremental updates. Card pictures
   still come straight from Scryfall's image server, never through the Pi.
9. Light requests that a browser can't make directly (sites without CORS, such as Archidekt) go through the Pi.
10. Web notifications are opt-in only: a Settings switch, off by default, plus one dismissible hint in the Games list.
    The site never asks for notification permission by itself.
11. The site is installable as an app (home screen / desktop window).
12. An open tab left on an old version reloads itself at a safe moment, with a short note.
13. Once the web client is live, every feature works in both clients before it is released.

## 1. Architecture (provisional until the feasibility test)

1. **Modules.**
   - `app-ui` (new, Kotlin Multiplatform with Compose Multiplatform): every screen, view model and controller that is
     in `androidApp` today, in `commonMain`; platform pieces in `androidMain` and `wasmJsMain`.
   - `androidApp` becomes a thin shell: the Activity, Firebase notifications, WorkManager jobs, the update installer,
     Android storage and permissions.
   - `webApp` (new, Kotlin/Wasm): the browser entry point, `index.html`, the web manifest, the service worker and the
     engine worker's entry.
   - `client-storage` splits into shared interfaces and logic, the Android implementation (SQLDelight) and a browser
     implementation.
   - `protocol`, `automatum` and `arcanum-*` gain a `wasmJs` target; their code stays common.
2. **Platform services** behind interfaces in `app-ui`, one implementation per platform: storage (catalog, personal
   data, gameplay tree cache), HTTP and WebSocket (Ktor: OkHttp engine on Android, the browser's on web),
   notifications, background work, app updates (Android only), clipboard, sharing a problem report, haptics, keeping
   the screen on, system bars, crash capture, opening links (password reset).
3. **The engine runs in a Web Worker** on the web, so drawing never waits for it (the browser gives a page one
   thread). The boundary is the one Android already uses for background planning (cast plans, End turn, trial casts),
   made message-based (serialisable requests and answers) on both platforms, so there is one design. Whether the
   <20 ms local apply (SYS-025) stays on the drawing thread depends on the measured numbers.
4. **Browser storage**: the catalog and the gameplay tree cache in IndexedDB (or the browser's private file system),
   with persistent storage requested so the browser doesn't evict them; personal data and the session in IndexedDB;
   card pictures in the browser's HTTP cache and Cache API.
5. **Sign-in** (proposal): the same session token as Android, kept in IndexedDB and sent the same way; no new
   server-side session kind.
6. **Tests**: shared composables keep their Roborazzi screenshot tests (rendered on the JVM through the Android host),
   and desktop layouts get screenshot tests the same way at desktop sizes. Web-only behaviour (keys, mouse, routes)
   gets common UI tests. Web end-to-end tests drive a real browser; how they find controls on Compose's canvas
   (semantics or a test hook) is decided after the feasibility test.

## 2. Server

1. **Serves the web app** from the same origin as the API, so the browser needs no cross-origin permissions. Built
   files have content-hashed names and are cached for good (Cloudflare caches them too); `index.html` is never
   cached. Every app route (section 8.2) answers with `index.html`.
2. **Version and stale tabs**: the server already serves only the current protocol (SYS-033). A tab that finds it is
   running an older web build reloads itself at the first safe moment: never while a cast, choice, panel or dialog is
   open or text is being typed; at once on the menu, the game list or before opening a game. After the reload a short
   note says "ATW was updated" and What's new (LOB-087) follows its usual rules. The web has no Update screen
   (LOB-084–086 stay Android-only).
3. **Light proxies** for sites without CORS: Archidekt deck import (LOB-061). Cube Cobra, MTGJSON set files, the
   Scryfall API and Scryfall pictures allow browser requests and are fetched directly (checked 2026-11-07).
4. **Web Push**: the server keeps each browser's push subscription (VAPID keys) and sends the same notifications it
   sends through Firebase (LOB-072–078, LOB-111, LOB-114, LOB-116, DRF-135–138).
5. Problem and crash reports use the existing endpoints.

## 3. Server-built card catalog (first build piece, both clients)

1. Every night (proposal: 04:00 Pi time) the server checks MTGJSON's `Meta.json`; when it changed, the server downloads
   `AllPrintings`, builds the trimmed catalog with the same import code the app uses today (moved to shared JVM code)
   and stores it.
2. The trimmed catalog holds only what the clients read: card faces (name, cost, type line, rules text, P/T, loyalty,
   defense, colours, colour identity, layout, side), legalities and rarity for search (LOB-110), related cards and
   tokens (LOB-125), and each English printing's set, number and Scryfall id for pictures. A measured estimate on the
   2026-10-23 AllPrintings: 42,435 faces, 121,888 printings, **about 9 MB gzip** (184 MB today).
3. Its version is the checksum of its content: a rebuild with no real change keeps the version and publishes nothing.
   The server serves the file under its checksum, cached for good, and says which version is current.
4. Clients check the current version at start and on every return to the app. A new version shows the card records
   screen (like LOB-091: what it is and its size, with Download); until it is installed nothing else in the app works
   (proposal: the whole app, one code path with the first download; the owner allowed either the whole app or games
   only).
5. Proposal: the server also checks the catalog version when a game opens and refuses play from an older one ("Update
   card records to continue"), so both players always work from the same card texts.
6. **Android** switches to this file once it is served: the MTGJSON download, incremental updates and on-device
   indexing of 184 MB go away (changes LOB-091–096, LOB-110, LOB-125).
7. Draft set files (DRF-089): proposal: unchanged, both clients fetch MTGJSON's per-set files directly (small, allowed
   from browsers).

## 4. Layouts and input

1. **Two layouts.** Phone: today's app, portrait or landscape by window shape. Desktop: section 7. Automatic choice:
   desktop when the window is at least 1024 × 600 CSS px and the main pointer is a mouse or trackpad (it can hover);
   otherwise phone. Settings → Table → "Layout": Automatic (default), Phone, Desktop; kept per browser, applied at once.
2. **Input is judged per pointer, not per layout** (proposal): a finger on a touchscreen laptop works as on the phone
   (hold inspects), a mouse in the phone layout works as a mouse (click acts, hover previews, right-click = hold).
3. The Android app keeps the phone layout; a desktop layout for Android tablets or Chromebooks is out of scope.

## 5. Desktop: mouse

| Phone | Desktop |
| --- | --- |
| Tap (acts at once, TBL-041) | Left click, also at once |
| Double tap (400 ms, 32 dp, TBL-042) | Double click (same timing) |
| Hold a card 350 ms to inspect (TBL-047, TBL-104) | **Hover** a card ~250 ms: a large preview beside it with its change lines, no dimming; closes when the pointer leaves. Pressing and holding the button still works. |
| Pinned Inspect by dragging sideways while holding (TBL-131) | **Right-click** a card: pinned Inspect, open until Esc, a click outside or its close button |
| Holding a control does its hold action (step arrow → End turn, Undo → Undo dialog, tooltips SYS-045) | **Right-click = hold** everywhere (proposal): right-click the step arrow = End turn, right-click Undo = the Undo dialog; tooltips show on hover after 0.5 s |
| Drag a card (ZON-077, CMB-009) | Drag with the left button; the same meanings and highlights |
| One-finger drag on empty battlefield pans (TBL-011) | Left-drag on empty battlefield, or middle-drag anywhere |
| Pinch zooms (TBL-010) | Mouse wheel zooms around the pointer (0.65–2×); trackpad pinch zooms; Shift+wheel pans sideways |
| Double tap empty battlefield = fit (TBL-012) | Double click empty battlefield, or F |
| Swipes in draft, Build, Prepare (DRF-058, DRF-099, LOB-048) | The same with a mouse drag; also hotkeys while hovering a card (section 6) |
| Holding a timeline event shows its text (HIS-011) | Hover |

Correction to the first draft of decision 6: on the phone a hold inspects; there is no hold menu. Right-click therefore
does what a hold does (inspect a card, a control's hold action), and a left click opens card menus as a tap does.

## 6. Desktop: keyboard (proposal, Arena as baseline)

Hotkeys never act while a text field has focus. "?" shows the list over the screen.

| Key | Where | Does |
| --- | --- | --- |
| Space | Table | The main button's tap (Next, Resolve, Attack (N), Assume …, Keep) |
| Shift+Space | Table | End turn (the main button's hold) |
| Enter | Open panel or dialog | Its main button (Cast, OK, Done, Confirm, Send) |
| Ctrl+Z (⌘Z) | Table, Build, editors | Undo |
| Ctrl+S (⌘S) | Deck and cube editor | Save |
| Esc | Anywhere | What the phone's Back does (SYS-050): closes the top popup, Inspect, dialog or panel view; drops an unsent cast (STK-025); never leaves the table |
| 1–9 | Open panel | Chooses the Nth mode, way or option |
| T | Table | Timeline mode on/off |
| ← / → | Timeline mode | Previous / next event |
| P | Timeline mode | Auto on/off |
| C | Table | Focus the chat input |
| F | Table | Zoom to fit |
| S | Table | Stack list |
| G / X | Table | Your graveyard / exile view (with Shift: the opponent's) |
| M | Table | Next response mode (Control, Normal, Quick) |
| K / B / U | Draft pick, hovering a card | Keep / burn / unmark |
| ← / → / ↓ | Build, Prepare, hovering a card | Swipe left / swipe right / open its menu |

## 7. Desktop: screens

1. **Shell.** The landscape app's permanent side menu (LOB-018) on the left; content to the right with a readable
   maximum width for lists and forms; dialogs centred.
2. **Game table** (wireframes D1–D4). The landscape table (TBL-009) made for a large screen:
   - Top row: the turn line and move pill (TRN-005, TRN-006), the opponent's life square, piles, emblems and hand
     backs, the chat and Table actions buttons.
   - Proposal, **phase track**: the phase dialog's step squares (TRN-008) shown in the top row, current outlined, so
     the step button and dialog aren't needed on desktop.
   - The battlefields and combat band fill the middle (TBL-001), as wide as the window.
   - Bottom row: your life square and piles, your hand at a larger default card size, the quick actions, Undo and the
     main button at the right end.
   - Proposal, **right dock** (collapsible, about 320 px): the Stack as its full list, always open (top first, as the
     Stack list STK-018 shows it, replacing the floating Stack HUD STK-007 on desktop), and the game chat under it with
     its input always visible (LOB-066's panel, docked).
   - Panels (cast, choices, target bar, STK-044) sit above your hand at the left as in landscape; the damage division
     stays attached to the combat band (CMB-033).
   - Proposal: card sizes (TBL-034) are kept per layout, desktop starting larger (battlefield 1.5×, hand 2×).
3. **Timeline mode** (D5; the hotkey sheet is D6): the timeline panel along the bottom as in landscape; ← / → and hover on events.
4. **Lobby screens** (D7): the same screens with the side menu; game rows wider.
5. **Deck and cube editor** (D8, proposal): search sits beside the list (two panes) instead of a full-screen search
   page (LOB-106): a click on a result adds a copy to the section chosen at the top of the search pane, a drag drops it
   into any section, right-click inspects.
6. **Draft** (D9, proposal): the pick view with the keep/burn trays in a column at the right, and under the pack a
   compact strip of your picks so far (the Build columns at small size, Arena-style). Build, Stats and Games tabs
   unchanged, with more columns by default.
7. **Settings** (D10): adds Layout (section 4.1), Browser notifications (9.2) and Install app (9.1); hides app update rows;
   Card records shows the server catalog's version.

## 8. Phone web and the browser's Back

1. The phone layout is the Android app in a browser.
2. **Routes**: `/` menu, `/games`, `/games/<id>` (lobby or table), `/pods`, `/pods/<id>/<tab>`, `/decks`,
   `/decks/<id>`, `/decks/<id>/edit`, `/cubes/<id>`, `/contacts`, `/settings`, `/reset-password?…` (LOB-003's link
   opens this page when the app isn't installed). Refreshing a page restores that screen.
3. **On-screen Back** wherever the app relies on system Back today:
   - every screen opened from a section gets a back arrow in its header (LOB-020, LOB-106's search page, DRF-076);
   - the game chat side panel gets a close button;
   - already covered: dialogs (SYS-048 close button), menus and popups (a tap outside, TBL-068), pinned Inspect's close
     button (TBL-131), the graveyard view's ✕ (ZON-038), the Stack list's Close (STK-018), "Return to current"
     (HIS-018), Undo for an open cast (STK-025), the side menu from the table's ☰.
4. Browser Back moves between screens only. Proposal: Back from a table goes straight to the previous screen, without
   SYS-050's "second Back" guard (nothing is lost: the game lives on the server).
5. Browser chrome on the table and draft screens: no pull-to-refresh, no page zoom by pinch, no long-press callouts or
   text selection, no browser context menu (right-click = hold).
6. Installed on Android, the app opens without browser bars (SYS-079's hidden status bar has no equivalent in a tab).
7. Haptics where the browser offers them (Android), none on iOS.

## 9. Web platform features

1. **Install** (PWA): a web manifest, icons and a service worker (needed for push; it also caches the app's files for
   a fast start; there is still no offline play, SYS-013). Settings → "Install app" uses the browser's install prompt
   where there is one, or explains "Share → Add to Home Screen" on iOS. The site never prompts by itself.
2. **Notifications**: Settings → "Browser notifications", off by default; turning it on shows the browser's permission
   prompt; refused, the switch goes back off with "Blocked in your browser's site settings". When on, the three kinds
   (Invitations, Game activity, Draft activity, LOB-072) have their own switches. The Games list shows one hint card
   "Get notified when it's your move" with Turn on and ✕; ✕ hides it for good in that browser. Clicking a
   notification opens or focuses the tab at that game or pod (LOB-078). Proposal: the tab title counts games waiting
   for you ("(2) ATW").
3. **Card records**: the first download (LOB-091) shows the size; browsers don't reliably say whether a connection is
   metered, so that line is left out. iOS Safari deletes a site's data after 7 days without a visit unless the site is
   installed; the catalog then downloads again.
4. **Several tabs** are several devices (HIS-032); the same game open twice works as on two phones.
5. **Log out** (LOB-007) clears that account's data from the browser.
6. Test builds keep Switch account / Add account (LOB-009).

## 10. Catalog and tests

1. Web-only behaviour goes into a new catalog file `docs/features/web.md` (area `WEB`) as each part is built: layout
   choice, mouse mapping, hotkeys, routes and Back, notifications, install, stale-tab reload.
2. Existing entries apply to both clients unless a `WEB` entry says otherwise; Android-only ones (update screen, status
   bar, notification channels) are marked "(Android)" when the web part is built.
3. Each part's tests follow AGENTS.md: unit and UI tests for logic, screenshots for layouts, end-to-end coverage of each
   new flow, run in a browser for the web.

## 11. Build order

0. **Feasibility test** (running 2026-11-07, throwaway branch): engine in wasm vs JVM, engine in a Web Worker, table
   drawing frame rate with real pictures, moving one real table file into a shared module. Section 1 is finalised from
   its numbers.
1. **Server-built catalog** and Android's switch to it. Useful on its own; can go to prod independently.
2. **Shared UI module**: Android UI moved into `app-ui`, behaviour identical (screenshots unchanged).
3. **Web shell**: the wasm app served by the server, sign-in, card records, lobby screens in the phone layout, routes
   and on-screen Back.
4. **Game table on the web** (phone layout) with the engine in a worker.
5. **Desktop layout**: shell, table with dock and phase track, hover preview, right-click, hotkeys.
6. **Desktop decks, cubes, drafts and Build.**
7. **Notifications, install, stale-tab reload.**
8. **Web end-to-end coverage**, a full green run, then the first prod deploy.

## 12. Open questions for the owner

1. Right-click = hold everywhere (section 5)?
2. Right dock with the full Stack list and chat, replacing the floating Stack on desktop (7.2)?
3. Phase track in the top row instead of the step button and dialog on desktop (7.2)?
4. Card sizes kept per layout, desktop starting larger (7.2)?
5. The catalog update blocks the whole app (3.4)? The server refuses play from an old catalog (3.5)?
6. Draft set files stay direct from MTGJSON (3.7)?
7. Browser Back from a table leaves without a second-Back guard (8.4)?
8. The hotkey list (section 6), the two-pane deck editor (7.5), the draft picks strip (7.6), the tab title count (9.2)?
