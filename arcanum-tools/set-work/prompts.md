# Set work: the prompts

The orchestrator sends these exactly, with the brackets filled in. Every prompt starts with `SET-WORK <code>` so
`bin/set-cost.py` can find the agents. `<wt>` is the set's worktree, `<plan>` is `~/projects/atw-cache/set-plans/<CODE>`;
write both as absolute paths in the prompts (`/home/<user>/...`, not `~`).

## Audit (one agent per batch; model from the calibration)
```
SET-WORK <code> audit batch <NN>

Read <plan>/audit/batch-<NN>.md, and nothing else unless a rule number needs checking in ~/projects/atw-cache/MagicCompRules-*.txt, by grep.

Each entry is a Magic card's oracle text and the model a parser made of it. Decide whether the model does what the text says, ability by ability: costs, targets and how many, who is affected, amounts, durations, conditions, zones, and every word of the text accounted for. Don't assume the model is right because it looks tidy. Some entries may be wrong.

Write one line per entry to <plan>/audit/results-<NN>.tsv, with tabs: entry<TAB>correct|incorrect|unsure<TAB>reason
- correct: you checked every part of the text and it matches. Leave the reason empty.
- incorrect: something differs. The reason names the words and what the model does instead, in one line.
- unsure: you can't tell, or you don't understand a part of the model. Say which part. Prefer unsure to a guess: it only costs one test.

Reply with the counts only.
```

## Chunk (one worker per chunk; model per the README)
```
SET-WORK <code> chunk <NN>

Worktree: <wt> (branch set/<code>; work there by absolute path, never in another checkout).
Plan: <plan>, your chunk: chunks/<NN>.md.

Follow <wt>/arcanum-tools/set-work/worker-brief.md.

If your context passes about 250k, stop at the next green point, commit, and report what remains.
```

## Review of Sonnet's parser work (default model)
First write the card list to `<plan>/sonnet-sample.txt`: every card that didn't read at the start in the chunks Sonnet
did with parser work, plus a few random tested ones from them, about 20 in all.
```
SET-WORK <code> sonnet review

You check, from outside, parser models that Sonnet workers produced in this set's work, to learn whether Sonnet is accurate enough for ordinary parser chunks. A real misread is the important result.

Worktree: <wt> (branch set/<code>; by absolute path). Don't commit; leave `git status` clean (delete files you add).
Cards: <plan>/sonnet-sample.txt. Oracle text: `<wt>/arcanum-tools/set-work/bin/set-cards.py <CODE> [card name ...]` (never load AllPrintings.json.gz yourself); never copy it into the repository. Rules: ~/projects/atw-cache/MagicCompRules-*.txt, by grep. Helper signatures: <plan>/helpers.md.

1. Print each card's current model with one throwaway parser test in arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets/<code>/, run with `arcanum-tools/set-work/bin/set-tests.sh <code>`. Compare every model with the oracle text and the CR: each clause, targets, who chooses and controls, durations, "if you do", optional versus mandatory, filters, counts, every face, correctness with 3+ players.
2. Play the 4 riskiest cards in throwaway scenarios in arcanum-engine/src/commonTest/kotlin/arcanum/engine/sets/<code>/ (copy the style of an existing scenario test there), one edge case each.
3. Delete your throwaway files. Don't fix anything. Stop any background command before replying.

Reply in at most 20 lines: each defect (card, what the model or engine does, what the CR says with the rule number, parser misread or engine bug, and whether Sonnet introduced it or carried it over from an existing rule), the 4 cards played and the results, how many models you judged correct.
```

