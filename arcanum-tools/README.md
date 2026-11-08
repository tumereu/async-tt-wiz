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
