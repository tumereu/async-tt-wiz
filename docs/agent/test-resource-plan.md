# Parallel test-resource plan — D-309

The orchestrator assigns resource bundles before dispatch. Workers do not race for
devices, reset another lane's environment or treat contention as a product failure.
Keep real devices/services idle until their owning journey needs them.

| Work | Isolation / exclusive lease |
| --- | --- |
| Source inspection, pure/unit host tests | Parallel in separate worktrees/build outputs. Same-worktree builds never overlap. |
| Gradle/Testcontainers/codegen/images | Separate worktree outputs and run-owned DB/container/network/ports. Existing image builders/provisioning use immutable image IDs, not shared mutable tags. Shared dependency caches may use supported tool locking; serialize only genuinely shared mutable fixtures. Limit concurrent heavy builds to actual host capacity, not a fixed lane count. |
| Normal APK/Appium/instrumentation | Current host's whole device set uses `windows-devices` plus `windows-routing` for the complete provision → install → journey → cleanup lifecycle. A single global ADB/Appium/launcher or reverse-route operation can affect peers, so no per-device concurrency is claimed yet. |
| Reused Compose/backend/catalog/mailbox | `shared-e2e-backend` if reused. Otherwise unique run-owned project, names, volumes, ports and control configuration; still take Windows routing/device leases when used. Never share a resettable DB across concurrent tests. |
| Candidate integration/state acceptance | Sole orchestrator holds `integration-writer`, which also takes the legacy controller's writer lock. |

## Enforced lease command

`scripts/implementation-resources.py` uses canonical-order, nonblocking cross-worktree
OS locks in Git's common journal, not a worker-local lock directory. Wrap the **whole**
foreground lifecycle driver, including its `finally` cleanup:

```sh
python3 scripts/implementation-resources.py run --owner RUN_ID \
  --resource windows-devices --resource windows-routing \
  --resource shared-e2e-backend -- python3 PATH_TO_OWNED_JOURNEY_DRIVER.py
```

Substitute actual IDs/driver and omit the backend lease only for a demonstrated
isolated backend. This is not permission to create another bespoke runner: reuse
the existing device/reset/journey adapters and native phase helpers. Exit **75**
means queue/reschedule resource contention, not rerun a failed assertion. If a lease
is unavailable, the other worker can deliver host/source work, not bypass the lock.

The orchestrator process itself runs under the same command with
`--resource integration-writer`; its workers use other bundles. Do not nest that
same lease or run the serial controller inside it. Dispatch honors STOP and budget
before every new session; existing owners may finish checks/cleanup to drain safely.

Locks are inherited by the foreground child: killing the wrapper does not free a
still-running child. Failed/interrupted commands additionally retain durable owner
fences. Do not delete them, steal leases or infer cleanup from an absent PID. Confirm
the exact owned processes/resources are finished, retain a successful cleanup command
with `capture-check.py` under the original owner's run namespace, then recover:

```sh
python3 scripts/implementation-resources.py recover --owner RUN_ID \
  --resource windows-devices --resource windows-routing \
  --resource shared-e2e-backend \
  --cleanup-evidence docs/agent/implementation-runs/RUN_ID/cleanup.json
```

Recovery requires free OS locks, matching owners and successful hash-checked output;
the orchestrator still inspects whether that command actually cleaned the named
resources. Keep the diagnostic/cleanup records and actual environment identities.
Resources in unrelated repositories/host applications are outside this cooperative
lock scheme; check interference before claiming isolation, never kill them.

## Evidence and scheduling

Pin commit, APK/server-image hashes, fixture/environment identity, device set and
native phase inventory. `fullJourney=false`/`skippedPhases` is a partial continuation,
not complete E2E acceptance. Preserve failed runs and only recheck affected missing
assertions. Use host checks while devices are occupied; run one meaningful integrated
journey when its feature seam is ready. Final deferred/full-suite/compatibility and
complete UI-comparison obligations remain; a resource queue does not waive them.