## Fix chunk (default model; repeat with the remaining items until none are left)
```
SET-WORK <code> fixes

Worktree: <wt> (branch set/<code>; by absolute path).
Fix every item in <plan>/fixes.md and every "found" item in <plan>/coordinator-log.md not marked fixed, as general parser or engine fixes. Each fix gets a test that fails before it and cites the CR. Every rules violation is fixed, additive API changes included; engine code must be right for any number of players. A test that cites a rule may change only where it encoded the rule wrongly: name each such test and why. A ruling question: settle it from the CR text and say how. If an item is too big for your context, leave it and say exactly what it needs; the next fix worker takes it.
Follow the "Find things with tools", "Parser", "API", "Rules correctness" and "Finish" parts of <wt>/arcanum-tools/set-work/worker-brief.md, with `--full --long` for the last full run, then also run `arcanum-tools/set-work/bin/gradle-slot.sh :arcanum-tools:setSupport`. A supported card outside this set may change only where one of your fixes requires it: name each and why.
If your context passes about 250k, stop at the next green point, commit, and report what remains.
```

## Bug-class sweep (default model)
```
SET-WORK <code> sweep

Worktree: <wt> (branch set/<code>; by absolute path).

Set work keeps finding bugs in existing engine and parser code one card at a time; your job is to find their siblings before a card does. Read the "found" items of <plan>/coordinator-log.md and the fix commits of this branch (`git log --oneline master..HEAD`; look at a fix's diff only around the change).

For each engine or parser fix:
1. Name its root-cause class in one line (e.g. "a later choice doesn't see what an earlier one bound", "one path handles X, a sibling path doesn't").
2. List the sibling paths it could equally affect: spells, activated, triggered and static abilities, replacement effects, delayed and reflexive triggers, copies, costs, 3+ players.
3. For each sibling that plausibly has the same gap, write a probe test with an invented card, citing the CR, in arcanum-engine/src/commonTest/kotlin/arcanum/engine/sets/<code>/<Code>SweepScenariosTest.kt (or a parser probe in arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets/<code>/<Code>SweepTest.kt). A probe that passes stays as a regression test.
4. A probe that fails: fix it generally, with the probe as its failing-first test. If the fix is too big for your context (e.g. two duplicate code paths that should become one), keep the probe out of the suite and say exactly what it needs; it goes to a fix worker.

Follow the "Find things with tools", "Parser", "API", "Rules correctness" and "Finish" parts of <wt>/arcanum-tools/set-work/worker-brief.md, with `--full --long` for the last full run.
If your context passes about 250k, stop at the next green point, commit, and report what remains.

Reply in at most 20 lines: per fix its class, the siblings probed, which failed and what you did; then totals (probes written, failing, fixed).
```

## Quality sample of REPEAT cards (default model)
First write 15-30 random `REPEAT` cards from `plan.tsv` to `<plan>/quality-sample.txt`.
```
SET-WORK <code> quality sample

You check, from outside, cards of this set marked REPEAT: they read, and every ability shape on them was judged already covered by other tests, so they got no test of their own. A real defect means that judgment was too loose; that is the important result.

Worktree: <wt> (branch set/<code>; by absolute path). Don't commit; leave `git status` clean (delete files you add).
Cards: <plan>/quality-sample.txt. Oracle text: `<wt>/arcanum-tools/set-work/bin/set-cards.py <CODE> [card name ...]` (never load AllPrintings.json.gz yourself); never copy it into the repository. Rules: ~/projects/atw-cache/MagicCompRules-*.txt, by grep. Helper signatures: <plan>/helpers.md.

1. Print each card's current model with one throwaway parser test, run with `arcanum-tools/set-work/bin/set-tests.sh <code>`. Compare each model with the oracle text and the CR: every clause, targets, who chooses and controls, durations, optional versus mandatory, filters, counts, correctness with 3+ players.
2. Play the 4 riskiest cards (not basic lands or vanilla creatures) in throwaway scenarios, one edge case each, 3 players where opponents matter.
3. Delete your throwaway files. Don't fix anything. Stop any background command before replying.

Reply in at most 15 lines: each defect (card, what happens, CR rule, parser misread or engine bug), the cards played and the results, how many models you judged correct.
```
