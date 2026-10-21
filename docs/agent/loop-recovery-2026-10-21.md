# Planning-helper recovery and unattended supervision — 2026-10-21

## Provenance and correction

The implementation session `ses_ea0491e44ffePodiQ3nc5olXCO` completed its bounded
I01c planning unit, passed its checks and saved/pushed `89f0a02`. Its private
controller journal records a halt: the role-path guard classified the run-owned
`20261021T182623Z-26bd32d9-planning-check.py` as a forbidden product change. No
application work was lost and the checkout was clean. The agent exiting one unit
was correct; the controller's rejection and lack of supervisory continuation
caused the unintended stop.

The guard now accepts explicitly declared check-only `checks[].supportPaths`
under that exact run's namespace. It still rejects unlisted helpers, runtime/build
inputs, older report files, path/symlink escapes and unsaved bytes. These helpers
must not be imported as application behavior. The old report is amended only to
declare its existing planning helper; no original evidence is regenerated or
relabeled, no task/review/gate is promoted and the original private halt survives.

The regression suite has **91 passing tests**. Six additions exercise full fresh
continuation after a helper-bearing split, unlisted helper rejection, runtime/old
report/traversal refusal, invalid/missing support, symlink escape and byte-for-byte
commit enforcement. Existing model, role, release, save, lock, fix/review and recovery
tests remain. Fresh retained outputs accompany this record in
`loop-recovery-2026-10-21-runner.json`, `loop-recovery-2026-10-21-save.json`,
`loop-recovery-2026-10-21-audit.json` and `loop-recovery-2026-10-21-docs.json`.

## Owner-directed execution — D-306

The owner explicitly selects 3,000 sessions / two weeks (1,209,600 seconds) per
session, blocking calls with no shell-tool timeout, and completion inspection after
each exit. The supervisor must repair recoverable errors, validate/save/push,
acknowledge the safe owned halt and restart without routine owner approval. The
requested overnight states are done, still implementing, or exhausted quota.

This is supervision of the existing serial driver, not a second concurrent loop.
Driver checks remain fail-closed; do not delete fences or accept unsafe state.
Necessary evidenced plan/dependency adjustments may route around forbidden work
while preserving unmet obligations and delivering all feasible real implementation.
Exact high model, scoped grants, grouped independent reviews, fresh fix verification,
truthful evidence, release gates, non-destructive ownership and explicit stop remain.
Release/operator/physical-resource claims cannot be fabricated to make state complete.
