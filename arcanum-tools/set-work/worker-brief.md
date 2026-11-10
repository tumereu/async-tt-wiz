# Set work: brief for one chunk

You do one chunk of a set in the set's worktree, then stop. A fresh worker does the next one, so keep your context small.
Paths: `<plan>` is `~/projects/atw-cache/set-plans/<CODE>`, `bin/` is `arcanum-tools/set-work/bin/` in the worktree; run
everything from the worktree root.

**Read only:** this brief, `arcanum-tools/set-work/techniques.md` (know-how of earlier sets), `<plan>/digest.md`,
`<plan>/notes.md` (this set's), `<plan>/helpers.md` (test helper signatures) and your `<plan>/chunks/NN.md`. Don't read
READMEs, specs or whole source files. The rules: `~/projects/atw-cache/MagicCompRules-*.txt`, by grep for the rule you need.

## Find things with tools, not by browsing
- Each card in your chunk file lists `Nearest tests:` (the most alike existing parser test and engine scenario, `file:line`)
  and, if it doesn't read, `Nearest rules`. Open those first and copy their format. Look helper names up in `helpers.md`.
- `bin/parser-explain.sh '"<text>"' ['"<more text>"' ...]`: which clause doesn't read and the nearest parser rules as
  `file:line`. It takes seconds after the first run; explain several texts in one call. Run it before searching.
- Read `sed -n` ranges of about 40 lines; grep with `-m` limits. Put several lookups in one command: every turn rereads
  your whole context. Keep command output short.

## Parser
Add general rules organised by construct where one exists; a slot or one line in a table beats a new sentence rule.
Generalise a near-duplicate rather than adding one. Bump `OracleParser.VERSION` when any model changes. Unparsed text is
kept, never guessed. A rule change that changes a supported card's model must be justified by that card's text; say so in
your reply.

## API
Additive only: sealed types, KDoc, a CR citation. List every change in your reply. If a card needs a concept the API lacks
and it isn't small, leave the card unread and name it with what it would take; the orchestrator gives it to a fix worker.

## Rules correctness
- The Comprehensive Rules decide. If you find anything in the engine or parser that breaks them, in this set's cards or
  any other, fix it generally with a test that fails before the fix. If it's too big for your chunk, name it in your reply.
- **Any number of players.** Engine code and models must be right for 3+ players: APNAP order, "each opponent", "any
  opponent may", choices per player. Where a card involves opponents or each player, play it with `scenario(players = 3)`.
- Never argue that a card is rare or odd; every card is supported.

## Tests
Your chunk file shows each card's verdict from `plan.tsv`.
- `TEST`: write a parser test. Write an engine scenario only when the card gives the engine something its tests haven't
  played: a new effect, trigger, replacement, condition, timing or interaction. A card that is mechanically the same as a
  tested one and differs only in wording gets the parser test alone.
- `REPEAT`: its abilities' shapes are already tested and nothing connects them; no new test is needed. You may turn
  `REPEAT` into `TEST` whenever you doubt it, never the other way.
- Files: `arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets/<code>/<Code>ChunkNN<Theme>Test.kt` and
  `arcanum-engine/src/commonTest/kotlin/arcanum/engine/sets/<code>/<Code>ChunkNN<Theme>ScenariosTest.kt`. The set's first
  chunk copies the newest set's helper file (named in `helpers.md`) to `<Code>Cards.kt` in its package.
- Each test card has an invented name: check it isn't a real card (`grep -x -F "<name>" ~/projects/atw-cache/all-card-names.txt`)
  and isn't already used by another test (invented names are global across the engine suite). Use the exact oracle text with
  the name replaced; change cost and subtypes only where it doesn't affect play.
- Expected values come from the oracle text and the CR, never copied from parser output. Cite CR numbers in the test name or
  a comment above it.

## Audit lines
A card may carry `Audit (incorrect): ...` or `Audit (unsure): ...` from a separate read of its model. Check it against the
oracle text and the CR. A real misread gets its parser rule fixed generally, with a test that fails before the fix; a false
alarm gets a test showing the model right. Name each one and which it was in your reply.

## Finish
- While working: `bin/set-tests.sh <code> ['<pattern of a shared test class you changed>' ...]`: one Gradle call for your
  set's parser and engine tests, printing counts and failures only.
- Last: `bin/set-tests.sh <code> --full`: the whole parser and engine suites plus a comparison of every card's model with
  the set's starting point. Supported cards changed outside your set, cube changes and cards no longer read must all be 0,
  or named in your reply with the reason. Check each held-out card it lists as read differently and fix any now wrong.
  If you change anything after this run, run it again.
- Every Gradle task goes through `bin/gradle-slot.sh <tasks>` (or the scripts above), one at a time; never `./gradlew` or
  `./scripts/gradle.sh` directly.
- Commit in the worktree. Never merge or push.
- Append at most 5 lines to `<plan>/notes.md`: what later chunks can reuse (a new table, helper, scenario technique). No history.
- Stop every background command or monitor you started before your final reply.

Final reply, at most 12 lines: cards done, cards left and why, engine or parser bugs found (fixed or not), misreads, API
changes, changed test expectations (which and why), supported cards whose model changed (which and why), choices made.
