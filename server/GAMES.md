# Ordered game service — invitations and preparation

The running Spring service now implements recipient **Accept/Decline** and inviter
**Reinvite**, plus **Select deck** and individual **prepared-card transfers**, through `GET /v1/socket` WebSocket upgrade. Use the normal bearer
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

Socket `head` and revisions carry a separate nullable `preparation` payload.
The inviter and accepted recipient receive it; pending/declined recipients receive
only invitation metadata, including through revision HTTP reads and publication
to an already subscribed socket. They can still recover their own original outcomes.
The ordinary HTTP lobby summary/list remains metadata-only. Account-owned saved
sources remain private even when an accepted participant receives the game copy.

Accept/Decline requires the recipient and exact current pending attempt. Reinvite
requires the inviter and exact declined attempt in the same preparation. A new
attempt is retained without replacing the game, preparation, name or server date.
Old attempt/preparation requests reject; same-ID retries recover their original
result. Decline leaves the inviter's listed lobby; pending invites never age out.
Accept is not Ready/start. No reciprocal contacts or online-presence check applies.

## Prepared-deck selection — I10a1

Use `action: {"type":"selectDeck","deckId":"captured UUID","sourceRevision":"decimal"}`
with the current game/preparation UUIDs and **null invitation/play scope**. The
captured saved revision is checked under a shared source-row lock in the same
game execution transaction. A rename, cover or content change before execution
rejects `sourceChanged`; absent/foreign/deleted sources uniformly reject
`sourceUnavailable`. Wrong round, non-lobby and unaccepted-recipient requests
reject normally. Invitation attempt changes are not preparation locks. No whole
lobby revision, format/size, reciprocal-contact or online-presence guard is added.

`PreparationRules.select` is the pure shared application used by the server and
available to I10a2's provisional display. It installs an independent name/cover,
source identity/revision and complete definition snapshot, partitions mainboard
copies into Library and sideboard copies into Sideboard, and clears only the
actor's Ready. This unit supplies the lower-layer Ready field/rules, not Ready/start
actions or UI. Leaving, Reinvite and invitation decisions do not reset preparation.
Later source changes/deletion never rewrite or cascade-delete selected copies.

Copies have concrete UUIDv5 identities: namespace = selection request UUID,
name = `prepared-card/<zero-based-index>` in captured saved-entry/copy order.
The selected-deck identity uses `prepared-deck/0`. `PreparedDeck` stores that seed
and compact `PreparedEntry` ranges (`firstIndex` is a decimal string), not an
allocation per quantity or a fungible count without identity. `deck.card(entry,
offset)` resolves the exact stable UUID. This also supports saved sources with
large quantities without a new legality limit. A transfer in I10b must preserve
these identities, splitting the ranges as needed rather than generating new copies.
The result's `createdIds` contains the selected-deck UUID; all card UUIDs follow
the shared role rule. Results are correlation/outcomes, never state snapshots.

Explicit **V8** adds `player_preparation` and `game_definition`; neither references
the mutable saved source. Immutable definitions remain referenced by the game.
Selection, actor Ready clearing, definition retention, final result, revision and
outbox commit together on the existing inbox/processor. Pending/final retries use
the original request and cannot attach a second copy, revision or publication.
The accepted preparation is in socket heads and revision payloads for coherent
restart/recovery; picker drafts and outgoing/provisional work are not persisted here.

## Individual prepared-card transfer — I10b1

`transferPreparedCard` captures `deckId` (the prepared copy, not the saved source),
`cardId`, decimal `cardIndex` and `source` (`MAINBOARD` or `SIDEBOARD`). Scope is
game/preparation with null invitation/play. The server derives the destination as
the opposite section, validates the concrete identity against the selection seed
and current source membership, and uses the same pure `PreparationRules.transfer`
as client prediction. Range splitting/recompaction preserves every concrete UUID,
definition and selected metadata; the existing V8 encoding needs no migration.

Each card is an independent ordinary ordered request. Its prepared state, own-only
Ready clearing, result, revision and outbox are transactional. Wrong prepared deck,
copy, source section, preparation round, actor or lifecycle refuses without a
substitute or accepted revision. Independent constituents still execute; retries
recover original results. Saved-source edits/deletion and unrelated invitation
attempts/revisions are not transfer guards. No bulk write endpoint or size,
exchange or format-legality rule is added.

The production `GameSession` and `LobbyController` expose immutable gesture-start
capture and release submission. Captured row ranges exclude later arrivals; the
controller does not apply the invitation single-pending gate to transfers. Accepted
state, volatile original requests/prediction disposition and personal capture stay
separate. Rebase applies accepted changes first and surviving originals afterward;
correlated revisions or commit-revision results retire only their own predictions.
Withdrawn display does not recall a request; late acceptance remains accepted work.
Receipt is not acceptance, and old reads/results cannot reinstall an old snapshot.

Ordinary captures pipeline immediately. The existing 256-ID recovery window bounds
active uncertain requests; a larger captured tail stays compact in process memory,
previews immediately and drains in captured order as capacity returns. Its remaining
count is exposed separately (`queuedTransferCards`), never silently truncated.
Reconnect/reauthentication retains original IDs and captures; Logout/account switch
clears them and fences callbacks. Restart reads accepted server state, never a
durable outgoing journal. Native one/all tap/drag/accessibility, inspection and
feedback/row transitions remain I10b2, not a native pass from these host interfaces.

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

./scripts/gradle.sh --no-daemon --console=plain :protocol:jvmTest --tests '*PreparationTest' \
  :server:test --tests '*PreparedDeckTest' --tests '*GameSocketTest'
```

These cover invitation lifecycle, immutable metadata, actor/context privacy,
FIFO/pipelining, receipt commit, pending/final dedup, global conflicts, fenced
replacement/independent devices, real SQL rollback/restart, committed publication,
worker recovery/close and the retained V6 migration. They are server checks, not a
native journey or final qualification. I09b2 supplied native invitation controls/
volatile recovery and the two-APK decline/reinvite/accept journey on this path.
I10a1 adds pure/server selection, retained V7 migration, exact source isolation,
actor-only Ready fixtures and actual socket preparation/privacy checks. I10a2 still
owns its saved native picker/accepted-predicted-UI evidence; I10b1 adds per-copy
transfer semantics, durable socket execution and multi-in-flight session/controller
prediction/recovery; I10b2 owns native transfer controls and presentation,
I11/I12 Ready/start/cancel, and I13/I14 gameplay/complete replica extensions on this
same domain. I34–I36 retain full-suite, compatibility and final UI/release obligations.
