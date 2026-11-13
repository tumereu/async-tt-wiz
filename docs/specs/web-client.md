# Web client: desktop and phone browsers (owner, 2026-11-07)

Status: **ready for owner review (2026-11-07), not started.** Roadmap item 2 ("Desktop web client"), started before the Android client is
finished because Automatum shrank the set of player actions. Decisions marked **(owner)** are agreed; everything marked
**(proposal)** is Claude's and waits for the owner. The feasibility test (section 11, step 0) reported on 2026-11-07:
the shared route works on desktop. Phone browsers aren't measured: the web client is built for desktop, phones have the
native app, and phone-web speed is optimised later only if people use it (owner). Wireframes: the "ATW Web Wireframes" design canvas, https://claude.ai/artifact/FhqdRe7Er5PHv86t8u2rXL (private to the
owner): D1–D19 and D3b desktop, P1–P4 phone web (redrawn 2026-11-11, multiplayer-ready). The wireframes show layout and
positioning only; every element looks as it does in the Android app (owner).
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
8. **Server-built card catalog** (both clients; revised by the owner 2026-11-09, section 3): one pinned MTGJSON file,
   trimmed into our catalog at release and deployed with the server; its checksum is its version, and it changes only
   with a release. Android and web install it before anything that reads card records. Android switches to it once
   serving works and drops its own MTGJSON download and incremental updates. Card pictures
   still come straight from Scryfall's image server, never through the Pi.
9. Light requests that a browser can't make directly (sites without CORS, such as Archidekt) go through the Pi.
10. Web notifications are opt-in only: a Settings switch, off by default, plus one dismissible hint in the Games list.
    The site never asks for notification permission by itself.
11. The site is installable as an app (home screen / desktop window).
12. An open tab left on an old version reloads itself at a safe moment, with a short note.
13. Once the web client is live, every feature works in both clients before it is released.
14. Desktop mouse: **left click acts like a tap** (for now); **right-click toggles Inspect**, shown the desktop way: **[IN DOUBT: the Inspect input is open, §12 item 10; build no desktop Inspect input until it is decided.]**
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
20. No phone-browser benchmark before building (round 3): the web client is for desktop; phone web is secondary and gets
    optimised only if people end up playing on it.
21. Right-click Inspect dims the table behind it, so it reads as a layer on top that scrolls on its own. How a large pile **[IN DOUBT: the Inspect input is open, §12 item 10; build no desktop Inspect input until it is decided.]**
    (a creature with many Equipment) looks is tuned by real testing.
