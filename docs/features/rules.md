# Cross-cutting rules

Rules that every feature follows: what the product is, who decides game state, how sync, errors and performance behave, version compatibility, the player-access policy and shared UI conventions.

### Product scope and principles

- **SYS-001** A two-player cooperative tabletop for Android, Magic: The Gathering first. The app records and shows what happened and the players interpret the rules: there is no rules referee or priority tracker, and the app never works out costs, targets or resolution. Turn/step tracking, combat and Stack views and quick actions are conveniences that run only when a player starts them. (r1)
- **SYS-002** Change only what was requested. Moving a card keeps its state unless a visible, consistent exception applies (for example, leaving the battlefield resets it). Nothing is guessed from card names or rules text. (r13)
- **SYS-003** Finished edits save and sync automatically. There is no Send button (chat messages excepted), no required explanation and no routine confirmation. (r1)
- **SYS-004** Each history step is one change, such as moving or tapping one card. Bulk controls record a sequence of single changes, and one invocation still counts as one Undo. (r15)
- **SYS-005** Personal UI state stays on the device and is never shared: camera, selection, open menus, timeline review position, playback and card-size settings. (r4)
- **SYS-006** Undo is only a convenience. No feature works by undoing something (withdrawing a result suggestion, for example, is its own event), so a refused undo never blocks a feature. (r18)

### Server authority and sync

- **SYS-007** The server decides all shared game, lobby and history state, Undo/Redo included. The app applies each action locally right away as a prediction, not as a separate private game. (r6)
- **SYS-008** The app sends actions in order and keeps several in flight without waiting for replies. The server handles them in arrival order, and each one succeeds or fails on its own: one refusal never cancels other valid actions. (r1)
- **SYS-009** When the server corrects a prediction, only the real difference animates. Accepted moves and still-valid local moves stay where they are, with no visible rewind and replay. (r1)
- **SYS-010** A player action that needs several game actions (for example, a double-tap cast followed by its resolve) goes to the server as one all-or-nothing batch. (r15)
- **SYS-011** Ordinary edits apply to the current state. Only card moves (the source must still match) and Stack additions (the Stack must be unchanged) are refused when the board changed underneath them. A refusal never creates a branch. (r1)
- **SYS-012** The active branch changes only through a deliberate branch action, never as a side effect of play. An ordinary move sent just as the opponent branched is refused with "Your opponent went back to an earlier point — your last action wasn't applied." (?) (r33)
- **SYS-013** Playing and viewing a game need a connection; there is no offline queue of actions and no offline mode. An action made while the connection is briefly lost waits up to 3 s and is sent once, as soon as the connection is back. (r1)
- **SYS-014** After a reconnect, actions that were already sent are recovered automatically: no duplicates and no manual resend. If the app process dies, it restarts from the server's state, and actions it had not sent yet may be lost. (r1)
- **SYS-015** Recovery from missed updates is automatic. There is no manual "download history" or resync button. (r15)
- **SYS-016** Losing the connection shows nothing by itself. Only when an action has waited 3 s for the server does "Reconnecting…" / "Game actions unavailable." appear, with a "Back to games list" button; from then on actions are refused until the connection is back. The dialog can be dismissed; you stay on the table and actions are still refused. (r1)
- **SYS-017** Opening a game shows "Loading table…" until the table loads or really fails. It never flashes "Table unavailable" first. (r13)
- **SYS-018** Lobby buttons stay enabled while the app reconnects. A tap waits for the connection and then goes through. (r21)
- **SYS-019** Coming back from the background never bounces or jumps the layout, and the interruption itself shows no error. (r7)

### Connection failures and errors

- **SYS-020** Automatic refreshes of data already on screen fail silently and keep the shown data. After 30 s of continuous failures, a calm "Can't reach the server · retrying" note appears; taps pass through it, and it disappears on the next success. (r22)
- **SYS-021** A first load (nothing on screen yet) keeps its loading indicator and retries quietly for about 10 s before it shows Retry. (r22)
- **SYS-022** Requests the player makes (taps, saves, Retry) report their failures at once. (r22)
- **SYS-023** Background checks never show loaders or spinners that push content around. Player edits such as settings hide the wait: no views are disabled while saving. (r17)
- **SYS-024** Real errors appear as a floating message, never as inline red text that shifts the layout (fixed form slots excepted). A first load shows a full-area placeholder, the layout keeps the same geometry when its state changes, and list order stays stable. (r15)

### Performance

- **SYS-025** Every action, Undo/Redo included, shows its result locally before the server answers: under 20 ms from input to applied state, with 80 ms as the hard ceiling. Waiting on the server for a visible result is a bug. (r6)
- **SYS-026** Games reach 10,000+ actions. Normal play, sync, navigation, undo and saving never replay or rebuild from the start of the game. Their work depends only on the change, a fixed window (such as 50 later events for undo) or what is visible. (r7)
- **SYS-027** Every event stores exactly how to apply and revert it, checked against the current values. Timeline stepping, undo and opponent animations move step by step from the current position. (r6)
- **SYS-028** Background lobby refreshes never redraw the open game table. (r30)
- **SYS-029** Every single-tap menu opens immediately, without waiting out the double-tap window. (r8)
- **SYS-030** Test and debug builds record every action that took over 20 ms or had to wait for the server, plus stutters and reconnects, and upload them as reports. Production builds do not. (r30)

