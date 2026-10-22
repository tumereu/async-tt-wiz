# Ordered game service — I09b1

The running Spring service now implements recipient **Accept/Decline** and inviter
**Reinvite** through `GET /v1/socket` WebSocket upgrade. Use the normal bearer
Authorization header, never a URL credential; TLS is supplied by the normal HTTPS
boundary (`wss` outside isolated local tests). There is no HTTP game-write route.
Initial create+first-invite remains the [account HTTP exception](LOBBY.md).

## Client handoff

The shared [typed wire codec](../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/GameWire.kt)
uses JSON `type` discriminators, strict unknown-field/duplicate-key rejection,
expanded semantic defaults and decimal-string revisions/ordinals/generations.
An action uses `{"type":"acceptInvitation"}`, `declineInvitation` or `reinvite`.
The server derives the actor from authentication, never a supplied label.

1. Send `hello` with random process/installation UUIDs and supported protocol major
   1, minor 0, engine 1/read format 1; wait for `welcome`. Commands before agreement
   fail `upgradeRequired`; incompatible versions receive `upgradeRequired`.
2. `openStream` names the game and process-local stream UUID. `streamReady` returns
   its decimal fence generation. One installation/account/game has one current
   stream; different installations remain independent.
3. `subscribe` independently names the accepted revision/read formats. It returns
   up to 100 committed revision records followed by a current `head`. Fetch any
   remaining suffix through HTTP before declaring catch-up. The 30-second head
   heartbeat detects a missed tail; no traffic for 90 seconds closes the socket.
4. Send `command` with request UUID, `scope` (game/preparation/invitation UUIDs),
   typed action, stream UUID/generation and command version 1. Invitation actions
   have no play/continuation/inline definitions. Capture immutable originals before
   sending. Multiple requests can be in flight; no client sequence/ack gate exists.
5. `receipt` follows durable inbox commit and means pending, not accepted.
   `result` contains the original outcome/ordinal/nullable commit revision and
   created IDs, **not a success snapshot**. `revision` separately carries the
   accepted metadata change with base/result revision and request/author identity.
   Incorporate by revision, not by replaying old results over current state.
6. On same-process reconnect/reauthentication, send the same `hello`, then
   `resumeStream` with the same game/stream and at most 256 retained uncertain IDs.
   The dispatcher fences the previous connection before returning authorized
   `pending`, `final` or `notAdmitted` statuses. Retry retained originals in local
   order under the new generation; connection/auth changes are not semantic intent.
   A process restart opens a fresh stream and synchronizes, without recreating lost
   requests or a durable client outgoing journal. Logout clears private queues.

`admissionError` has code/retryability and a request ID when available. Invalid,
fenced, conflicting or busy frames reserve no ordinal; later valid frames continue.
Storage/transport unavailability is never `notAdmitted`. New game frames recheck
current authentication; admitted work may finish after revocation (D-289).

## Reads, applicability and privacy

All reads require current authentication and game participation:

| Route | Result |
| --- | --- |
| `GET /v1/games/{id}/revisions?after=decimal` | `GameRevisions`, ordered 100-record suffix and `next` cursor; current invitation revisions are all retained. |
| `POST /v1/games/{id}/results:lookup` | `{requestIds:[...]}` → `GameResults`; at most 256 distinct IDs belonging to this actor/game. |
| `GET /v1/games/{id}/results/{requestId}` | One `GameRequestStatus`; final outcomes remain independent of delivery. |

HTTP absence is `unknown`, **not** proof of non-admission by a still-live socket;
only the fenced resume handshake provides that proof. Foreign account/kind/scope
identity collisions return a detail-free conflict, never another outcome. Foreign
games return inaccessible/404. Existing lobby HTTP `head` remains a `GameSummary`
for the inviter or accepted recipient, preserving I09a/native compatibility.

Socket `head`/revision data currently consists only of username-only invitation
metadata already shared with the two invitation participants. Pending/declined
recipients may reconnect for their original outcomes and future invitation state;
this does not grant accepted-lobby/prepared-deck/gameplay access. Future preparation
and gameplay read payloads must enforce that distinction when added in I10–I14.

Accept/Decline requires the recipient and exact current pending attempt. Reinvite
requires the inviter and exact declined attempt in the same preparation. A new
attempt is retained without replacing the game, preparation, name or server date.
Old attempt/preparation requests reject; same-ID retries recover their original
result. Decline leaves the inviter's listed lobby; pending invites never age out.
Accept is not Ready/start. No reciprocal contacts or online-presence check applies.

## Persistence and workers

Explicit migration `V7__ordered_game_requests.sql` retains V6 games/attempts and adds
current-attempt pointers, streams, durable inbox, revisions and outbox. It never
runs at service startup. jOOQ is generated from the real migrated PostgreSQL17.

Decoded game frames synchronously enter one bounded per-game FIFO **before**
asynchronous authentication/database work. Admission, stream cutover and status
cutover share that dispatcher and game→stream lock order under the existing writer
epoch. One transaction binds global `request_identity`, canonical intent and the
next ingress ordinal/inbox row. Duplicate pending/final requests consume no new
ordinal. Expected protocol refusals roll tentative work back to a savepoint inside
the epoch lock; database failures retain the existing sanitized ownership recovery.

Four execution workers claim games with `FOR UPDATE SKIP LOCKED` and execute each
game's lowest pending ordinal. State, final outcome, revision and outbox commit
together. A separate publication worker reads only committed rows and delivers
outside SQL; duplicate delivery is safe, and socket delivery is not durability.
Result delivery is actor-private; subscribed participants receive revisions.
Workers poll at 100 ms with bounded drains, including after startup/recovery.
Readiness includes ingress/execution/publication and the existing reset-mail worker;
all recovery hooks run on a new writer epoch. Shutdown stops admission, drains/fences
transactions, closes sockets and bounds worker termination.

Initial bounds: 1 MiB/depth-32 command, 1024 pending requests/game, 8 MiB decoded
mailbox/game, 4096 queued dispatcher jobs/service, 256 messages/4 MiB queued
delivery/socket. Overflow refuses admission or closes a slow consumer; it never
discards a committed request. Runtime performance qualification remains I33/I34.

## Evidence and remaining delivery

Focused real PostgreSQL and actual WebSocket/HTTP tests:

```sh
./scripts/gradle.sh --no-daemon --console=plain :protocol:jvmTest --tests '*GameWireTest' \
  :server:test --tests '*GameDurabilityTest' --tests '*GameSocketTest' --tests '*LobbyHttpTest'
```

These cover invitation lifecycle, immutable metadata, actor/context privacy,
FIFO/pipelining, receipt commit, pending/final dedup, global conflicts, fenced
replacement/independent devices, real SQL rollback/restart, committed publication,
worker recovery/close and the retained V6 migration. They are server checks, not a
native journey or final qualification. I09b2 supplies native controls/volatile
recovery and the two-APK decline/reinvite/accept journey on this path. I10–I14 add
preparation, Ready/start/cancel, gameplay and complete replica recovery to the same
domain. I34–I36 retain full-suite, compatibility and final UI/release obligations.
