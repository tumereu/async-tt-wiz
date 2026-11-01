# arcanum-parser

Our own parser from MTGJSON oracle text to `arcanum-api` card definitions.
Design: `docs/specs/arcanum-automatum.md` §6.

## Contents

- `OracleParser.parse(name, text)`: one face's oracle text → `FaceDefinition`. Reads
  only the text, so it works for custom cards too. Understood so far: keyword lines
  for the keywords in `KeywordSyntax.all` (no value, a number, a mana cost, or a
  number and a mana cost). Everything else stays `UnparsedAbility`.
- `OracleRenderer.render(ability)`: writes an ability back as oracle-style English,
  for checking parses against the original text.
- `OracleNormalizer`: turns oracle text into normalised patterns (self-references
  → `~`, numbers → `N`, mana → `{M}`, colours → `[COLOR]`, creature types →
  `[TYPE]`, keyword lines split, reminder text and ability words dropped). It is a
  measuring tool for choosing what to parse next, not the parser.

## Workflow

One wording group at a time, most frequent first: look at the real wordings in the
local frequency list, decide how they parse and what definition they produce, write
test cards, implement. The frequency list comes from `arcanum-tools`:

```
./scripts/gradle.sh :arcanum-tools:patternFrequencies -Pmtgjson=/path/to/AllPrintings.json.gz
```

It writes `arcanum-tools/build/arcanum/pattern-frequencies.tsv`, with the parser's
coverage in its header, and `unparsed-keywords.tsv` (never committed).

## Rules

- Test cards are invented: our own names and costs, wording copied from real oracle
  templates or merged from several cards. No real card data is committed.
- Unparsed text is kept and reported, never guessed.
- Generic MTG code: no dependency on any ATW module; Kotlin Multiplatform common code.
