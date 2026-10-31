# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Pods and games use one list order everywhere (screens, menu panels, side menu): yours to act on first (your move; for pods your pick or build), then invitations to you, then the rest. (LOB-022, DRF-012, DRF-013, DRF-014, DRF-015)
- Pods get the same app-side reminders as games while a pick or build waits for you. (LOB-076, DRF-136)
- The saved cube screen shows the cards in the same picture view as saved decks, with more columns by default (number to decide). (DRF-010, LOB-056)
- Pod rows everywhere (main menu Pods panel, Pods screen, side menu) show the side menu's compact stack of the other members' avatars (up to two plus "+N"; only yourself when alone). (DRF-012, DRF-014, DRF-015) Approved text: DRF-012 "The Pods screen lists pod invitations with Accept and Decline at the top, then "MY PODS (N)"; each pod row shows the compact stack of the other members' avatars (up to two plus "+N"; only yourself when alone), the name and status. (r35)" DRF-014 "The main menu's Pods panel lists up to 3 pods, each with the compact stack of the other members' avatars as in the side menu: your pick or build first, then other pods, then invitations (answered in place); it is hidden when empty. (r35)"
- Notifications use a proper single-colour app icon (the logo's silhouette) as their status-bar icon instead of Android's generic info icon. (LOB-072) Approved text: LOB-072 "Three notification channels: Invitations, Game activity and Draft activity. A game notification reads "<game> · vs <opponent>" with "Your move", "<opponent>'s move" or "<opponent> is waiting for you", plus " · new message" after chat, and shows the time of the last event. Every notification shows the app logo as a single-colour status-bar icon. (r35)"
- Side menu game rows show the last activity like every other game list, not the creation date. (LOB-019, LOB-024) Approved text: LOB-019 "A side-menu game row shows the opponent's avatar, the game name, an "Invitation" label on its own line for invitations, and "opponent · status · last activity" (the time if today, else the date) on two lines. (r35)"
- Booster and Glimpse become built-in presets of one pack-draft mode named "Booster": mode chips Booster and Grid; the built-ins are listed first in the same preset list as your own presets and can't be edited or deleted; a pod's mode always reads "Booster". (DRF-020, DRF-032) Approved text: DRF-020 "Mode chips Booster and Grid load that mode's default settings, keeping the sideboard limit and Librarians (Grid always has no Librarians). Glimpse is a built-in preset of Booster, and a pod's mode always reads "Booster", also for pods created as Glimpse. (r35)" DRF-031 "A Presets button on New pod and on the creator's pod setup opens a dialog listing the built-in presets Booster and Glimpse first, then your saved presets, with search; there are no preset chips. (r35)" DRF-032 "A preset stores the draft settings and source under a name; rows read like "Booster · My cube · 3×15", and a deleted cube shows "Cube missing". Built-in presets hold settings only and read "Glimpse · Built-in · 9×15"; choosing one keeps the pod's current source. (r35)" DRF-033 ""Save current settings as preset…" asks a name (up to 60 characters); an existing name asks "Replace preset"; rows can be renamed and deleted (confirmed). Built-in rows can't be renamed, replaced or deleted. (r35)"
- Damage and EOT marks move from Stack cards to combat copies. Approved text: TBL-094 "Buttons that change a card are greyed out outside the battlefield. Stack entries and combat copies can have their own notes and pings; combat copies also set damage and the until-end-of-turn marks on the real card (CMB-003). (r35)" CMB-003 "Combat copies are always upright and can't be tapped. Tapping one opens its own one-page Combat menu, not the normal card menu: Inspect, Note, Ping / Unping, Attack →, Remove from combat, Graveyard, Exile, Hand, Damage, EOT P/T, EOT note, EOT keywords, Deal to target, Deal to target or creatures, Link, Links. Damage and the EOT marks set the real card's values; Graveyard, Exile and Hand move the real card (CMB-004). It has no tap, counter, flip or other battlefield controls (use the real card for those). Double-tap does nothing; hold inspects. (r35)"
- Phase dialog step squares use short one-line names. Approved text: TRN-008 "The phase dialog is a real dialog, not a floating popup. Its title is the step with its phase (e.g. "Combat · Declare attackers"), then the turn player ("Your turn" / "<name>'s turn"); phase labels sit above each row of step squares, and the current step is outlined in the turn player's colour. Step squares use short names that always fit on one line on a phone: Untap, Upkeep, Draw · Main 1 · Begin, Attackers, Blockers, Damage, End · Main 2 · End, Cleanup. (r35)"

## Specified, not started

- Limited engines: Rochester (simultaneous packs), Rotisserie, Winston, Winchester and Sealed, a Picks view of opponents' picks, per-engine player limits. Spec: `docs/specs/limited-engines.md`. (DRF-016–DRF-032, DRF-071, DRF-079–DRF-087, DRF-136)
- Card lists: decks and cubes share one model (Main/Side/Maybe; cubes Main/Maybe), one saved view, one editor with catalog search, and pods get "Prepare cube". Spec: `docs/specs/card-lists.md`. (LOB-047, LOB-054–LOB-065, DRF-001–DRF-010, DRF-050)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
