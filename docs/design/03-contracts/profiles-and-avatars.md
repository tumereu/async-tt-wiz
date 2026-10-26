# Profiles and avatars

## Status

**Owner-approved design, 2026-10-26.** Not yet implemented. Phase 1 of
[cubes, pods, and drafting](cubes-pods-and-drafting.md), useful on its own.

## Catalog download

- At the **first app launch**, before login, show a dialog explaining that the app
  needs the card catalog to work, with its approximate download size and exactly
  two buttons: **Download** and **Exit**. There is no "later" option. Exit finishes
  the activity; the dialog appears on the next launch until a catalog exists.
  Download starts the download. Incremental updates and the manual full re-import
  in Settings stay as they are.
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
  planeswalkers** from the catalog, a **Reroll** button, and a **search by card
  name** field (any card with art). **Skip** keeps the initial-letter circle.
- If the catalog is still downloading, the step shows progress and opens when it
  is ready.

### Settings → Profile

Change the **username** (must stay unique; login uses email, so it is unaffected)
and the **avatar** (same chooser).

## Where avatars appear

- Pods: member strip with status badges.
- Game lobby: both players, with the Ready tick.
- Games list rows and Quick continue: the opponent.
- Contacts, contact search, and invitations.

Wherever a username is shown next to another player, the avatar is sent with it.
