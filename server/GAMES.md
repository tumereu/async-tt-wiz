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
   state independently of results. The head of the open game (`engine = true`)
   carries its full current state (the engine snapshot at the live line's last
   event); a game in the list gets summary-only heads and revisions. Follow HTTP
   pagination for missing suffixes. Head heartbeats occur every 30 seconds; 90
   seconds without traffic closes the socket.
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
| `GET /v1/games/{id}/timeline?through=event` | A page of the active play's main line ending at that event (older timeline history) |
| `GET /v1/games/{id}/chat?before=…` / `?after=…` | A page of the game's chat with the caller's read state |
| `GET`/`PUT /v1/games/{id}/chat/read` | The caller's chat read cursor |
| `GET /v1/games/{id}/history` | The game's match records (`HistoryHttp`) |

Pending/declined recipients receive invitation metadata, not private preparation.
Accepted participants receive independent game definitions, not access to the
other account's saved decks. Request identity collisions never disclose foreign
outcomes. Cancellation removes ordinary replica access while allowing recovery
of the actor's original outcomes.

## Preparation and play

- Accept/Decline captures the exact pending attempt; Reinvite requires the declined
  attempt. Old rounds reject; same-ID retries recover original results. Accept is
  not Ready. Disconnecting does not reset preparation.
- Select deck captures an owned saved ID/revision; the server makes the game's card
  data from its card catalog then (feature catalog SYS-084), so later saved edits
  or deletion don't rewrite the game copy. A changed/deleted source rejects.
  Mainboard becomes the library; Side and Maybe are the player's cards outside the
  game, never part of the library (LOB-141). Transfers preserve concrete UUIDs.
- Deck selection/transfers and changed starter choice clear only the actor's Ready.
  Starter must be explicitly chosen (participant or Random). The second applicable
  Ready starts once, atomically; disconnecting does not withdraw Ready.
- Every play is an Arcanum Automatum game from its first state (SYS-055): the
  server starts the engine with both decks shuffled from retained server random
  inputs and both opening hands drawn, and the game begins with the opening's
  mulligans (TRN-062, TRN-027).
- Players act through `submit` (engine inputs on the event they were made on);
  the server makes the event at the head, confirms or branches at an assumption,
  and refuses inputs the engine doesn't allow (see `GameAction.Submit`). Changes
  made by hand are refused (SYS-069). The server records random outcomes and
  accepted branches; reads/retries never reroll them. Game copies, accepted state
  and original outcomes persist independently of local prediction, selection,
  camera or review cursor.

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
  --tests '*GameStartTest' --tests '*PreparedDeckTest' --tests '*EngineGameServiceTest'
```