### Versions and compatibility

- **SYS-031** Game rules are versioned. Each recorded event keeps the behaviour and history icon of the rules version it was made under, so old games play back unchanged. Event texts are rebuilt by the app. (r33)
- **SYS-032** If a game has events from newer rules than the app knows, the app shows "Update the app to continue this game" and the update screen. If the server is the older side, it shows "Server update pending" and retries every 30 s. (r16)
- **SYS-033** Only one compatible set of apps and server is kept working: the server serves only the current app protocol and rules, so a server change may require the newest app, and older apps then only show the update screen (SYS-032). (r35)
- **SYS-034** Unknown event kinds or icons from a newer version never crash the app; they show a fallback. (r31)
- **SYS-035** If local data was written by a newer app, the app says "Local data needs a newer app. Update the app without uninstalling." It never erases local data when storage fails to open. (r1)
- **SYS-052** If the server can no longer read one saved game, pod, pod preset or card (for example after an update changed its format), or one saved game fails the server's damage check at startup, only that item is affected: it is left out of the games, pods and presets lists (a deck whose cover card can't be read just shows no cover), opening it fails like a temporary server problem, and a pending action that can't be read is refused. The server still starts, other games and players carry on, and nothing is deleted or rewritten; the server logs which item it was. (r35)

### Player access policy

- **SYS-036** Every gameplay action is available to both players, on either player's cards, zones, panels and piles. The "phone test": you could run the game for an opponent who only sees the board and tells you over the phone what to do. Hidden information stays hidden by default, so reaching it for them (e.g. their hand) takes an extra action. (r30)
- **SYS-037** The only allowed differences: quick actions and defaults can fit the acting player, hidden information the opponent owns is not shown by default, and an action may ask for an extra confirmation. Who is acting never hard-blocks an action. (r30)
- **SYS-038** These rules still hold: cards go to their owner's hand, library, graveyard, exile and Temp piles (drops redirect and the preview shows it; owner is separate from controller), only game members can act, you undo only your own actions, and personal proposals belong to their author (withdrawing a result suggestion, your own status). (r30)
- **SYS-039** The app hides hidden information on screen only, a deliberate trust-the-client choice. Normal or accidental inspection never shows a hidden side; seeing it takes a deliberate, recorded action. (r21)
- **SYS-040** Menu actions are always shown and are disabled when they don't apply. An action is replaced only by its state twin (Mute ↔ Unmute, Hide ↔ Show). Each menu context has one fixed layout with no hidden buttons and no empty slots. (r28)
- **SYS-041** The battlefield feels infinite: no visible edge inside the pan area, and drags may place cards in any column, including left of the first. Only how far you can pan away from placed cards is limited. Auto-placement starts at the first free slot from column 0. (r31)

### Shared UI rules

- **SYS-042** Every context menu on the game table uses one floating popup: square, opaque, bordered buttons on the board, with no sheet, title or Close button. Tapping outside or pressing Back dismisses it. Only buttons and chips count as inside; gaps count as outside, and outside taps also pass through to the table. The phase/step picker is a dialog instead. Menus outside a game (lobby, pods) are ordinary drop-down menus. (r11)
- **SYS-043** Only one popup can be open at a time; tapping with several fingers never opens more. (r19)
- **SYS-044** Every custom button shows press feedback. (r4)
- **SYS-045** Holding a short-label button shows its full name in a tooltip above the button (below it near the top edge), never under the finger. (r3)
- **SYS-046** Value controls everywhere use "− [value] +" with one square value field. (r13)
- **SYS-047** Input dialogs focus their field, select its text and show the keyboard. The keyboard's Enter key does what the screen's main button does. (r24)
- **SYS-048** Dialogs on regular screens share one style: a title, a close button and Cancel/main action buttons, never a lone left button. Gameplay, inspect and special popups are excluded. (r21)
- **SYS-049** Every hold preview behaves like the in-game one: it opens at once, closes on release, and early finger movement goes to the scroll or swipe underneath. (r30)
- **SYS-050** System Back first closes open menus, dialogs, panels and modes. It never undoes game actions. On the bare table, leaving needs a second Back within 2 s ("Press back again to leave"). Top-level menu destinations reset the back stack. (r30)
- **SYS-051** Shared card type order: Creatures, Planeswalkers, Battles, Instants, Sorceries, Artifacts, Enchantments, Lands, Other. Used by deck preparation (LOB-047, DRF-126) and saved decks (LOB-056). (r24)
