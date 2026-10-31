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

## Specified, not started

- Card lists: decks and cubes share one model (Main/Side/Maybe; cubes Main/Maybe), one saved view, one editor with catalog search, and pods get "Prepare cube". Spec: `docs/specs/card-lists.md`. (LOB-047, LOB-054–LOB-065, DRF-001–DRF-010, DRF-050)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
