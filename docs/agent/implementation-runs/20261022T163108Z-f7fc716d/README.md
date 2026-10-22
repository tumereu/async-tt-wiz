# I09a — contacts and named game creation

Delivered the native P02/P05 → real waiting-lobby path and P01 Games/Invitations,
with actual account HTTP persistence and username-only participant projections.
`server/LOBBY.md` documents the implemented interfaces. No existing-game writes,
fake-success acceptance, deck/Ready/start or cancellation controls are introduced.
Those remain I09b/I10–I12, as planned. I09 parent and B3 remain incomplete.

## Evidence and corrections

- `server-and-ui-compile.json`: first three actual PostgreSQL lobby tests passed.
  `affected-server-final.json` adds the missing bounded-search/complete contacts
  pagination assertion and explicitly tests same-ID recovery after SQL rollback;
  four lobby tests pass. `lobby-junit.json` retains the final actual JUnit XML.
- `client-and-deck-regression.json`: five new client/controller/real transport/auth
  tests, twelve affected auth regressions and ten deck HTTP regressions pass.
  `client-junit.json` / `decks-junit.json` retain their XML. `protocol-junit.json`
  retains the executed green shared codec/validation test from the compile command
  whose independent server compilation initially failed. It is not a claim that
  that whole diagnostic command passed. Generator migration-count/table inventory
  was corrected and subsequent compilation, tests, normal APK/lint/boundaries pass.
- Initial `native-journey.json` reproduced touch replacement appending to the
  suggestion. `LobbyScreens.kt` now observes short taps after TextField handling,
  retaining drag/hold/keyboard editing. `name-selection-build.json` builds/lints
  that repair. Subsequent native assertions replace both suggested and edited text
  by touch+paste without a test-issued Clear/Select-all.
- `device-second.json` initially refused unrecorded ADB peer discovery. The existing
  exact-owner pair operation and fresh wait passed without weakening guards.
  Changed APKs then required the existing explicit unroute/prepare handoff.
  All refusal outputs remain diagnostics. Exhausted `/tmp/opencode` caused truncated
  APK readback and ENOSPC; only this unit's duplicate readback/input APK artifacts
  were moved into ignored `e2e/build` storage. The two offload inventories preserve
  exact paths/hashes; no unrelated data, normal.apk, guard or ownership state moved.
- Other red native attempts exposed check-helper errors, not product successes:
  Appium `hideKeyboard` cancelled a native dialog (use its real IME Done action),
  and landscape sign-in needed to scroll to the actual lower fields. These helpers
  were corrected without changing runtime/build inputs or weakening assertions.
- **`native-journey-qualified.json` is the final complete green run** with
  `runner-1c83d464c1d24de9836fe0941419f090-*` and
  `runner-cfd0081448e24cd2bb4922c841f2c324-*` captures/JUnit/session closure.
  Both normal APKs register/login through UI, search/add unilaterally, validate and
  cancel name dialogs, create exactly one game, show both orientations, reopen the
  exact waiting lobby after navigation/process restart, show the recipient's pending
  invitation and synchronize contacts/game to a second same-account installation.
  `observed-native-game.json` is read-only observation of those UI-created IDs/date;
  no account, contact, invitation or game was API/SQL-injected into this journey.
- `cleanup-first.json`, `cleanup-second.json`, `cleanup-environment.json` confirm
  exact owned sessions, mappings, emulators/services and Compose resources removed.
  Large APK/tool artifacts are not required committed check output; JSON commands,
  XML (losslessly enveloped where necessary), screenshots and diagnostics are.

## Retained obligations

I09b owns Accept/Decline/Reinvite and the ordered socket/durable game processor.
I10–I12 own prepared copies, Ready/start/cancel, lifecycle overlaps and full prepared
two-Android start; I13/I14 extend processing/recovery, I28/I31 check rematch/combined
multi-instance use. B3 independent coverage is still empty. No complete cross-task
P/T/F scenario, final UI comparison or external gate is certified here.

I32/I34/I36 retain larger-text/tablet/keyboard-accessibility and complete mock
comparisons; this phone's landscape content width is below the two-column minimum,
so contacts use the reachable scrollable stacked form. This is an explicitly
recorded responsive adaptation, not a claimed whole-packet visual pass. Later
capabilities must replace the plainly labelled unavailable controls/state, not
leave them in the finished interface.

I34 retains the broad suite/deferred foundation assertions. Existing older fixture
migration-count assumptions in `AccountRepositoryTest`, `PasswordResetTest`,
`FoundationDatabaseTest` and foundation scripts need reconciliation with the final
schema inventory there; they are not claimed run/passing by this unit. This unit's
actual V5→V6 retained account/deck/definition/outcome migration and affected deck
upgrade paths pass. No repeated clean build/full suite/fault matrix was run.
