# Adding a set to Arcanum: the procedure

This is how one Magic set becomes fully supported: every card reads, is tested and plays by the Comprehensive Rules. It is
written for a Claude Code session in this repository, with default settings, default model and default effort. That
session is the **orchestrator**. It never writes parser or engine code itself. It launches one fresh subagent (Agent tool,
`general-purpose`) per step, reads only their final replies, checks their commits and keeps the steps in order.

Why it is shaped this way: most of a set's cost is long contexts being reread, so work is split into chunks of about 15
cards, each done by a fresh worker that starts from a short plan. Each chunk is one rules area (transform, flashback,
replacements…), so a worker sees several cards exercising the same engine path, which is where existing engine bugs
surface. Every chunk ends with the whole parser and engine suites plus a comparison of every card's model against the
set's starting point, so a regression is caught where it is made.

## Rules that always apply
- **The Comprehensive Rules decide.** Anything found that isn't according to them is fixed: in the parser, the engine or
  the API, whichever card or set it was found in, without asking. Only a product or API-design question goes to the
  project owner.
- **Any number of players.** Engine code and models must be right for 3+ players although the app is two-player now:
  APNAP order, "each opponent", "any opponent may", choices per player.
- **No rarity arguments.** Every card of the set is supported; none is skipped because it's rare or odd.
- **Never commit real card data,** MTGJSON files or the rules text. Tests use invented card names with real oracle
  wording (see AGENTS.md, "Arcanum libraries").
- **Unparsed text stays unparsed.** The parser never guesses.
- One Gradle job at a time per worker, always through `bin/gradle-slot.sh` (machine-wide build slots).

## One-time setup
Run `arcanum-tools/set-work/bin/setup.sh` from the repository root. Everything it fetches or writes lives in the local data
folder `~/projects/atw-cache` (outside the repository; the Arcanum tools, `scripts/catalog-pin.sh` and the catalog all read it):
- the rules text pinned by `ComprehensiveRules.EFFECTIVE_DATE` (`MagicCompRules-YYYYMMDD.txt`, downloaded from Wizards);
- the MTGJSON `AllPrintings.json.gz` named by `catalog/pin.properties`. MTGJSON only serves its current build, so setup
  downloads it only when its checksum matches the pin. Otherwise moving the pin (`scripts/catalog-pin.sh move`, then
  `setSupport` and `parserDiff` against the new file) is a task of its own, done before any set work;
- `all-card-names.txt` (every real card name, so tests' invented names can be checked against it), rebuilt when it is older
  than AllPrintings;
- the lock folder for the build slots.

## Choosing the set
`python3 -I arcanum-tools/set-work/bin/next-set.py` lists the candidate sets newest first, marks the supported ones and
marks `NEXT` on the set to take. Candidates are released paper sets that players draft: expansion and core sets, plus other
sets sold in draft or play boosters (masters, Modern Horizons), with at least 150 cards in their base set (MTGJSON's
`baseSetSize`), so Jumpstart, small supplements and bonus sheets don't count. They are sets of tournament-legal cards (at
least 90% legal or restricted in Vintage, never set type `funny` or `memorabilia`): Un-sets, collector editions and
playtest grab-bags aren't candidates. Sets not released yet, sets without cards and online-only sets are left out. The
order only keeps progress easy to follow; it needn't be exact.

## Steps
Below, `<code>` is the set code in lower case, `<CODE>` in upper case, `<plan>` is `~/projects/atw-cache/set-plans/<CODE>`,
`bin/` is `arcanum-tools/set-work/bin/`, and `<wt>` the set's worktree. Prompts are in [prompts.md](prompts.md); fill in
the brackets (`<wt>` and `<plan>` as absolute paths) and send them exactly.