22. Hovering a usable card already shows its chips, faded, and the sources Autotap would tap (ZON-079's amber outlines).
23. Keyboard shortcuts are rebindable and kept on the player's account.
24. Deck and cube editor: the list on the left, search on the right.
25. No swipes with a mouse. Draft pick: left click keeps, right click burns. Build and the card list screens: a card
    shows a zone at its left and right edge, tinted in the destination section's colour on hover; a click there moves it.
26. Desktop Build has no one-by-one review (DRF-107–110). If it is wanted later, it is used by keyboard (Space keeps,
    other keys move).

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
- Not measured: a phone browser. Mid-range phones may be 3–5× slower than desktop (a pass 0.3–0.5 s); not needed for
  the desktop client (decision 20).


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

## 3. Server-built card catalog (first build piece, both clients; owner 2026-11-09)

The catalog changes only with a release, because a changed wording can break the parser: what the tests check, the
server and both phones all read one pinned file.

1. **The pin.** A small file in the repo, `catalog/pin.properties`, names the MTGJSON version and the SHA-256 of the raw
   `AllPrintings.json.gz`; the trimmed catalog's checksum is not pinned (section 3.4). No card data is committed. The
   raw file lives only on the developer's machine, in `~/projects/atw-cache/AllPrintings.json.gz`, where the Arcanum
   tools already read it (`ParserCorpus`); nothing in the repo says where else a copy is kept (owner: no tailnet or
   host details in the repo), and the Pi gets only the trimmed catalog. `scripts/catalog-pin.sh verify` checks the
   local file; `move` takes MTGJSON's current build and rewrites the pin. A fresh clone builds and passes every test
   without it: unit tests and e2e use invented cards. Only the real-card tools (`setSupport`, `parserDiff`,
   `patternFrequencies`) and a release need it; without the pinned file they stop with one plain message: put the
   pinned file there (its version and checksum named), or move the pin.
2. **Moving the pin** is a deliberate task: `setSupport` and `parserDiff` run against the new file first, and their
   changes are fixed or accepted before it ships. New sets and previews reach players only with a release (LOB-092's
   automatic updates go away). The first pin is MTGJSON's latest build at the time (owner), as its own first step.
3. **The trimmed catalog** holds only what clients read: card faces (name, cost, type line, rules text, P/T, loyalty,
   defense, colours, colour identity, layout, side), legalities and rarity for search (LOB-110), related cards and
   tokens (LOB-125), each English printing's set, number and Scryfall id for pictures, the set list (DRF-088), and the
   booster recipes of draftable sets with the printings they name. Estimate: about 12–13 MB gzip. Its version is the
   checksum of its content, so a change in what we extract makes a new version too, even on the same pin.
4. **At release** `scripts/prod-release.sh` verifies the raw file against the pin (a different file stops the release),
   builds the trimmed catalog with the same code the server and clients read it with, builds its checksum into the app,
   and deploys it to the Pi with the server. The Pi never downloads MTGJSON. The server keeps the previous
   catalog for a rollback. Endpoints, open without sign-in (the first download comes before sign-in, LOB-091): the
   current version (checksum, size, date, counts) and the file by checksum, cached for good.
5. **Clients** carry the expected checksum, built in at release. The one-version rule already sends an older app to
   the update screen, so a new catalog only ever comes with a new app: on start the app compares its installed
   catalog with the built-in checksum and downloads the file from the server when they differ. No catalog version is
   sent with requests. Always the whole file, no partial updates; indexing it is quick.
6. **While it installs** the app is usable: menus, Settings, chat, profile, match history and the games list work. Every
   control whose action reads card records stays visible but greyed, saying "Card records updating · 43%" (opening a
   game, creating or joining one, deck and cube editing, imports, card search, pod picks, avatars); content that needs
   card records (a deck's cards, a game's table) shows a placeholder with the same line in its place. A failed download
   is tried once more by itself, then the line says so with Retry.
7. **Set pods** get packs the server builds from its own catalog; clients send only the set code, so the set upload and
   its limits (`SetSourceValidation.MAX_SHEETS`, `MAX_CARDS`) go away and Reality Fracture and The List can be chosen.
8. **Support marks** (owner 2026-11-09): the catalog says which cards Automatum supports and which sets can be drafted,
   worked out at release with the parser and engine that ship with it, so clients know at once (no working it out on
   the phone: `CatalogSetSupport` and LOB-118's "Checking which cards Automatum can play…" go away). A card is supported
   by the backlog's "support moves set by set" rule (a printing in a listed set, or on the single-card list) and only if
   `AutomatumSupport.handParts` finds nothing for it; a set can be drafted when every card its packs can hold is
   supported (DRF-161's rule). The marks are part of the catalog, so a release that changes support makes a new
   catalog version too. Deck and cube warnings (LOB-115, DRF-161) read the same marks.
9. **Showing them** (owner): in the draft set search an unsupported set is greyed and can't be selected at all; deck
   building and card search show an unsupported card with a mark over its art, and so do the visual deck editor and
   Inspect. Proposed mark: LOB-115's amber warning sign in the art's corner; holding it says "Automatum can't play this
   card yet"; a set row's hold names its unsupported cards as DRF-161 does.

## 4. Layouts and input

1. **Two layouts.** Phone: today's app, portrait or landscape by window shape. Desktop: section 7. Automatic choice:
   desktop when the window is at least 1024 × 600 CSS px and the main pointer is a mouse or trackpad (it can hover);
   otherwise phone. Settings → Table → "Layout": Automatic (default), Phone, Desktop; kept per browser, applied at once.
2. **Input is judged per pointer, not per layout** (proposal, also to confirm in review): a finger on a touchscreen laptop works as on the phone
   (hold inspects), a mouse in the phone layout works as a mouse (click acts, hover gives a quick look, right-click toggles Inspect). **[IN DOUBT: the Inspect input is open, §12 item 10; build no desktop Inspect input until it is decided.]**
3. The Android app keeps the phone layout; a desktop layout for Android tablets or Chromebooks is out of scope.

## 5. Desktop: mouse

| Phone | Desktop |
| --- | --- |
| Tap (acts at once, TBL-041) | Left click, also at once |
| Double tap (400 ms, 32 dp, TBL-042) | Double click (same timing) |
| Hold a card 350 ms to inspect (TBL-047, TBL-104), pinned by a sideways drag (TBL-131) | **Right-click toggles Inspect** (owner): the table dims behind it; the card's images with its changes beside them (as pinned Inspect shows them), a long list scrolling inside the layer, and a close button; right-click again, ✕, Esc or a click outside closes. No press-and-hold on desktop. **[IN DOUBT: the Inspect input is open, §12 item 10; build no desktop Inspect input until it is decided.]** |
| — | **Hover** a card ~250 ms for a quick look (owner): the large picture only, no dimming, gone when the pointer leaves |
| A tap raises a hand card with its chips and amber Autotap outlines (ZON-079, ZON-080); a tap on a card with several abilities opens its chips (TBL-124) | **Hovering a usable card shows its chips faded** (owner), with the amber outlines of what Autotap would tap for the first chip; hovering a chip makes it solid and outlines that chip's sources. A click on a chip uses it at once; a click on the card does what a tap does. A battlefield card with one usable ability shows that one chip, so hover tells what a click will do (proposal) |
| Holding a control does its hold action (step arrow → End turn, Undo → Undo dialog, tooltips SYS-045) | Right-click a control does its hold action (owner) (step arrow = End turn, Undo = the Undo dialog); Shift+Space = End turn; tooltips show on hover after 0.5 s |
| Drag a card (ZON-077, CMB-028) | Drag with the left button; the same meanings and highlights |
| One-finger drag on empty battlefield pans (TBL-011) | Left-drag on empty battlefield, or middle-drag anywhere |
| Pinch zooms (TBL-010) | Mouse wheel zooms around the pointer (0.65–2×); trackpad pinch zooms; Shift+wheel pans sideways |
| Double tap empty battlefield = fit (TBL-012) | Double click empty battlefield, or F |
| Draft pick: swipe right keeps, left burns; tap opens the menu (DRF-058–062) | **Left click keeps, right-click burns** (owner): the same click again unmarks, the other switches (DRF-060); hover gives the quick look with every face (DRF-063's content); a ⋯ button on the hovered card opens the menu (Refund Librarian) (proposal); K / B / U on the hovered card |
| Build, Prepare and the Cards tab: swipe a card to another section (DRF-099, LOB-048, LOB-105) | **Edge zones** (owner): hovering a card's left or right quarter tints it in the destination section's colour with its name ("◂ Trash", "Side ▸"); a click there moves one copy, as the swipe would. Build keeps DRF-099's destinations; in the deck editor and Prepare the right zone is the next section (as the swipe) and the left zone the remaining one (decks; cubes have only a right zone) (proposal). A click in the middle opens the card's menu as a tap does |
| Build review, one by one (DRF-107–110) | Not on desktop (owner): column labels and section headers don't open it |
| Holding a timeline event shows its text (HIS-011) | Hover |

A left click opens card menus, activates or raises a hand card exactly as a tap does (TBL-041, TBL-122, ZON-079).

## 6. Desktop: keyboard (Arena as baseline; owner: build as proposed, refine by testing)

Hotkeys never act while a text field has focus. "?" shows the list over the screen with the player's current keys and a
"Change keys in Settings" button.

**Rebinding** (owner): Settings → "Keyboard shortcuts" lists every action below with its key; a click on a key waits for
the next key press (Esc cancels), "Reset all" restores the defaults. Kept on the server for the account, like LOB-113's
Automatum settings, so every browser shows the same (owner). Proposals: taking a key another action uses leaves that
action without a key and says so on its row; Esc, Enter and the mouse can't be rebound; one key per action.

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
| K / B / U | Draft pick, hovering a card | Keep / burn / unmark (the clicks do the same) |
| ← / → / ↓ | Build, Prepare, editor, hovering a card | Move to the left / right edge zone's section / open its menu |

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
5. **Deck and cube editor** (D8): the list on the left and search on the right (owner), instead of a full-screen
   search page (LOB-106): a click on a result adds a copy to the section chosen at the top of the search pane, a drag
   drops it into any section, right-click inspects. List cards move by their edge zones (section 5). **[IN DOUBT: the Inspect input is open, §12 item 10; build no desktop Inspect input until it is decided.]**
6. **Draft** (D9): the pick view with the keep/burn trays in a column at the right, and under the pack a
   compact strip of your picks so far (the Build columns at small size, Arena-style); click keeps, right-click burns
   (section 5). Build with edge zones and no one-by-one review; Stats and Games tabs unchanged; more columns by default.
7. **Settings** (D10): adds Layout (section 4.1), Browser notifications (9.2), Install app (9.1) and Keyboard shortcuts
   (section 6); hides app update rows; Card records shows the server catalog's version.

## 8. Phone web and the browser's Back

1. The phone layout is the Android app in a browser.
2. **Routes**: `/` menu, `/games`, `/games/<id>` (lobby or table), `/pods`, `/pods/<id>/<tab>`, `/decks`,
   `/decks/<id>`, `/decks/<id>/edit`, `/cubes/<id>`, `/contacts`, `/settings`, `/reset-password?…` (LOB-003's link
   opens this page when the app isn't installed). Refreshing a page restores that screen.
3. **On-screen Back** wherever the app relies on system Back today:
   - every screen opened from a section gets a back arrow in its header (LOB-020, LOB-106's search page, DRF-076);
   - the game chat side panel gets a close button;
   - already covered: dialogs (SYS-048 close button), menus and popups (a tap outside, SYS-042), pinned Inspect's close
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
playtest batch ─► library upgrades ─► 3 web shell ─► 4 web table ─┬─► 5 desktop table (after the wireframe review)
                                                                  ├─► 6 desktop screens
                                       3 ─► 7 notifications, install (any time after 3)
                                                                  └─► 8 web e2e + prod
```

### Step 0: feasibility test (done)

Desktop results are in section 1. No phone-browser run (decision 20).

### Step 1a: the pin and the server catalog (built)

- `scripts/catalog-pin.sh` (verify, move) and the pin file (section 3.1); the Arcanum tools read the pinned file, check
  it against the pin, and stop with section 3.1's message without it.
- The MTGJSON import (today `client-storage`'s streaming `MtgJsonCatalog`, JVM code) moves to a module the release
  script and server can use; the trimmed catalog's format and reader go in common code (Android and browser read it).
  Format (proposal): gzip-compressed JSON lines, one record per face, printing, set and booster recipe; browsers unpack
  it natively (`DecompressionStream`).
- Release: section 3.4 in `scripts/prod-release.sh` and the Ansible role; the e2e stack and the local dev stack serve
  a catalog built from the test cards.
- Set pods: the server builds set packs from its catalog; `PodService` no longer takes uploaded set files.
- Support marks (section 3.8) built into the catalog from `supported-sets.txt` and a new single-card list, starting
  with the backlog's cube list; the server refuses a pod from a set that can't be drafted, by the marks.
- Tests: with a small synthetic MTGJSON (no real card data): the build, the same input giving the same checksum, a pin
  mismatch stopping the release script, the endpoints, set packs from the catalog.

### Step 1b: Android uses the server catalog (built)

- The app installs the server's catalog (checked against its built-in checksum) as its card records, greys what reads
  them while it installs (section 3.6), and shows the support marks (section 3.9); set pods send only the set code.
- MTGJSON downloads, incremental updates, per-set files, the set upload and its limits are gone.

### Step 1c: the first pin moves to MTGJSON's latest build (built)

- The pin names MTGJSON 5.3.0+20261109; `setSupport` and a model comparison against the old file found no change on
  supported cards. Steps 1a–1c go to prod together, before any web work (owner approves that release as usual).

### Before step 3 (owner, 2026-11-11)

Step 2 (the shared UI module, `app-ui`) is built: the UI is in `app-ui`'s commonMain and compiles for Android and wasm.
Two batches come first, in this order:
- The 2026-11-11 playtest and gallery notes (`docs/backlog.md`, "Ready to build").
- Library upgrades, kept apart from step 2 (owner): the newest compose-bom with its matching Compose Multiplatform,
  CMP Material3 no longer held at 1.9.0, the androidx.savedstate 1.3.3 pin in `androidApp/build.gradle.kts` removed,
  a Coil built for current CMP (check that Coil links for wasm), other due updates; changed screenshots re-recorded and
  added to the gallery for the owner; locks written plain and with the Firebase identifiers.

### Step 3: web shell (phone layout) (built)

- Built in 3a–3c (2026-11-12): browser storage, card records and pictures; the browser socket and single-thread paths;
  the `webApp` module and the served build (`WEB_APP_DIR`, set nowhere in prod yet), the Archidekt proxy, stale-tab
  reload, first load, addresses, history, on-screen Back, Esc and the close watcher; behaviour in
  `docs/features/web.md`. The browser smoke is e2e's `WebSmokeTest` (Chrome through Playwright).
- Left for later steps: the chat side panel's close button and browser chrome on the table and draft screens (§8.3,
  §8.5: step 4 and the draft's step), haptics (§8.7), the engine in a Web Worker (step 4; it runs on the page's thread
  meanwhile, for a game's lobby).

### Step 4: the game table on the web (phone layout)

- The engine in a Web Worker behind the message boundary (section 1.3); Android uses the same boundary on its
  background thread. The table, hand, panels, combat band, timeline, chat in the browser; browser chrome kept off the
  table (section 8.5); haptics where available.
- Tests: the existing e2e suite already drives the shared UI in the JVM, so it keeps covering the table; the browser
  smoke gains "open a game, make one move"; a mixed e2e: one player on Android code, one on the web build's code path.
- Built in 4a (2026-11-13): the boundary (`automatum`'s `org.asyncttwiz.automatum.boundary`: requests and answers,
  the engine side's boards as a snapshot then events, the page's staleness ledger, an in-process and a message channel),
  Android's board planning (hand plays' trial casts, card plays) on it in-process, and the worker (`webApp/engine`,
  served as `engine.js`; a test build at `#engine-bench` measures it). Not yet behind it: the device's own work
  (End turn, passes, own actions, server events, projection, which Android's main thread waits for synchronously) and
  the cast, combat and shared-flow steps; the web page still runs those on its own thread.
- Built in 4b (2026-11-13): games open in the browser (the placeholder gone; WEB-007); the page starts the worker and
  asks it for each board's hand plays and card plays (`EngineBoundary.remote`, a snapshot per board; a reader that can't
  wait works them out on the page); browser gestures off the table and the draft (WEB-011), haptics (WEB-012), Esc and
  Back leaving the table and draft (WEB-004). Tests: `WebEnginePlaysTest`, the browser smoke's move, `WebMixedTest`.
  Not yet: boards sent as events instead of snapshots, the device's own work in the worker.
- Built in 4c part 1 (2026-11-13): the engine's results come back as callbacks on both platforms (`EngineWorker.call`'s
  `done`, `early`'s `released`; Android's engine thread is waited for as before, so the callback runs before the call
  returns, in the same frame; `await` is the result-returning form for readers that need it now); `EngineGame`'s own
  actions and the table's routes use the callback forms; boards go to the worker as the events from the one it holds
  (`BoardLines`, `BoardTransfer.since`; a snapshot first, after a reload, or when the worker lacks the base). Not yet:
  the device in the worker (its state, `Shown` sent across, the page's own copy of each board's engine state), the
  device's reads still waited for (the timeline's and Undo dialog's boards, `read`, `repro`, the board values' shared
  flow and combat), and the cast, combat and shared-flow steps.
- Built in 4c part 2 (2026-11-13): published boards across the boundary (`ShownEncoder` on the engine side,
  `ShownDecoder` on the page; `automatum`'s `ShownWire`): events, card data, a board's cards and lists sent once and
  referred to after; each board's engine state the page's own copy (`BoardMirror`), moved by the events from the line it
  was last sent to (an open draft's changes, a motion step's walk applied on a copy), the engine side moving its own copy
  of it first (`MirrorShadow`) and sending a state whole only where the page's copy can't follow. The device's reads the
  table waits for have callback forms (the timeline's and Undo dialog's boards, the undo request's board, older history,
  `read`, `repro`, the board log and outbox for reports and closing); Android answers them in the same frame as before.
  Measured (`#engine-bench`, headless Chrome, a 120-object board): the page takes a board after a pass in 1.7 ms (3 KB),
  one after End turn with its 8 motion steps in 18 ms (34 KB), the game's first board whole in 89 ms (once per game).
  Not yet: the device itself in the worker (its state and every request for its actions and server events: `EngineGame`
  and what it uses must first leave the Compose module, since the worker builds without it), and the flows step (the
  board values' shared flow and combat, casts, `onShown`'s staging closure).

### Step 5: desktop table

- Layout choice and the Layout setting (section 4); the desktop table: top row with phase track, right dock (Stack list,
  chat), larger hand, panels as in landscape; mouse (section 5: click = tap, right-click toggles Inspect with changes **[IN DOUBT: the Inspect input is open, §12 item 10; build no desktop Inspect input until it is decided.]**
  over a dimmed table, right-click on controls = hold action, hover quick look, hover chips with Autotap outlines, wheel
  zoom, drags); hotkeys, the "?" sheet and rebinding in Settings, kept on the account (server: a key-bindings field in
  the account settings LOB-113 uses; section 6); card sizes per layout.
- Catalog: `web.md` entries for layout choice, mouse, hover chips, hotkeys and rebinding, dock, phase track.
- Tests: screenshot tests of the desktop table at 1440×900 (D1–D6 states), e2e flows in the desktop layout with mouse
  and key input (the JVM harness renders it at desktop size).

### Step 6: desktop lobby, decks, drafts

- Permanent side menu shell, wider lists, two-pane deck and cube editor (list left, search right, D8), draft pick with
  click keep / right-click burn, trays at the right and the picks strip (D9), edge zones in Build, Prepare and the
  Cards tab, no Build review on desktop, Stats with more columns, settings rows (D10). Hotkeys K/B/U, ←/→/↓, Ctrl+S.
- Tests: screenshots per screen at desktop size; e2e: build a deck in the two-pane editor; draft a pick with clicks and
  keys; move a Build card by its edge zone.

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

- Phone browsers may be slow for comfortable play; accepted (decision 20), optimised later if people use phone web.
- Compose for web is Beta: upgrades may need small fixes; iOS Safari is untested (no device).
- Step 2 is large and collides with any concurrent Android work.
- Bandwidth from the Pi: the app (~3.8 MB) and catalog (~12–13 MB) per new device or version, cached by Cloudflare under
  content-hashed names.
- Web e2e on a canvas: kept small by testing shared UI in the JVM harness and only smoke-testing the real browser.

### Possible follow-ups (not planned)

- The server works out card definitions from its own catalog instead of storing the ones clients upload with decks.
- A desktop layout for Android tablets and Chromebooks.
- A keyboard-driven Build review on desktop (Space keeps, other keys move) if the owner wants it after testing.
- Phone-web speed work (measure, then optimise the engine's hot spots such as `ObjectId.Card.equals`).

## 12. Open questions for the owner

All design questions are answered (2026-11-07, round 3 the same day). Claude's choices to confirm in review:

1. Catalog file format: gzip-compressed JSON lines (1a).
2. Steps 1a + 1b may go to prod before any web work (1b).
3. Superseded 2026-11-09: no catalog version on requests; the one-version rule covers it (section 3.5).
4. Step 2 runs when no other Android batch is open (2).
5. The real browser is only smoke-tested (Playwright on Chrome); everything else is tested through the shared UI in the
   existing JVM e2e harness, at phone and desktop sizes (3–5).
6. Hover chips: a battlefield card with one usable ability shows that one chip too, so hover always tells what a click
   does (section 5).
7. Edge zones outside Build: in the deck editor and Prepare the right zone is the next section, the left zone the
   remaining one; cubes (two sections) only a right zone (section 5).
8. Draft pick: a ⋯ button on the hovered card opens its menu, for Refund Librarian (section 5).
9. Rebinding: taking a used key leaves the other action without a key, with a note; Esc, Enter and mouse buttons
   fixed; one key per action (section 6).

10. **Open (owner, 2026-11-11): right-click Inspect may be misspecified.** Inspect must work the same way on the table and
    the draft screens, and the draft uses right-click to burn (decision 25), so decision 14 is in doubt. Proposal: hold Alt
    over a card inspects while held, a tap of Alt pins it (Arena uses Alt; check the browsers' Alt-key menus), Shift as
    the fallback; right-click stays burn in the draft and the hold action on controls. Decide at the wireframe review,
    before step 5; build nothing of desktop Inspect until then.