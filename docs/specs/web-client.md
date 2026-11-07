# Web client: desktop and phone browsers (owner, 2026-11-07)

Status: **ready for owner review (2026-11-07), not started.** Roadmap item 2 ("Desktop web client"), started before the Android client is
finished because Automatum shrank the set of player actions. Decisions marked **(owner)** are agreed; everything marked
**(proposal)** is Claude's and waits for the owner. The feasibility test (section 11, step 0) reported on 2026-11-07:
the shared route works on desktop; the phone measurement is still to be done. Wireframes: the "ATW Web Wireframes" design canvas, https://claude.ai/artifact/FhqdRe7Er5PHv86t8u2rXL (private to the
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
   hover gives a quick look, the mouse wheel zooms, chat/timeline/Stack can be docked, and there are hotkeys (Space, Ctrl+Z, Esc,
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
14. Desktop mouse: **left click acts like a tap** (for now); **right-click toggles Inspect**, shown the desktop way:
    the card with its changes beside it and a close button (desktop has the room), open until right-clicked again,
    closed or Esc. Desktop has no press-and-hold.
15. Desktop table: phase track in the top row instead of the step button and dialog; a right dock with the full Stack
    list and the chat, replacing the floating Stack; card sizes kept per layout, desktop starting larger.
16. Clients and server always work from the same card records: a new catalog version must be installed before
    anything else, and the server refuses clients on an older one.
17. Draft data (set booster recipes) is part of the server-built catalog, so drafts can be set up on the web too.
18. Browser Back leaves a table without a second-Back guard.
19. The proposed hotkeys, two-pane deck editor, draft picks strip and tab title count are built as proposed and refined
    by testing.

## 1. Architecture

Feasibility test results (2026-11-07, throwaway branch `w/web-spike`, Chrome on Windows, busy board of ~180 cards):

| Engine operation | JVM | Chrome wasm (worker) | Ratio |
| --- | --- | --- | --- |
| Load a game snapshot | 61 ms | 169 ms | 2.8× |
| Cast start / pay / send | 3 / 16 / 31 ms | 11 / 49 / 88 ms | 2.9–3.3× |
| Passes after a cast | 41 ms | 119 ms | 2.9× |
| End turn | 67 ms | 185 ms | 2.8× |

- All shared modules (`core`, `protocol`, `automatum`, `arcanum-*`) build for wasm and their 12,597 common tests pass
  there; the fixes were small (explicit element types where the wasm compiler infers `List<Any>`, a few JVM-only calls
  that slipped into common code, wasm SHA-1/SHA-256).
- In a Web Worker the page keeps a steady 60 fps while the engine computes; sending states across as JSON strings costs
  about 2 ms per round trip.
- Drawing 150 cards with real Scryfall pictures, tapped and panning, runs at the display's rate (~60 fps) in Chrome with
  a GPU; mouse, wheel, touch pan and pinch all work.
- Download: about 3.8 MB brotli on first load (Compose's Skia runtime 2.5 MB, engine 0.9 MB, app code 0.35 MB), cached
  afterwards.
- A real table file moved into a shared module builds for Android and wasm with Android screenshots unchanged; the
  table code is not layered, so the UI must move as one module, not file by file.
- Not measured yet: a phone browser (the phone wasn't attached). Mid-range phones may be 3–5× slower than desktop; a
  pass would then take 0.3–0.5 s.


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
   made message-based (JSON-string requests and answers) on both platforms, so there is one design. Kotlin/Wasm, not
   Kotlin/JS (slower for this engine).
4. **Browser storage**: the catalog and the gameplay tree cache in IndexedDB (or the browser's private file system),
   with persistent storage requested so the browser doesn't evict them; personal data and the session in IndexedDB;
   card pictures in the browser's HTTP cache and Cache API.
5. **Sign-in** (proposal): the same session token as Android, kept in IndexedDB and sent the same way; no new
   server-side session kind.
6. **Tests**: shared composables keep their Roborazzi screenshot tests (rendered on the JVM through the Android host),
   and desktop layouts get screenshot tests the same way at desktop sizes. Web-only behaviour (keys, mouse, routes)
   gets common UI tests. Web end-to-end tests drive a real browser; how they find controls on Compose's canvas
   (semantics or a test hook) is decided when that step is built.
7. **First load** shows a loading screen while the ~3.8 MB app downloads; later visits load from the browser cache.
8. **The wasm target stays green**: once added, the shared modules' common tests run on wasm too, so JVM-only calls
   can't slip back into common code.

## 2. Server

1. **Serves the web app** from the same origin as the API, so the browser needs no cross-origin permissions. Built
   files have content-hashed names and are cached for good (Cloudflare caches them too); `index.html` is never
   cached. Every app route (section 8.2) answers with `index.html`.
2. **Version and stale tabs**: the server already serves only the current protocol (SYS-033). A tab that finds it is
   running an older web build reloads itself at the first safe moment: never while a cast, choice, panel or dialog is
   open or text is being typed; at once on the menu, the game list or before opening a game. After the reload a short
   note says "ATW was updated" and What's new (LOB-087) follows its usual rules. The web has no Update screen
   (LOB-084–086 stay Android-only).
3. **Light proxies** for sites without CORS: Archidekt deck import (LOB-061). Cube Cobra, the Scryfall API and
   Scryfall pictures allow browser requests and are fetched directly (checked 2026-11-07).
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
4. Clients check the current version at start, on every return to the app and when the server says it changed. A
   new version shows the card records screen (like LOB-091: what it is and its size, with Download); until it is
   installed nothing else in the app works (one code path with the first download).
5. The server takes requests only from clients on the current catalog version (sent with every connection and
   request); an older one gets "Update card records to continue" and the client shows the card records screen. So
   both players and the server always work from the same card texts.
6. **Android** switches to this file once it is served: the MTGJSON download, incremental updates and on-device
   indexing of 184 MB go away (changes LOB-091–096, LOB-110, LOB-125).
7. Draft data is part of the catalog: the booster recipes of draftable sets (with the printings they name), measured
   at about 3.4 MB gzip for 184 sets, so the whole catalog is roughly 12–13 MB. The per-set MTGJSON downloads and their
   Redownload buttons go away (changes DRF-089, DRF-088, DRF-090); since the server holds the same catalog, it builds
   set packs from it.

## 4. Layouts and input

1. **Two layouts.** Phone: today's app, portrait or landscape by window shape. Desktop: section 7. Automatic choice:
   desktop when the window is at least 1024 × 600 CSS px and the main pointer is a mouse or trackpad (it can hover);
   otherwise phone. Settings → Table → "Layout": Automatic (default), Phone, Desktop; kept per browser, applied at once.
2. **Input is judged per pointer, not per layout** (proposal, also to confirm in review): a finger on a touchscreen laptop works as on the phone
   (hold inspects), a mouse in the phone layout works as a mouse (click acts, hover gives a quick look, right-click toggles Inspect).
3. The Android app keeps the phone layout; a desktop layout for Android tablets or Chromebooks is out of scope.

## 5. Desktop: mouse

| Phone | Desktop |
| --- | --- |
| Tap (acts at once, TBL-041) | Left click, also at once |
| Double tap (400 ms, 32 dp, TBL-042) | Double click (same timing) |
| Hold a card 350 ms to inspect (TBL-047, TBL-104), pinned by a sideways drag (TBL-131) | **Right-click toggles Inspect** (owner): the card's images with its changes beside them (as pinned Inspect shows them) and a close button; right-click again, ✕, Esc or a click outside closes. No press-and-hold on desktop. |
| — | **Hover** a card ~250 ms for a quick look (owner): the large picture only, no dimming, gone when the pointer leaves |
| Holding a control does its hold action (step arrow → End turn, Undo → Undo dialog, tooltips SYS-045) | Right-click a control does its hold action (owner) (step arrow = End turn, Undo = the Undo dialog); Shift+Space = End turn; tooltips show on hover after 0.5 s |
| Drag a card (ZON-077, CMB-009) | Drag with the left button; the same meanings and highlights |
| One-finger drag on empty battlefield pans (TBL-011) | Left-drag on empty battlefield, or middle-drag anywhere |
| Pinch zooms (TBL-010) | Mouse wheel zooms around the pointer (0.65–2×); trackpad pinch zooms; Shift+wheel pans sideways |
| Double tap empty battlefield = fit (TBL-012) | Double click empty battlefield, or F |
| Swipes in draft, Build, Prepare (DRF-058, DRF-099, LOB-048) | The same with a mouse drag; also hotkeys while hovering a card (section 6) |
| Holding a timeline event shows its text (HIS-011) | Hover |

A left click opens card menus, activates or raises a hand card exactly as a tap does (TBL-041, TBL-122, ZON-079).

## 6. Desktop: keyboard (Arena as baseline; owner: build as proposed, refine by testing)

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
   - **Phase track** (owner): the phase dialog's step squares (TRN-008) shown in the top row, current outlined, so
     the step button and dialog aren't needed on desktop.
   - The battlefields and combat band fill the middle (TBL-001), as wide as the window.
   - Bottom row: your life square and piles, your hand at a larger default card size, the quick actions, Undo and the
     main button at the right end.
   - **Right dock** (owner; collapsible, about 320 px): the Stack as its full list, always open (top first, as the
     Stack list STK-018 shows it, replacing the floating Stack HUD STK-007 on desktop), and the game chat under it with
     its input always visible (LOB-066's panel, docked).
   - Panels (cast, choices, target bar, STK-044) sit above your hand at the left as in landscape; the damage division
     stays attached to the combat band (CMB-033).
   - Card sizes (TBL-034) are kept per layout (owner), desktop starting larger (proposal: battlefield 1.5×, hand 2×).
3. **Timeline mode** (D5; the hotkey sheet is D6): the timeline panel along the bottom as in landscape; ← / → and hover on events.
4. **Lobby screens** (D7): the same screens with the side menu; game rows wider.
5. **Deck and cube editor** (D8): search sits beside the list (two panes) instead of a full-screen search
   page (LOB-106): a click on a result adds a copy to the section chosen at the top of the search pane, a drag drops it
   into any section, right-click inspects.
6. **Draft** (D9): the pick view with the keep/burn trays in a column at the right, and under the pack a
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
4. Browser Back moves between screens only. Back from a table goes straight to the previous screen, without SYS-050's
   "second Back" guard (owner; nothing is lost: the game lives on the server).
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
   notification opens or focuses the tab at that game or pod (LOB-078). The tab title counts games waiting for you
   ("(2) ATW").
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

## 11. Build plan

Each step is one implementor batch (or a few, where noted), ends merged on master with its suites green, and follows
AGENTS.md (catalog entries in the same commit, release notes for visible changes, e2e coverage of new flows). Order and
what may run side by side:

```
0 (phone run) ─┐
0b wasm target ─┴─► 2 shared UI ─┐
1a server catalog ─► 1b Android ──┴─► 3 web shell ─► 4 web table ─┬─► 5 desktop table
                                                                  ├─► 6 desktop screens
                                       3 ─► 7 notifications, install (any time after 3)
                                                                  └─► 8 web e2e + prod
```

### Step 0: feasibility test (desktop done)

Remaining: run the spike pages in Chrome on the owner's phone (benchmark in a worker, table drawing) and add the numbers
to section 1. If a pass takes much over ~0.5 s there, the phone-web table needs its own plan before step 4 (the desktop
plan is unaffected).

### Step 0b: wasm target on the shared modules

- `wasmJs` (browser and Node) on `core`, `protocol`, `automatum`, `arcanum-api`, `arcanum-parser`, `arcanum-engine`; the
  spike's fixes redone cleanly (explicit element types for mixed sealed-object lists, a common `replaceAll` helper,
  `@JvmInline` imports, wasm SHA-1/SHA-256), Node/Yarn/Binaryen download repositories in `settings.gradle.kts`.
- Common tests also run on wasm (Node); AGENTS.md's task list gains the wasm test tasks (owner approves the AGENTS
  change). Arcanum libraries stay ATW-free (`verifyModuleBoundaries`).
- No behaviour change, no catalog change, no release note. Small (one batch).

### Step 1a: the server builds and serves the catalog

- The MTGJSON import (today `client-storage`'s streaming `MtgJsonCatalog`, JVM code) moves to a module the server can
  use; the trimmed catalog's format and reader go in common code (Android and browser read it).
- Format (proposal): gzip-compressed JSON lines, one record per face, printing, set and booster recipe; browsers unpack
  it natively (`DecompressionStream`). Contents: section 3.2 plus the set list (DRF-088) and the booster recipes of
  draftable sets with the printings they name.
- A nightly job (section 3.1) streams `AllPrintings` (never loads it whole: the Pi has 8 GB), writes the file, computes
  its checksum, keeps the current and previous files on disk. A manual "build now" for operations (CLI or admin
  endpoint).
- Endpoints, open without sign-in (the first download happens before sign-in, LOB-091): current version (checksum, size,
  date, counts) and the file by checksum, cached for good.
- Version check: clients send their catalog version with every request and socket handshake; the server answers lobby,
  deck, pod and game requests from an older one with "Update card records to continue". Account, catalog, update and
  problem-report requests stay open.
- Set pods: the server builds set packs from its own catalog; clients send only the set code (PodService no longer takes
  uploaded set files).
- Tests: server tests with a small synthetic MTGJSON (no real card data committed): build, unchanged rebuild keeps the
  version, endpoints, the version refusal, set packs from the catalog.

### Step 1b: Android uses the server catalog

- The app downloads the server file instead of MTGJSON and builds its search indexes from it (small, so indexing is
  quick). A different version shows the card records screen and blocks the app until installed (owner decision 16);
  a refused request does the same.
- Removed: incremental and metered-connection updates, "Refresh full catalog", per-set downloads and Redownload, the
  "downloaded again once" migrations.
- Catalog: LOB-092 removed; LOB-091, LOB-093, LOB-094, LOB-095, LOB-110, LOB-125, DRF-088, DRF-089 changed; DRF-090
  unchanged in effect (recipes now from the server). Release note "mod: Card records now come from the ATW server: a
  much smaller download, and both players always have the same ones."
- E2E: a new catalog version published while both players are in a game: each app asks for the update, play continues
  after it; a pod from a set starts with server-built packs.
- Steps 1a + 1b can go to prod on their own, before any web work (owner approves that release as usual).

### Step 2: the shared UI module

- New `app-ui` module (Compose Multiplatform 1.11.x; on Android it resolves to the same androidx Compose, so drawing
  doesn't change). `androidApp`'s UI moves into it as a whole (its `internal` code doesn't survive a split), keeping
  package names so history stays readable. `androidApp` keeps the Activity, Firebase, WorkManager, the update installer.
- Sub-steps, each merged green: (a) switch dependencies to Compose Multiplatform artifacts, nothing moved; (b) platform
  interfaces for the ~41 files using Android APIs (about 10–15 need real redesign: storage and preferences, text paint
  on the native canvas, Coil loaders, resources, Ktor engine, notifications, haptics, system bars); (c) move the code;
  (d) `client-storage` becomes multiplatform: common interfaces and logic, the Android SQLDelight implementation.
- The guard: every existing screenshot test passes unchanged in verify mode, and every e2e test passes. No catalog
  change, no release note.
- The largest step and it touches almost every Android file: run it when no other Android batch is open, and merge it
  quickly to avoid long conflicts.

### Step 3: web shell (phone layout)

- `webApp` module (Kotlin/Wasm); the server serves its build (section 2.1) and the Archidekt proxy (2.3); stale-tab
  reload (2.2); a loading screen for the first load (1.7).
- Routes, browser history, on-screen Back and CloseWatcher (section 8); sign-in with the token in IndexedDB; the card
  records download into IndexedDB with persistent storage; lobby screens in the phone layout: menu, games, contacts,
  game lobby, decks and cubes (view, import, editor), settings.
- Catalog: new `docs/features/web.md` (routes, Back, stale reload, first load, card records on the web); LOB-084–086
  marked Android-only. Release notes start here but no prod deploy until step 8.
- Tests: the web-only logic (routes, reload decision, storage) as common unit tests; a real-browser smoke for the wasm
  build (Playwright on Chrome: load, sign in, list games), run with the e2e suite.

### Step 4: the game table on the web (phone layout)

- The engine in a Web Worker behind the message boundary (section 1.3); Android uses the same boundary on its
  background thread. The table, hand, panels, combat band, timeline, chat in the browser; browser chrome kept off the
  table (section 8.5); haptics where available.
- Depends on step 0's phone numbers for the phone layout's speed.
- Tests: the existing e2e suite already drives the shared UI in the JVM, so it keeps covering the table; the browser
  smoke gains "open a game, make one move"; a mixed e2e: one player on Android code, one on the web build's code path.

### Step 5: desktop table

- Layout choice and the Layout setting (section 4); the desktop table: top row with phase track, right dock (Stack list,
  chat), larger hand, panels as in landscape; mouse (section 5: click = tap, right-click toggles Inspect with changes
  and close button, right-click on controls = hold action, hover quick look, wheel zoom, drags); hotkeys and the "?"
  sheet (section 6); card sizes per layout.
- Catalog: `web.md` entries for layout choice, mouse, hotkeys, dock, phase track.
- Tests: screenshot tests of the desktop table at 1440×900 (D1–D6 states), e2e flows in the desktop layout with mouse
  and key input (the JVM harness renders it at desktop size).

### Step 6: desktop lobby, decks, drafts

- Permanent side menu shell, wider lists, two-pane deck and cube editor (D8), draft pick with trays at the right and
  the picks strip (D9), Build and Stats with more columns, settings rows (D10). Hotkeys K/B/U and the Build swipes, Ctrl+S.
- Tests: screenshots per screen at desktop size; e2e: build a deck in the two-pane editor; draft a pick with keys.

### Step 7: notifications, install, tab title

- Web Push (server VAPID keys and subscriptions, sending alongside Firebase), the Settings switch and per-kind switches,
  the Games-list hint, notification clicks opening the right route; web manifest, icons, service worker, Install app;
  the "(N) ATW" tab title.
- Tests: server tests for subscriptions and sending; unit tests for the hint and switches; browser smoke for the
  manifest and service worker registering.

### Step 8: complete, then prod

- Full suites including the browser smoke and e2e, a manual session of the owner on desktop and phone web, the review
  gates (`scripts/review.sh` catalog and screenshots), then the first prod deploy of server + web + Android together.

### Risks

- Phone browsers may be too slow for comfortable play (step 0 decides; desktop is unaffected).
- Compose for web is Beta: upgrades may need small fixes; iOS Safari is untested (no device).
- Step 2 is large and collides with any concurrent Android work.
- Bandwidth from the Pi: the app (~3.8 MB) and catalog (~12–13 MB) per new device or version, cached by Cloudflare under
  content-hashed names; the nightly MTGJSON download (184 MB) when it changed.
- Web e2e on a canvas: kept small by testing shared UI in the JVM harness and only smoke-testing the real browser.

### Possible follow-ups (not planned)

- The server works out card definitions from its own catalog instead of storing the ones clients upload with decks.
- A desktop layout for Android tablets and Chromebooks.

## 12. Open questions for the owner

All design questions are answered (2026-11-07). Claude's plan choices to confirm in review:

1. Step 0b adds wasm test tasks to AGENTS.md's task list.
2. Catalog file format: gzip-compressed JSON lines (1a).
3. Steps 1a + 1b may go to prod before any web work (1b).
4. Account, catalog, update and problem-report requests stay open to clients on an old catalog (1a).
5. Step 2 runs when no other Android batch is open (2).
6. The real browser is only smoke-tested (Playwright on Chrome); everything else is tested through the shared UI in the
   existing JVM e2e harness, at phone and desktop sizes (3–5).
