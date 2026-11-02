# arcanum-parser

Our own parser from MTGJSON oracle text to `arcanum-api` card definitions.
Design: `docs/specs/arcanum-automatum.md` §6.

## Contents

- `OracleParser.parse(card: OracleCard)`: one printed face (name, mana cost, type line,
  P/T, loyalty, oracle text) → `FaceDefinition`. Reads only the printed values, so it
  works for custom cards too. It understands keyword lines (`KeywordSyntax.all`) and the
  wordings the cube chunks test, read by the rules in `Sentences.kt` and the phrases in
  `Phrases.kt`. Everything else stays `Ability.Unparsed`. An instant or sorcery with any
  unread line keeps all its spell text and casting costs unparsed, because a spell
  resolves as one object (CR 608.2b). A leveler's "LEVEL" band becomes one conditional static
  ability (CR 711.2), or stays unparsed as a whole. Each parsed ability keeps its printed lines as its `text`
  (reminder text left out), each mode its "• " line (`Mode.text`), for engines to show players.
- `OracleParser.parseCard(layout, faces)`: a card with several faces (split, adventurer,
  double-faced; CR 709, 712, 715) → `CardDefinition`, each face parsed on its own. A face's
  colour indicator is read from `OracleCard.colorIndicator` ("U").
- `OracleParser.VERSION`: the parser's output version. Hosts that store parsed definitions keep them per version
  and parse again when it changes. **Bump it in every change that changes the parser's output for any card.**
- Subtypes and ability words are accepted only from the lists in the pinned
  Comprehensive Rules (`RulesWords.kt`, CR 205.3 and 207.2c). Exhaust, Boast, Max speed,
  Power-up and Solved become ability labels.
- Set tests (`commonTest/…/sets/<code>/`): every card of a set with invented names. Duskmourn (`dsk`): all 276
  cards parse.
  Bloomburrow (`blb`): 271 of 279 cards parse; left unparsed: Azure Beastbinder, Eluge, Long River Lurker,
  Mockingbird, Portent of Calamity, Rottenmouth Viper, Dragonhawk and Reptilian Recruiter (one line each that needs new
  API or engine work).
- `TypeLine`, `PrintedNumbers`: type lines and printed numbers, both ways.
- `OracleRenderer.render(ability)`: writes an ability back as oracle-style English,
  for checking parses against the original text.
- `OracleNormalizer`: turns oracle text into normalised patterns (self-references
  → `~`, numbers → `N`, mana → `{M}`, colours → `[COLOR]`, creature types →
  `[TYPE]`, keyword lines split, reminder text and ability words dropped). It is a
  measuring tool for choosing what to parse next, not the parser.

## Workflow

**Current target (D46): every card of the Powered Synergy Cube.** The tests are in
`src/commonTest/kotlin/arcanum/parser/cube/`, one file per chunk of similar wordings
(`Chunk01KeywordCreaturesTest`, `Chunk02ManaLandsTest`, …), one test per card. Each
card is invented (new name, sometimes another mana cost or subtypes) with the cube
card's exact oracle text, and states the definition it must parse to; the helper in
`CubeCard.kt` also checks the printed values read back, nothing stays unparsed, each ability's text is its lines
(compared apart from the definition) and the
definition passes `DefinitionChecks`. The owner reviews these files chunk by chunk.

Earlier approach, still useful for cards outside the cube:

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
- Any change to the parser's output bumps `OracleParser.VERSION` in the same commit.
- Generic MTG code: no dependency on any ATW module; Kotlin Multiplatform common code.