1. **Worktree.** `git worktree add ~/projects/atw-wt/set-<code> -b set/<code> master`. All workers work there, one at a time.
2. **Plan.** In `<wt>`: `bin/gradle-slot.sh :arcanum-tools:coveredShapes`, then
   `bin/gradle-slot.sh :arcanum-tools:setPlan --args=<CODE>`. It writes `<plan>`: `plan.tsv` (each card `UNREAD`, `TEST`
   or `REPEAT`), `chunks/NN.md`, `digest.md`, `difficult.md` (over-inclusive warnings), `notes.md` and `audit/`.
   `coveredShapes` writes one machine-wide file (`~/projects/atw-cache/set-plans/covered-shapes.tsv`), so run one set at a
   time per machine.
3. **Starting point.** In `<wt>`: `bin/base-snapshot.sh <CODE>` (the parser's model of every local card, to
   `<plan>/parser-base.snapshot`; `bin/set-tests.sh <code> --full` compares against it).
4. **Worker aids.** `python3 -I bin/chunk-pointers.py <wt> <plan>` adds, per card, the most alike existing parser test and
   engine scenario as `file:line`. `python3 -I bin/helper-index.py <wt> <plan>` writes `<plan>/helpers.md`, the test
   helpers' signatures and the engine scenario API workers call, including the most recently worked set's helper file
   (the `sets/*/*Cards.kt` most recently changed in git; give a set code as a third argument to choose another) and each
   shared scenario driver of the set packages (`resolveAll`, `playOut`...) once, with the file and line to import it from.
   General techniques of earlier sets are in [techniques.md](techniques.md), which every worker reads; `<plan>/notes.md` starts empty and collects this set's.
5. **Audit.** A cheap model rereads every card that already reads, so misreads in cards no test would look at come to light.
   `audit/seeded.tsv` names entries made deliberately wrong; auditors never see it.
   - Calibrate on `audit/batch-01.md`: run the audit prompt twice, with `model: haiku` and `model: sonnet`, renaming
     `audit/results-01.tsv` after each run to `audit/calibration-01-haiku.tsv` or `audit/calibration-01-sonnet.tsv`, then
     `python3 -I bin/audit-score.py <plan>`; copy the chosen model's file back to `audit/results-01.tsv`. Keep Haiku if it
     calls at least 90% of the seeded entries `incorrect` and at most 10% of its other `incorrect` calls are false alarms
     (check those against the oracle text and the CR); otherwise Sonnet.
   - Run the remaining batches with the chosen model, one fresh agent each.
   - `python3 -I bin/audit-apply.py <plan>` writes each real `incorrect` or `unsure` finding into its card's chunk entry as an
     `Audit (...)` line; flagged `REPEAT` cards become `TEST` in a new chunk.
6. **Chunks**, one worker at a time, in chunk order, with the chunk prompt. Model: test-only chunks → sonnet; otherwise
   default model if more than 5 unread, or more than 2 cards in "## Cards", or a rules-heavy theme (conditions, effects,
   copies, replacements, layers, storm, meld); otherwise sonnet. Run `python3 -I bin/chunk-models.py <plan>` instead of
   reading `difficult.md`: it prints each chunk's cards, unread, cards in `difficult.md`'s "## Cards", whether it is
   test-only ("mechanical"), its theme and the model by this rule; pass `model: sonnet` for sonnet, nothing for default.
   After each reply:
   - `git -C <wt> status --short` must be empty and the reply must name a commit; otherwise send the worker back to finish.
   - If the reply says it skipped or didn't repeat the final `bin/set-tests.sh <code> --full`, run it yourself in `<wt>`.
     Supported cards changed outside the set, cube changes and cards no longer read must be 0 or named with a reason.
   - Stop the worker (TaskStop), even if it says it is waiting for background work: a finished worker woken later by a
     leftover background command rereads its whole context each time.
   - Note the chunk's result in `<plan>/coordinator-log.md`: cards done/left, commit, and from the reply: engine or parser
     bugs found and fixed, misreads, uncertain rulings, API changes, changed test expectations.
   - Cards left because the worker ran out of room go to `<plan>/chunks/NNr.md` (copy their sections) and run next.
   - A card a worker left unread and named with what it would take, a rules question it couldn't settle, or a gap it
     noticed but didn't fix, goes to `<plan>/fixes.md`.
