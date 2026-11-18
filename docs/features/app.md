# App

Settings, app updates, What's new, problem and crash reports, card records and the card catalog Automatum supports,
card pictures, and how screens and dialogs look.

## How it works

Settings, opened from the main menu or the side menu, holds your profile, game, table, playback and Automatum choices;
some are kept on your account so every device has them, others on the device alone. The Android app always runs the
newest version: when a newer one is out it shows only an update screen until it is updated, and after an update What's
new lists what changed. Problem reports, crash reports and other error reports go to the server with the app's log, so
a problem explains itself. The card records every screen reads come from the server's card catalog, which also says
which cards and draft sets Automatum supports; the app downloads them by itself and stays usable meanwhile. Card
pictures come from Scryfall when first needed and are kept on the phone.

## Settings

- **LOB-079** Settings (main menu and side menu) is grouped into Profile, Games, Table, Timeline playback, Automatum, Card records and About & account, with dividers and compact rows. (r41)
- **LOB-080** Games → "Deck visibility": Visible, Hidden or "Show after first game" (default), saved on your account and used as the start value of "Show deck name & art" in new games. (r31)
- **LOB-146** Games → "Your move sound": Off, Plip (the default: on for every account), Drop, Chime or Tock, short soft sounds of the app's own, each row with a play button to listen to it; kept on the server for your account (every device plays the same). It is the sound of the Your move cue (TRN-075) while a game's table is open. (r64)
- **LOB-081** Table → Card move speed (TBL-038), the Event text switch (on by default; the other player's open choice still shows, HIS-075), and Card size and Card text size (TBL-036) with "Customize per area"/"Customize per text", tap-to-type values and a live sample; these are stored on the device. (r52)
- **LOB-147** In a browser, Settings has a Keyboard group after Table: "Keyboard shortcuts" (WEB-035), kept on the server for your account, so every browser you sign in to has the same keys; the Android app has no such group. (r73)
- **LOB-113** Automatum → "Response mode in new games" (Control, Normal or Quick, the default, each with what it means), "Assume resolves in new games" (Never, Normal, When they have no mana, the default for every account, or Always, each with what it means, HIS-096) and "Autotap mana sources" (on by default, STK-024). (r60)
- **LOB-228** The Automatum settings (LOB-113) are kept on the server for your account (every device shows the same) and given to each new game; each game's own are changed from its quick actions (TRN-059). (r60)
- **LOB-083** About & account → What's new, Report a problem, Send error reports (LOB-104), the version line "Version <name> (<code>) <type> · commit <hash>" (selectable), and Log out. (r35)

## App updates

- **LOB-084** (Android; the web reloads itself instead, WEB-006) When a newer version is published, or a game needs a newer app, the app shows only an "Update required" screen with the version and an Update button; it can't be used until updated. (r18)
- **LOB-085** (Android) The update downloads (from GitHub first) with a progress bar "x / y MB", keeps the screen on, continues where an interrupted download stopped and is checked before the installer opens. (r28)
- **LOB-086** (Android) A failed update says why in plain words and offers "Download from server" and "Download from GitHub" with install instructions, plus "Send problem report". (r28)

## What's new

- **LOB-087** After an update, "What's new" opens on the app's first frame with that update's notes grouped as New sets (the sets Automatum newly supports, by name; left out when there are none), New, Changed and Fixed, newest first; a fresh install shows nothing. (r22, r62)
- **LOB-088** What's new never pops up over an open game or pod or when a notification is opening one; it can be reread any time from Settings. (r21)

## Problem and crash reports

- **LOB-089** "Report a problem" (Settings, the update screen, or Table actions in a game, TRN-010) sends an optional description with the app's recent log, the last game's reproduction bundle and the device model, but no passwords or email addresses, and shows "Report sent — code N". The server keeps the newest 20 per account and 200 in total. (r40)
- **LOB-229** The app's log also records, in plain words, what each game showed the player: the whole board at the game's opening, at each new turn and when the report is sent, with what they could do then, and in between the cards entering and leaving their hand, the battlefields, graveyards, exile and the Stack; of the other player's hand and of libraries only how many cards. (r40)
- **LOB-230** The app's log also records each press of the main button, a tap or a hold, with what it said and the step; each undo, undo request and rewind with where from and to, what it took back or why it was refused; each move on the timeline; and each decision asked, with the cards offered, each tap on it and why an answer went nowhere. (r40)
- **LOB-231** A problem report's reproduction bundle holds the last game's card rules, its events from a recent point and the latest log entries, which may include hidden cards. (r40)
- **LOB-090** Test builds always, and production builds when reports are on (LOB-104), capture crashes (with the last game's log as in LOB-089) and upload them at the next start, and also upload slow actions, stutters and reconnect episodes, with no passwords or email addresses. A generic stand-in the app shows because it couldn't name or describe something ("Trigger 1", "An object", a card without its name) is an error: logged and sent by itself at once as a report with the app log and the game's Automatum bundle, once per kind per run, while the player sees the best text there is (hidden cards shown as hidden by design are not). The server keeps per account the newest 20 crash, 60 slow-action/reconnect and 20 stutter reports, 500 in total and nothing older than 30 days. (r40)
- **LOB-241** A refusal shown with a catch-all reason (a greyed ability chip's "Not now: a cost it can't pay, or nothing to target.", a card menu's "Automatum doesn't allow that now …", TBL-123) still shows it, and is an error reported as LOB-090's stand-ins are, once per card and ability per run, with the card, the ability and Automatum's own refusal; a shake without words (TBL-118) is not.
- **LOB-104** Production asks once per device, after sign-in on the main menu or Games list (never over a game, What's new or another prompt), "Send error reports?" with No thanks / Send reports; closing it means No. (r35)
- **LOB-232** Settings → About & account → "Send error reports" changes the error reports choice (LOB-104) at any time; turning it off deletes reports not yet sent. Test builds show the switch on and locked, "Always on in test builds". (r35)

## Card catalog and card records

- **SYS-081** The server serves one card catalog, built at release from the pinned MTGJSON build (the repository's catalog pin) and deployed with it; the one before stays beside it. The app's card records come from it (LOB-091).
- **SYS-203** Open without sign-in: the card catalog's current version (its checksum, size, MTGJSON build and counts) and the file by its checksum, cached for good.
- **SYS-082** The card catalog (SYS-081) says which cards and draft sets Automatum supports, worked out when it is built with that release's parser and engine. The server decides set pods by it (DRF-162), and the app shows it (LOB-115, LOB-144, DRF-088, DRF-161).
- **SYS-204** Automatum supports a card when a printing of it is in a supported set or it is on the list of cards supported one by one (first the owner's Powered Synergy Cube), and Automatum finds nothing in it left to the players; a draft set when every card its packs can hold is supported. A reversible printing (the same card on both sides) counts as the card it shows.
- **LOB-091** Card records come from the ATW server: the card catalog the app was built for (SYS-081). On start, when the installed records aren't that catalog (a first launch, a new app), the app downloads and installs it without asking, before or after sign-in. (r50)
- **LOB-095** The card records are always the catalog the app was built for: installed records of another catalog or format are replaced with it at startup. (r17)
- **LOB-233** Sign-in works while card records install, with "Card records updating · 43%" and a bar at the top (in the desktop layout lying over the top of the page, so nothing under it moves when it comes or goes). (r50)
- **LOB-234** A failed card records download is tried once more by itself, then the line says "Card records couldn't be downloaded" with Retry. (r50)
- **LOB-094** How far an installation is reads as one share of the whole: the download is its first fifth, adding the records takes it to 60%, and preparing card search goes on from there to 99%. (r55)
- **LOB-093** Settings → Card records shows the installed date and record count; while card records install, "Card records updating · 43%" with a bar; after a failed download, that line with Retry. (r9)
- **LOB-096** While card records install, the app stays usable (menu, Settings, profile, match history, the games list); every control whose action reads card records stays visible but greyed: opening a game, New game, accepting a game invitation, Import deck, New cube. (r17)
- **LOB-235** While card records install, the Games and Manage decks screens say "Card records updating · 43%" under their title. A failed download leaves the controls that read card records (LOB-096) greyed, the line offering Retry. (r17)
- **LOB-143** While card records install, content that needs them shows a placeholder with LOB-096's line in its place: a game's table (one opened from a notification), a saved deck's or cube's cards, the Sets tab of a pod's source, pod picks, and the avatar choices.
- **LOB-110** Card records (the server's card catalog, LOB-091) include rarity, format legalities and colour identity for search (LOB-108). (r35)
- **LOB-125** Card records (the server's card catalog) include which tokens each card makes and which cards make each token, for token pictures (SYS-072). (r57)
- **LOB-150** Card records keep, beside a card's Oracle name, the name a printing gives it where it is printed under another one (Secret Lair drops, Universes Within), for each face of a card of several faces too. (r76)

## Card pictures

- **LOB-097** Card pictures come from Scryfall when first needed and are kept on the phone: a kept picture, and the lookup that found it, shows at once and is never checked again (a failed one keeps retrying, LOB-098). (r61)
- **LOB-236** One card picture is kept per printing, face and size (and custom picture), whichever screen asked for it and through catalog updates, up to 1 GB, the ones used longest ago going first. (r61)
- **LOB-237** A deck's pictures start downloading when you save it and when a game opens. (r61)
- **LOB-098** A picture on screen keeps retrying until it loads; on-screen pictures go before background downloads. (r21)
- **LOB-099** Inspecting a card whose picture is still loading shows the placeholder card with its name and "Downloading artwork X of Y", or "Artwork unavailable". (r52)

## Screens and dialogs

- **LOB-100** Screen titles are one line in the same place on every screen; New/Import buttons sit at the top right of Games, Pods and Manage decks. (r21)
- **LOB-101** Dialog buttons sit in one row on the right with the main action last and delete actions in red. (r47)
- **LOB-238** Every button in dialogs, the lobby, decks and cubes and drafts shows an icon before its text (a cross on the dismiss button, a check, a bin on delete actions, or the action's own icon), never text alone; a button showing only an icon or symbol (the draft grid's arrows, a basic land's − and +) says what it does when held. (r47)
