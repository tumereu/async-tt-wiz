# R1 source-to-task coverage ledger — I01b

[ledger.json](ledger.json) accounts for **72 P + 715 T + 88 F = 875** primary
definitions, using the actual product, acceptance and cumulative feedback tables.
References elsewhere are not extra definitions. Each entry retains its exact source
cells, line and row SHA-256, disposition/rationale, task owners, separately pending
assertion portions and a final integration/reconciliation dependency.

## What this does and does not prove

- All approved I01–I36 parents have source-row/hash-pinned concrete check plans, expected scopes and test
  layers from the [implementation plan](../implementation-plan.md). The selected
  child's exact scope is the foundation accounting/fixtures outcome, **not** every
  future assertion assigned to I01. I01a build evidence is partial; this ledger
  deliberately declares **no complete product scenario executed or passed**.
- A cross-task item has one separately pending assertion per owning task. Its exact
  primary-source oracle remains in `source.cells`; `portion` limits the task's
  contribution. Neither a passing unit test nor implementing one task completes
  the whole item. `completionCheck` retains I34 whole-scenario reconciliation, or
  I36's phase/scope/supersession reconciliation. Final independent B8 review and
  external release gates remain additional obligations, not ledger-generated passes.
- `current` includes negative requirements (e.g. no priority, no offline actions).
  `superseded` retires the entire old fixture, retaining its provenance/replacement.
  `excluded` records a post-R1 capability while preserving its negative scope check
  and in-scope neighbors. `phase-only` covers process/design artifacts, not runtime
  implementation. `conditional` preserves **T-610**, which does not mandate an event
  hash chain. Partial feedback supersessions keep the earlier item **current** and
  list its later narrowing decisions instead of dropping all earlier direction.
- Global D-278/D-285–D-289 rules constrain every older example: no outgoing disk
  journal, missing-number gate, disclosure side channel, stronger reset race or
  definition-upload session protocol. Current corrected primary rows are the oracle.
  Historical “open”/“technical work” phrases do not re-open settled policy; consult
  the detailed current contracts referenced by those rows and the task plan.

## Reproduce and maintain

```sh
python3 scripts/coverage-ledger.py
python3 -m unittest discover -s tests -p 'test_foundation_data.py' -v
```

The validator rejects missing/duplicate/unknown source or ledger IDs, stale source
hashes/cells, unexplained or changed dispositions, missing assertion/check/task
dependencies, dropped provenance, and fabricated execution/pass claims. Regression
tests deliberately mutate both primary definitions and ledger records.

To reconcile an intentional source/ownership change, edit the explicit assignment,
disposition and check-plan definitions in `scripts/coverage-ledger.py`, then run
`python3 scripts/coverage-ledger.py --write` and review the generated diff. Ownership
is enumerated, not guessed by keyword or inferred from task completion. Never run
regeneration as a way to suppress an unknown ID or unreviewed policy change.

The foundation format has only pending assertions and an empty `executions` list.
When a later feature has real tests, extend the schema/validator with run/command,
exit code, retained evidence, exact asserted portions and remaining dependencies
**before recording executions**. Bind actual source symbols/commands when they exist;
do not invent runnable feature commands now. Require all applicable portions plus
normal-app E2E evidence before a full current scenario can pass. Preserve conditional,
retired and excluded records rather than counting them as executed green tests.

## Synthetic data

The separately versioned [catalog fixtures](../../../tests/fixtures/catalogs/README.md)
are original data/art in normal MTGJSON/Scryfall shapes. I01e hosts them; I05/I06 and
later normal-app journeys execute the downloader/import/image behavior. Fixture
validation is not an execution of T-549/T-550/T-625–T-629 or of any other product ID.
