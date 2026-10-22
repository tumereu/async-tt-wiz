# I10a2 — native prepared-deck selection

This unit completes the native picker over I10a1's existing ordered selection path.
Source scope: P-044/P-045/P-052/P-053/P-059; T-400–T-405, T-440–T-445,
T-608/T-680/T-682–T-684; F20/F21/F30–F32/F34–F36, with transfer, Ready/start,
two-player overlap and final-qualification assertions retained in their later tasks.
The picker is personal input, GameSession predictions are volatile original intent,
and only coherent revisioned preparation reads install accepted state.

## Delivered behavior

- Own Choose/Change deck opens the account-private collection. Rows highlight a
  personal draft with only a border and native selected/checked semantics. Fixed,
  equal-width bottom Import/Use selected deck actions remain reachable. Back/X
  cancels without a game request or Ready change; rotation retains the draft.
- Contextual Import uses the existing importer and save API, then returns to this
  picker without automatically selecting or preparing the new saved deck. Empty
  collections use that same route. Leaving the import also returns to the picker.
- One confirmation captures the highlighted source revision and preparation round,
  reads complete source/definitions and submits one existing ordered `selectDeck`.
  Deleted/changed sources fail usefully without substitution. A closed picker or
  changed round fences a delayed source-read callback before it can submit.
- `GameSession` uses I10a1's `PreparationRules.select` for provisional display.
  Accepted preparation, original commands/predictions and UI drafts are separate.
  Receipts do not accept work. A final accepted result keeps the prediction until
  the accepted preparation reaches its commit revision; a matching accepted copy
  retires it even if the result is delayed. Later state is never overwritten by an
  old result/read. Only the actor's Ready clears. Copies survive invitation changes.
- Normal auth wiring preserves surviving originals and drafts through same-account
  sign-in; Logout/different-account login clears and fences them. A new process
  reads accepted state with a fresh stream and no outgoing journal.

## Actual checks and retained evidence

- `affected-host-and-apk-corrected.json`: 35 affected production controller/session/
  auth/deck-host cases, incremental debug APK, lint and module boundaries pass.
  `targeted-cancel-and-round-tests.json` adds the missing accepted-choice cancellation
  and delayed old-round/start assertion: eight preparation cases pass (seven overlap
  that earlier run), **36 distinct host cases** overall. Actual JUnit XML is retained
  in `host-junit-baseline.json` and `host-junit-targeted.json`; not a full-suite run.
- `native-journey-accessible-selection.json`: one complete real normal-APK/API29
  non-GMS picker journey in both orientations. UI-created accounts/catalog/decks/
  contact/named game; empty collection, Import cancellation/save-return, no automatic
  choice, multiple highlighted rows/rotation with **zero requests**, Back/X unchanged,
  one confirmation, real copied name/counts, leave/reopen/restart, missing-art text
  fallback and normal saved-source rename/Overwrite/cover/delete isolation.
  Successful capture prefix: `runner-aec37c68851d4e29b0a3b725407805b8`.
- `native-retained-acceptance-label-corrected.json` checks the missing accepted-lobby
  seam on that same retained game, without rerunning the whole green journey. The
  recipient's preceding ordinary Logout/login/Accept succeeded in the diagnostic
  attempt; its assertion wrongly expected bare “Preparing” instead of the actual
  state/date title. The targeted passing tail resumes the already accepted UI and
  checks preserved inviter copy plus the recipient's private empty picker and Back,
  both orientations. It is **not** claimed as a newly executed second Accept.
- `observe-*.json` and `ordered-native-outcomes.json` retain read-only synthetic SQL
  assertions: exact original copy/definitions/partitions survive source deletion and
  later invitation acceptance; one select + one accept, two revision/outbox effects,
  no double selection. No helper writes account/game/action state or drives an API
  opponent. One emulator plays the ordinary account-switch routes; complete two-
  Android Ready/start overlaps remain I12, not a pass from this single-device check.
- Native use exposed clickable Surface overriding the caller's selected semantics.
  The picker now uses the same selectable Surface overload as the existing cover
  screen, still border-only; `accessible-selection-apk.json` and the passing actual
  Android selected/checked assertion/captures verify the correction.
- V8's `player_preparation`/`game_definition` are now explicit empty-baseline stores.
  `v8-reset-regression.json` refuses a retained row in every game store and the actual
  empty V8 baseline is retained. The native server image is built incrementally
  from I10a1, not deployed; its server durability/source-isolation tests are reused
  from that exact child rather than repeated here. Input hashes pin the tested APK,
  sources, locks, image and fixtures. No dependencies or permissions changed.

## Diagnostics and scope remaining

- The initial Kotlin build rejected `Throwable` where the existing deck-error
  presenter accepts `Exception`; the adapter is corrected and affected checks pass.
- The first native attempt raced the generic Retry lookup/Review target. The runner
  now waits on the existing explicit enabled Review view and the normal automatic
  lookup, as the earlier native import tests do. A later setup readback exhausted
  `/tmp/opencode` tmpfs and truncated its APK. `offload.py` moves only this unit's
  redundant APK readbacks to ignored build storage with exact hashes, retaining all
  diagnostics and never deleting unrelated files. Normal guarded APK validation,
  explicit reattachment and the affected journeys subsequently pass.
- Only I10a2 completes. I10a/I10 have their separately selected small child-evidence
  audits; no extra review/certification child. I10b adds per-copy transfers/feedback
  on this same model; I11/I12 own actual Ready/starter/start/shuffles/table/cancel and
  full two-Android preparation/acceptance/readiness overlaps. I14 extends gameplay
  prediction; I28/I31 rematch/combined recovery stay pending.
- B3 independent review still follows I09–I12. I34 retains final full suite/deferred
  assertions, older migration fixtures and I09's recovered-HTTP-error correction;
  I33 measurements, I35 compatibility and I36/B8 complete interface-to-mock comparison
  plus all external release gates remain required/unverified. No UI-document edit,
  publication, production deployment, paid resource, new loop or subagent occurred.

The first capture invocation executed Gradle but failed to write its output because
this run directory did not exist. Its console traceback is retained in the session;
that invocation is not claimed as passing evidence. Subsequent affected execution
retains its actual output here. No product checks are inferred from the lost output.
