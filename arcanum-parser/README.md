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
  Comprehensive Rules (`RulesWords.kt`, CR 205.3 and 207.2c; the subtypes are public as `OracleParser.subtypes`, for
  hosts offering "choose a creature type"). Exhaust, Boast, Max speed,
  Power-up and Solved become ability labels.
- Set tests (`commonTest/…/sets/<code>/`): every card of a set with invented names. Duskmourn (`dsk`): all 276
  cards parse.
  Bloomburrow (`blb`): all 279 cards parse.
  Return to Ravnica (`rtr`): all 254 cards parse.
  Gatecrash (`gtc`): all 249 cards parse. Dragon's Maze (`dgm`): all 156 cards parse (split cards with fuse as their
  halves). Reality Fracture (`fra`): all 285 cards parse (preparation cards as two faces, the prepare spell second); its
  rules are in `FraWordings.kt` and the `fra*` functions of `Sentences.kt`, most of them tried only after the other rules
  find nothing.
  The Hobbit (`hob`): all 193 cards parse (adventurers as two faces); its rules are in `HobWordings.kt` and the `hob*` functions of
  `Sentences.kt` (tried first, each reading one specific wording; texts of several sentences are offered to them whole first).
  Marvel Super Heroes (`msh`): all 281 cards parse (modal double-faced heroes as two faces); its rules are in `MshWordings.kt` and
  the `msh*` functions of `Sentences.kt`, tried first like The Hobbit's; flavor words before modes and abilities are dropped
  (CR 207.2d), dotted names ("U.S.Agent") are also read without dots, and "he/she/they" refer back to the card.
  Secrets of Strixhaven (`sos`): all 271 cards parse (preparation cards as two faces; the five basic lands untested); its rules are in
  `SosWordings.kt` and the `sos*` functions of `Sentences.kt`, tried first like The Hobbit's except "A, B, and C" sentences and "If
  [any condition], …" / "… unless [condition]", tried only after every other rule finds nothing.
  Teenage Mutant Ninja Turtles (`tmt`): all 190 cards parse; its rules are in `TmtWordings.kt` and the `tmt*` functions of
  `Sentences.kt`, tried first like The Hobbit's; names of two characters ("Don & Raph attack", "The Neutrinos get") are read with
  singular verbs, and a modal trigger's "choose one that hasn't been chosen this turn." may end with a period.
  Lorwyn Eclipsed (`ecl`): all 273 cards parse (the transforming legends as two faces, the five basic lands untested); its rules are in
  `EclWordings.kt` (`Ecl`, and `EclPhrases` for values, conditions, nouns and events) and the `ecl*` functions of `Sentences.kt`, tried
  first; a face name with a comma in "transforms into [name]" is read through a comma-free alias, and a creature type chosen as a spell
  resolves ("Choose a creature type. … of the chosen type") is the chosen one, not a remembered one.
  Marvel's Spider-Man (`spm`): all 193 cards parse (the five heroes as modal double-faced cards; four of the five surveil lands and the five
  basic lands untested); its rules are in `SpmWordings.kt` (`Spm`, and `SpmPhrases` for nouns, conditions and events) and the `spm*`
  functions of `Sentences.kt`, tried first; flavor words of the set are listed in `RulesWords`, "this card's other abilities" is written
  out with the face's other abilities, the modes of "choose one or both" get distinct target names, and "it" in a trigger on this
  creature dealing damage to a player is this creature.
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