7. **Review of Sonnet's parser work** (when Sonnet did parser chunks, not only test-only ones): one default-model agent with
   the Sonnet review prompt checks about 20 models from those chunks, all their newly read cards first. Findings go to
   `fixes.md`. A Sonnet-made misread means the next set gives Sonnet fewer parser chunks.
8. **Fix chunk.** One default-model worker with the fix prompt fixes everything in `fixes.md` and every unfixed "found" item
   of the log. If it leaves items, run fresh fix workers with the remaining ones until none are left.
9. **Bug-class sweep.** One default-model worker with the sweep prompt takes each engine and parser fix of the set, names
   its root cause, probes the sibling code paths with failing-first tests and fixes what fails. One card's bug is often a
   whole class (a spell handles something a triggered ability doesn't); this step finds the rest before a card does.
10. **Close.** In `<wt>`:
    - `git merge master` (resolve conflicts; a parser `VERSION` conflict takes the higher number plus one).
    - `bin/base-snapshot.sh <CODE> --from master` replaces the starting point with current master's, so the comparison
      shows only this branch's changes.
    - `bin/set-tests.sh <code> --full`, then `bin/gradle-slot.sh :arcanum-tools:setSupport :arcanum-tools:test`, then
      `bin/gradle-slot.sh :arcanum-tools:setPlan --args=<CODE>`: 0 unread.
    - Add the set to `arcanum-tools/src/main/resources/arcanum/tools/supported-sets.txt` (code and name, sorted) and, in
      the same commit, its release note `release/notes/<YYYY-MM-DD>-set-<name-slug>.txt` holding one line
      `set: <full name as in supported-sets.txt>`. Run `setSupport` again: every card of the set must pass.
    - Quality sample: one default-model agent with the quality-sample prompt reads 15-30 random `REPEAT` cards (they got no
      test of their own) and plays 4-5. A real defect means `REPEAT` was too loose for that kind of card: fix it, and test
      the set's remaining `REPEAT` cards of that kind.
    - Fold the set's new general techniques from `<plan>/notes.md` into [techniques.md](techniques.md): at most about 10
      new lines, rewriting a line rather than appending when it is superseded, no set history and no real card names;
      in the closing commit.
    - Accept: every check green, every changed supported card named with a reason, nothing left in `fixes.md`. Then merge
      `set/<code>` to master by the project's merge rule (here: sets merge as soon as they are accepted) and remove the
      worktree.

## While it runs
- **Time.** A chunk takes 10-80 minutes: test-only chunks about 10-25, heavy parser chunks 45-80. A 300-card set takes
  roughly a day of wall time; chunks run one at a time because they share one worktree and the build slots.
- **Running times, roughly:** `coveredShapes` 6-8 minutes, `setPlan` under a minute, `base-snapshot.sh` 1-2 minutes,
  `set-tests.sh` 1-3 minutes, with `--full` 8-12 minutes.
- Gradle (through `gradle-slot.sh`) waits silently for a free build slot: no output for a while isn't a hang.
- **Context.** Workers stop at about 250k context and report what's left. If one goes far past it, tell it to commit at
  the next green point and report the rest; the rest becomes a new chunk.
- **Stop and look** when a worker leaves red tests or uncommitted work it says it can't fix, when two workers in a row
  finish no card, or when one says the API lacks a concept many remaining cards need. Difficult cards and found bugs are
  not stop reasons: they are collected and fixed.
- **Your own context.** Read only workers' final replies, the log and small files; never transcripts, diffs or source.

## Cost tracking (optional, local)
`python3 -I bin/set-cost.py <code>` sums the weighted tokens (input 1, output 5, cache read 0.1, cache creation 1.25) of every
agent whose prompt starts `SET-WORK <code>`, from the local Claude Code transcripts, per agent and in total. Two measured
sets for reference: Foundations (517 cards, 111 unread at the start) about 33M; Innistrad Remastered (297 cards, 89 unread,
many double-faced and meld cards) about 38M for its agents, sweep included, plus about 3M of the orchestrator's own turns. Rules-heavy unread cards cost about 200-250k each;
test-only cards on Sonnet about 30k.
