# Profiles and avatars

## Status

**Owner-approved design, 2026-10-26.** Not yet implemented. Phase 1 of
[cubes, pods, and drafting](cubes-pods-and-drafting.md), useful on its own.

## Catalog download

- At the **first app launch**, before login, a dialog explains that the app needs
  the card catalog to work and shows its approximate download size, with two
  choices: **Download** and **Exit**. There is no "later". This replaces the
  optional startup download prompt. Incremental updates and the manual full
  re-import in Settings stay as they are.
- Registration and login are usable while it downloads; the download and import
  continue in the background with visible progress.

## Avatar

- An avatar is a card's **art crop**, shown in a circle. The server stores it on
  the account as a card printing reference (Scryfall ID and face). Other players'
  phones load the art crop through the existing artwork repository; they do not
  need the card in their catalog.
- Without an avatar, a colored circle with the username's initial is shown.

### Choosing

- After the first login of a new account (and once for existing accounts without
  an avatar), a **Choose your avatar** step shows **6 random legendary creatures or
  planeswalkers** from the catalog, a dice-icon **Reroll** button, and a **search by card
  name** field (any card with art). **Skip** keeps the initial-letter circle.
- Until the first catalog finishes downloading/indexing, the choices area says
  avatars need the catalog and shows phase/progress. Choices appear automatically
  in the same area when ready; Skip remains usable.

### Settings → Profile

A pen icon beside the avatar/username opens one dialog with a large current
avatar, dice reroll, choices, search, username, Cancel and Save; no Skip or clear
action. Username must stay unique; login still uses email. Save applies locally
and closes immediately, reverting rejected changes with a short error. The two
existing profile endpoints save independently; an accepted username survives an
avatar failure. Catalog availability uses the same in-place block as first run.

## Where avatars appear

- Pods: member strip with status badges.
- Game lobby: both players, with the Ready tick.
- Games list rows and Quick continue: the opponent.
- Contacts, contact search, and invitations.

Wherever a username is shown next to another player, the avatar is sent with it.
