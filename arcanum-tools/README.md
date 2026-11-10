# arcanum-tools

JVM developer tools for the Arcanum libraries. Not a library and never shipped.

Adding a set to Arcanum: follow [set-work/README.md](set-work/README.md).

- `patternFrequencies`: counts normalised oracle-text patterns in the pinned MTGJSON AllPrintings file (Un-cards,
  online-only and rebalanced cards skipped) and writes them, most frequent first, to
  `build/arcanum/pattern-frequencies.tsv`, with coverage numbers for the top 50–2000
  patterns and the parser's coverage, plus `unparsed-keywords.tsv` (keyword-like lines
  the parser still leaves unparsed). `./scripts/gradle.sh :arcanum-tools:patternFrequencies`
- `setSupport`: checks that every card of each supported set (`src/main/resources/arcanum/tools/supported-sets.txt`,
  one MTGJSON set code and name per line) is fully supported: its faces read as a host's card data gives them
  (`OracleParser.layout`, `OracleParser.parseCard`) and nothing left to the players (`Engine.manualParts`), the check
  behind a host's "fully supported" mark. Un-cards, online-only and rebalanced printings are skipped; reprints are
  checked once. Reads the sets from the pinned MTGJSON file. Prints every unsupported card with the parts left to the
  players and fails when there is any. `./scripts/gradle.sh :arcanum-tools:setSupport [-Psets=LTR,BLB]`
- `manaCheck`: castability over every card of the supported sets, from the same pinned file (never committed): each mana ability must pay
  for a small spell through a payment by hand on a test board, each cost reducer is tried on test boards; all are listed in
  `build/arcanum/mana-check.tsv` (`pays` / `FAILS`, `lowers` / `look`: a reducer whose condition the boards don't meet is to be read, not a
  failure). Fails when a mana ability doesn't pay. `./scripts/gradle.sh :arcanum-tools:manaCheck [-Psets=LTR,BLB]`
- `parserDiff`: the parser migration's gate. `--args=snapshot` writes a canonical model of every local card (all faces, one card per
  line, sorted by name; equal parsers give equal files) outside the repository; the default `--args=compare` compares a snapshot with the current
  parser. It exits 1 on a changed model in a supported set or a cube card and on any card that stops being read; held-out
  cards newly read, read differently, or still unread but changed are listed for review. The whole list is in `parser-diff.txt`, and the
  current models in `parser-models.current.snapshot`, to diff by hand. A snapshot of other card data is refused (exit 2). A task that
  names a change sees that card in the list and the gate fail; after review, take a new snapshot.
  `./scripts/gradle.sh :arcanum-tools:parserDiff --args=snapshot`, then, after the change, `./scripts/gradle.sh :arcanum-tools:parserDiff`
- `parserMetrics`: what the migration is judged by: perturbation by class (real wordings with one slot swapped, `perturbation-rules.tsv`), grids
  G1 to G11 (every combination of parts, invented text, `ParserGrids.kt`), held-out cards read, regex count and rule usage (how many regexes match
  0, 1, 2-3, 4-9, 10-49, 50+ cards). The rule usage comes from a copy of the parser's sources compiled with a counting regex (`src/shadowParser`), in front
  of the real parser on this task's classpath only; the parser itself records nothing. Per rule, per cell and per regex numbers go to
  `parser-metrics-detail.tsv` and `rule-usage.tsv`. `--args="--set CODE"` reads one set cold instead: cards and abilities read and the unread
  abilities, which is how a future set's cost is measured, from the pinned file.
  `./scripts/gradle.sh :arcanum-tools:parserMetrics [--args="--set MOM"]`

- `coveredShapes`: writes `~/projects/atw-cache/set-plans/covered-shapes.tsv`, the ability shapes (`AbilityShape.kt`: one ability's model without printed text and names,
  numbers masked to `0`, `1` or `N`, mana costs to a placeholder) the tests cover. It runs the parser's and the engine's tests once more through their
  `recordCoveredAbilities` tasks, with the recorder in their test helpers on (`jvmTest` records nothing). Parser: cards a `parsesTo` or `cardFaces` passed.
  Engine: abilities that resolved, or mana abilities activated, in a scenario that passed; a static ability's effect can't be seen, so of what is only in play, only simple
  keywords count, and modal abilities never do. `./scripts/gradle.sh :arcanum-tools:coveredShapes`
- `parserExplain`: for an oracle text, which lines, sentences and clauses read alone (in carrier sentences, through the parser's public API), the first unread clause and
  up to 8 nearest rules as `file:line` by the words they share. At most 30 lines. `./scripts/gradle.sh :arcanum-tools:parserExplain --args='"<text>"'`
- `setPlan`: writes `~/projects/atw-cache/set-plans/CODE/` from the pinned file, as `parserMetrics --set`: `plan.tsv` (each card's verdict `UNREAD`, `TEST` or `REPEAT`,
  the reason and shape ids; `CardVerdict.kt` has the conditions, and whatever can't be judged is `TEST`), `chunks/NN.md`, `digest.md`, `difficult.md`, `notes.md` and `audit/`
  (`batch-NN.md`, plus `seeded.tsv`: about 8% of entries are deliberately wrong copies, the same for one card list). A rerun keeps `notes.md`, and the audit once it has results.
  Needs `coveredShapes` first. `./scripts/gradle.sh :arcanum-tools:setPlan --args=MOM`

Every tool here that reads real cards reads one MTGJSON AllPrintings file, the host's pinned one: `~/projects/atw-cache/AllPrintings.json.gz`
(or `-Pmtgjson=`), checked against the pin's SHA-256 (the host passes the pin as `arcanum.pin`). Without it, or with another file there, a tool
stops with one message: put the pinned file there (its version and checksum named), or move the pin. Nothing else is downloaded.

`supported-cards.txt` beside `supported-sets.txt` lists cards supported one by one, by name (the host's catalog marks a card supported when it
is in a supported set or on this list, and nothing of it is left to the players); the tools read it only as the cube's stand-in (below).

`parserDiff` and `parserMetrics` read the pinned file and `~/projects/atw-cache/arcanum-cube-cards.json` (or `-Pcube=`; without that file, as in a fresh
clone, the names in `supported-cards.txt` are the cube, with no cards read from a list of their own) and write to
`~/projects/atw-cache/parser-migration/` (or `-PparserOutputs=`), never into the repository. The cards are one printing per oracle id: no Un-cards, online-only or
rebalanced printings, no card printed only in memorabilia, token, minigame, alchemy, vanguard or treasure chest sets or in token, art series,
planar, scheme, vanguard, host, augment, reversible, emblem or sticker layouts. Cube cards those rules leave out are read from the cube's list. A card is "read"
when it parses and no ability stays `Ability.Unparsed`; "held out" is in no supported set and not in the cube. A supported set the pinned AllPrintings doesn't
have yet stops both tools: move the pin to a newer file, then take a new snapshot on the old parser.
