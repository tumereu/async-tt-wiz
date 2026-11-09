# arcanum-tools

JVM developer tools for the Arcanum libraries. Not a library and never shipped.

- `patternFrequencies`: counts normalised oracle-text patterns in a local MTGJSON file
  (AllPrintings or AtomicCards, `.json` or `.json.gz`; Un-cards, online-only and
  rebalanced cards skipped) and writes them, most frequent first, to
  `build/arcanum/pattern-frequencies.tsv`, with coverage numbers for the top 50–2000
  patterns and the parser's coverage, plus `unparsed-keywords.tsv` (keyword-like lines
  the parser still leaves unparsed). `./scripts/gradle.sh :arcanum-tools:patternFrequencies -Pmtgjson=<file>`
- `setSupport`: checks that every card of each supported set (`src/main/resources/arcanum/tools/supported-sets.txt`,
  one MTGJSON set code and name per line) is fully supported: its faces read as a host's card data gives them
  (`OracleParser.layout`, `OracleParser.parseCard`) and nothing left to the players (`Engine.manualParts`), the check
  behind a host's "fully supported" mark. Un-cards, online-only and rebalanced printings are skipped; reprints are
  checked once. The MTGJSON set files are kept outside the repository (`~/.cache/arcanum-sets`, or `-PsetCache=<dir>`)
  and fetched again only when MTGJSON's version changes (Meta.json checked at most once a day; offline, the cache is
  used), so the first run needs a network. Prints every unsupported card with the parts left to the players and fails
  when there is any. `./scripts/gradle.sh :arcanum-tools:setSupport [-Psets=LTR,BLB]`
- `manaCheck`: castability over every card of the supported sets, from the same cached files (never committed): each mana ability must pay
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
  abilities, which is how a future set's cost is measured, from the same cached set file as `setSupport` (needs network the first time).
  `./scripts/gradle.sh :arcanum-tools:parserMetrics [--args="--set MOM"]`

Both read `~/projects/atw-cache/AllPrintings.json.gz` and `arcanum-cube-cards.json` (or `-Pmtgjson=`, `-Pcube=`) and write to
`~/projects/atw-cache/parser-migration/` (or `-PparserOutputs=`), never into the repository. The cards are one printing per oracle id: no Un-cards, online-only or
rebalanced printings, no card printed only in memorabilia, token, minigame, alchemy, vanguard or treasure chest sets or in token, art series,
planar, scheme, vanguard, host, augment, reversible, emblem or sticker layouts. Cube cards those rules leave out are read from the cube's list. A card is "read"
when it parses and no ability stays `Ability.Unparsed`; "held out" is in no supported set and not in the cube. A supported set the local AllPrintings doesn't
have yet stops both tools: use a newer file, then take a new snapshot on the old parser.
