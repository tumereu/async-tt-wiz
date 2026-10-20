# Request ordering and recovery

## Selected mechanism — D-275

The owner accepts stable request identities, a **PostgreSQL-backed durable
per-game command inbox**, atomic individual execution and reconnect recovery with
obsolete-connection fencing. This implements D-183/D-184/D-216 rather than adding
offline play, a gameplay Send step or a blanket wait-for-acknowledgement policy.
No separate message broker is required initially. The
[history/publication contract](../03-contracts/history-and-undo.md#request-identity-durable-admission-and-recovery--d-275)
owns observable outcomes; exact schema/locking/wire mechanisms remain to specify.

## Identity and immutable intent

Each command carries a unique request ID, authenticated account/actor context,
persistent game/lobby and applicable play-incarnation/preparation-round context,
the requested action, captured references/limited guards, any random input and
explicit historical-continuation intent when applicable. Exact field names,
identifier generation and deduplication namespace remain technical design.

Save the original request durably before transmission/prediction under existing
recovery/exposure rules. Retrying keeps its identity and semantic payload, including
random seed/algorithm version; it is not another Undo press, roll or interruption.
Same identity plus different intent/scope is misuse and cannot overwrite the
original pending/final record. Compare versioned semantic payload identity, not
incidental JSON key order. Exact canonicalization/verifier representation remains
technical work. Credentials and replacement-connection/session metadata are not
immutable gameplay intent: same-account reauthentication must not turn a valid
recovery into payload mismatch. Authorize outcome reads; knowing an ID is not access.

## Durable admission and order

- Serialize per-game ingress so server admission assigns the processing order;
  persist that order with the inbox record. Preserve each client's ordered sending
  and allow interleaving across players/devices. Do not reorder by client clocks,
  inferred causality or dependency declarations. A database sequence/row lock by
  itself is not a complete ordered-ingress protocol; specify ingress/transaction
  races, failed admissions and recovery before implementation.
- Use one serialized authority domain for interacting game/control/lifecycle work,
  including persistent-lobby transitions; separate incarnation queues must not let
  old commands race past start/end. Unrelated games can run in parallel. Exact
  admission scope/worker ownership and tables/indexes remain technical design.
- A **durable receipt** can be sent only after inbox admission is committed. It
  means received/pending, not successful gameplay acceptance, a committed mutation
  or a caught-up replica. A socket write/heartbeat is not a durable receipt.
- A duplicate already pending has no second queue position; report pending or its
  eventual recorded result. A completed duplicate returns that original outcome,
  including rejection/no-op, not renewed validation against current state.

## Atomic execution and results

Process admitted commands serially for the game, checking actual authority,
access/lifecycle, applicability and existing limited guards at execution. Durable
admission reserves order, not success. For each individual command commit state/
history changes, relevant control/Undo/Redo changes, the final outcome, applicable
sync revision/change data and outbox work atomically. Publish accepted changes only
after this transaction commits. A rejected/no-op command retains its final outcome
without an invented history node or required state-revision increment.

A process/worker crash cannot leave committed gameplay without its recoverable
outcome or cause a duplicate execution on restart. Resuming incomplete processing
uses the same inbox identity/order and original random input. Each lowered bulk
constituent stays independently processed; rejection of one does not cancel later
independently applicable commands or make the gesture an atomic group. Admission
ordering is distinct from committed sync revisions and gameplay-tree ancestry.

D-277 local prediction withdrawal is not inbox cancellation or a rejection result.
The server can still accept the original admitted command under its actual checks;
its committed change uses the normal update path with original author/request ID,
like incoming opponent/other-own-device work for presentation. Do not add a recall
endpoint, suppress a valid final result or reactivate a cancelled predicted suffix.
See [prediction/playback](prediction-and-playback.md).

## Reconnect, transport replacement and lifecycle fences

- Automatically look up uncertain outcomes and retransmit recoverable original
  requests as necessary under the same identity/payload. A timeout, lost response,
  transport replacement or absent push means unknown, not rejection or permission
  for a new invocation. Exact pending/not-found/unavailable/expired-result response
  states and retry/backoff remain wire/retention design; absence of a pruned result
  never proves a command did not execute.
- Reconnect also restores accepted state through revisions or a coherent baseline.
  Request-result recovery is independent of delta retention and cannot overwrite
  newer accepted state with an old success snapshot or repeat Redo clearing.
- Fence obsolete connections from new admission after replacement, scoped to the
  replaced installation/logical stream, not all devices on an account/game. The
  cutover and admission checks must be race-safe. Already-admitted commands retain
  their records/order and are not silently discarded because the connection changed;
  normal execution/lifecycle checks can still reject them. Connection replacement
  does not revoke the login or transfer personal review/pending queues across devices.
- Preserve local original send order during resume: a not-yet-admitted older
  recoverable command cannot be overtaken by its newer local suffix. Recovery may
  pipeline requests without awaiting each final result; do not have the server
  reconstruct a dependency order. Exact resume handshake/watermark remains design.
- Every request/result remains fenced to original game/incarnation/preparation/
  invitation context. Old requests never mutate fresh play or a new preparation
  round; original committed outcomes remain recoverable without reapplication.
  Preserve protected account-scoped exposure obligations and Logout isolation.

Remaining work includes exact wire schemas, admission/worker/fence implementation,
limits/backpressure, failure result categories, deduplication retention, provisional
reference mapping, reconnect readiness/buffered-unsent handling and supported-version
recovery. Recovery of already-sent/uncertain online commands and existing protected
exposure obligations is not blanket replay authorization for arbitrary never-sent
gameplay buffers. Choosing the mechanism does not
approve every older pending-work/interruption proposal or a new whole-head guard.
No application, inbox schema, executable tests or stage transition is authorized.
