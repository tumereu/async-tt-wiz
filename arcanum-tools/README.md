# arcanum-tools

JVM developer tools for the Arcanum libraries. Not a library and never shipped.

- `patternFrequencies`: counts normalised oracle-text patterns in a local MTGJSON file
  (AllPrintings or AtomicCards, `.json` or `.json.gz`; Un-cards, online-only and
  rebalanced cards skipped) and writes them, most frequent first, to
  `build/arcanum/pattern-frequencies.tsv`, with coverage numbers for the top 50–2000
  patterns. `./scripts/gradle.sh :arcanum-tools:patternFrequencies -Pmtgjson=<file>`
