# Ordered game service

`GET /v1/socket` upgrades to WebSocket on the public listener, using the normal
bearer Authorization header (never a URL token). Use `wss` through the HTTPS proxy.
Invitation decisions, preparation, gameplay, chat and history/lifecycle changes
share this path. Initial create+invite is the [HTTP exception](LOBBY.md).
Exact message/action types and strict JSON codec live in
[`GameWire.kt`](../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/GameWire.kt).

## Session and command lifecycle

1. Send `hello` with installation/process UUIDs and supported protocol/engine/read
   versions; wait for `welcome`. Incompatible clients receive `upgradeRequired`.
2. `openStream` names a game and volatile stream UUID. `streamReady` supplies its
   decimal fence generation. One installation/account/game has one current stream;
   other installations remain independent.
3. `subscribe` requests revisions and a current `head`. Revisions carry accepted
   state independently of results. Follow HTTP pagination for missing suffixes;
   retained-tree baselines/history provide full synchronization. Head heartbeats
   occur every 30 seconds; 90 seconds without traffic closes the socket.
4. `command` captures request UUID, game/round/play scope, typed action and current
   stream generation. The server derives the actor. Requests may be pipelined.
   `receipt` means durable admission, **not acceptance**; `result` is the original
   outcome/correlation, not a state snapshot to install over newer revisions.
5. Same-process reconnect uses `resumeStream` with the same IDs and at most 256
   retained uncertain requests. It fences the old connection before reporting
   pending/final/not-admitted status. Retry originals in order under the new
   generation. HTTP absence alone never proves non-admission by a live socket.
6. Process restart opens a fresh stream and synchronizes accepted state. There is
   no crash-durable outgoing journal; Logout clears queues and fences callbacks.

## Reads and privacy

All routes require current authentication and game participation:

| Route | Result |
| --- | --- |
| `GET /v1/games/{id}/revisions?after=decimal` | Ordered 100-record suffix and `next` cursor |
| `POST /v1/games/{id}/results:lookup` | Statuses for at most 256 actor-owned request IDs |
| `GET /v1/games/{id}/results/{requestId}` | Original request status/outcome |
| `POST /v1/games/{id}/baselines` | Baseline manifest plus statuses for supplied request IDs |
| `GET /v1/baselines/{id}/manifest` | Retained baseline manifest |
| `GET /v1/baselines/{id}/chunks/{index}` | Bounded gzip baseline chunk |
| `GET /v1/games/{id}/plays/{playId}/mutations?after=decimal` | Retained history page |

Pending/declined recipients receive invitation metadata, not private preparation.
Accepted participants receive independent game definitions, not access to the
other account's saved decks. Request identity collisions never disclose foreign
outcomes. Cancellation removes ordinary replica access while allowing recovery
of the actor's original outcomes. Expired baselines return 410; resynchronize.

## Preparation and play

- Accept/Decline captures the exact pending attempt; Reinvite requires the declined
  attempt. Old rounds reject; same-ID retries recover original results. Accept is
  not Ready. Disconnecting does not reset preparation.
- Select deck captures an owned saved ID/revision and installs independent metadata,
  definitions and concrete card identities. A changed/deleted source rejects;
  later saved edits/deletion cannot rewrite the game copy. Mainboard starts in
  prepared Library; Sideboard stays separate. Transfers preserve concrete UUIDs.
- Deck selection/transfers and changed starter choice clear only the actor's Ready.
  Starter must be explicitly chosen (participant or Random). The second applicable
  Ready starts once, atomically; disconnecting does not withdraw Ready.
- Initial play preserves mainboard UUIDs, shuffles from retained server random
  inputs, starts life at 20 and turn 1 Beginning/Untap, with no automatic opening
  draw. Sideboard is retained for later preparation, not a playable zone.
- Shared pure rules apply manual gameplay operations and history changes. The
  server records random outcomes and accepted branches; reads/retries never reroll
  them. Game copies, accepted state and original outcomes persist independently of
  local prediction, selection, camera or review cursor.

## Durability and workers

Per-game FIFO dispatch precedes asynchronous admission. One writer-fenced SQL
transaction binds global request identity, canonical intent and ingress ordinal.
Duplicate pending/final IDs allocate no new ordinal. Four execution workers claim
games with `FOR UPDATE SKIP LOCKED`, executing each lowest pending ordinal; state,
outcome, revision and outbox commit together. Publication happens outside SQL and
may repeat; socket delivery is not durability. Recovery resumes pending work.

Bounds include 1 MiB/depth-32 commands, 1024 pending requests/game and bounded
dispatch/socket mailboxes. Overflow refuses admission or closes slow consumers;
it never discards committed work. All workers join readiness/recovery/shutdown.
Schema changes use explicit forward migrations, never startup auto-migration.

```sh
./scripts/gradle.sh :protocol:jvmTest :server:test \
  --tests '*GameDurabilityTest' --tests '*GameSocketTest' \
  --tests '*GameStartTest' --tests '*PreparedDeckTest'
```
