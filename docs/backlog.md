# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Pod rows everywhere (main menu Pods panel, Pods screen, side menu) show the side menu's compact stack of the other members' avatars (up to two plus "+N"; only yourself when alone). (DRF-012, DRF-014, DRF-015) Proposed text (owner reviews at the end): DRF-012 "The Pods screen shows "MY PODS (N)" (N counts your pods) above one list in the shared pod order (DRF-013): pod rows with the compact stack of the other members' avatars (up to two plus "+N"; only yourself when alone), the name and status, and pod invitations with Accept and Decline. (r35)" DRF-014 "The main menu's Pods panel lists the first 3 pods and pod invitations of the shared pod order (DRF-013), each pod with the compact stack of the other members' avatars as in the side menu, invitations answered in place; it is hidden when empty. (r35)"
- Notifications use a proper single-colour app icon (the logo's silhouette) as their status-bar icon instead of Android's generic info icon. (LOB-072) Approved text: LOB-072 "Three notification channels: Invitations, Game activity and Draft activity. A game notification reads "<game> · vs <opponent>" with "Your move", "<opponent>'s move" or "<opponent> is waiting for you", plus " · new message" after chat, and shows the time of the last event. Every notification shows the app logo as a single-colour status-bar icon. (r35)"
- Side menu game rows show the last activity like every other game list, not the creation date. (LOB-019, LOB-024) Approved text: LOB-019 "A side-menu game row shows the opponent's avatar, the game name, an "Invitation" label on its own line for invitations, and "opponent · status · last activity" (the time if today, else the date) on two lines. (r35)"
- Booster and Glimpse become built-in presets of one pack-draft mode named "Booster": mode chips Booster and Grid; the built-ins are listed first in the same preset list as your own presets and can't be edited or deleted; a pod's mode always reads "Booster". (DRF-020, DRF-032) Approved text: DRF-020 "Mode chips Booster and Grid load that mode's default settings, keeping the sideboard limit and Librarians (Grid always has no Librarians). Glimpse is a built-in preset of Booster, and a pod's mode always reads "Booster", also for pods created as Glimpse. (r35)" DRF-031 "A Presets button on New pod and on the creator's pod setup opens a dialog listing the built-in presets Booster and Glimpse first, then your saved presets, with search; there are no preset chips. (r35)" DRF-032 "A preset stores the draft settings and source under a name; rows read like "Booster · My cube · 3×15", and a deleted cube shows "Cube missing". Built-in presets hold settings only and read "Glimpse · Built-in · 9×15"; choosing one keeps the pod's current source. (r35)" DRF-033 ""Save current settings as preset…" asks a name (up to 60 characters); an existing name asks "Replace preset"; rows can be renamed and deleted (confirmed). Built-in rows can't be renamed, replaced or deleted. (r35)"

## Specified, not started

- Limited engines: Rochester (simultaneous packs), Rotisserie, Winston, Winchester and Sealed, a Picks view of opponents' picks, per-engine player limits. Spec: `docs/specs/limited-engines.md`. (DRF-016–DRF-032, DRF-071, DRF-079–DRF-087, DRF-136)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
