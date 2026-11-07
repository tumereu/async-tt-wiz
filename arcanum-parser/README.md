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
- Target spans (`TargetSpans.kt`): each target slot of a parsed ability gets `TargetSlot.span`, where its phrase ("any
  target", "up to two target creatures", "another target creature you control", "one or two targets") sits in the
  ability's text (a mode's slot: in `Mode.text`). It is read from the text after parsing, so every wording gets spans:
  slots take the phrases in order, one phrase may stand for several slots its count allows ("two target creatures
  controlled by different players"), and a reflexive trigger's sentence ("When you do, …") is left to that trigger. A slot
  with no phrase of its own (a keyword action that targets: "earthbend 2", "support 2"), or a text whose phrases don't
  pair up with its slots, has none. Every parse test checks the spans.
- `OracleParser.parseCard(layout, faces)`: a card with several faces (split, adventurer,
  double-faced; CR 709, 712, 715) → `CardDefinition`, each face parsed on its own. A face's
  colour indicator is read from `OracleCard.colorIndicator` ("U").
- `OracleParser.layout(name, faces)`: the `Layout` for `parseCard` of a card printed with `faces` under a card-data
  layout name (Scryfall's and MTGJSON's: "split", "transform", "modal_dfc", "adventure", ...); null where the parser
  has none. One face is always `Layout.Normal`.
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
  Avatar: The Last Airbender (`tla`): all 286 cards parse (the Sagas that transform as two faces, the five basic lands untested); its
  rules are in `TlaWordings.kt` (`Tla`, and `TlaPhrases` for conditions and values) and the `tla*` functions of `Sentences.kt`, tried
  first; names with " and " ("Kiro and Tide") are read with singular verbs, and "waterbend {X}" as an additional cost announces X.
  Aetherdrift (`dft`): all 276 cards parse (the five basic lands untested); its rules are in `DftWordings.kt` (`Dft`, and `DftPhrases` for
  values, conditions and nouns) and the `dft*` functions of `Sentences.kt`, tried first, with two fix-ups in `OracleParser` (the spell's
  target a cost reduction "if it targets a … you control" names, and the additional cost's sacrificed permanent a spell reads); "this
  creature attacks while saddled" is an attack by this creature while saddled (so "When this creature enters and whenever it attacks while
  saddled" is one ability), X in "when you cycle this card" is the cycling's X, and "Sacrifice one or more artifacts" announces X (at least 1).
  Edge of Eternities (`eoe`): all 261 cards parse (the five basic lands untested); its rules are in `EoeWordings.kt` (`Eoe`, and
  `EoePhrases` for values, conditions, nouns and events) and the `eoe*` functions of `Sentences.kt`, tried first; station symbol lines
  ("N+ | abilities") become statics of the Spacecraft or Planet with N or more charge counters, and the printed power and toughness
  belong to the striation that makes it a creature ("It's an artifact creature at N+.").
  Final Fantasy (`fin`): all 312 cards parse (the transforming cards, Adventures and the meld pair as two faces; the basic lands and nine
  Towns that only repeat a template untested); its rules are in `FinWordings.kt` (`Fin`, and `FinPhrases` for nouns, values and
  conditions) and the `fin*` functions of `Sentences.kt` (whole lines first, effects, modifiers and late rules); flavor words (CR 207.2d)
  are dropped before abilities, chapters, modes and keywords, and tiered modes ("• Cure — {0} — …") become modes with extra costs.
  Tarkir: Dragonstorm (`tdm`): all 276 cards parse (the Omen cards as two faces, the five basic lands untested; the reversible
  printings skipped); its rules are in `TdmWordings.kt` (`Tdm`, and `TdmPhrases` for nouns and conditions) and the `tdm*` functions of
  `Sentences.kt`, tried first; "As this enchantment enters, choose Abzan or Mardu." with its "• Abzan — [ability]" lines is read by
  `OracleParser` (each ability works only if its clan was chosen), "Mobilize X, where X is …" is written out as its triggered
  ability, an exile "until … leaves the battlefield" is kept as card memory when another ability names "the exiled card", and "if you
  cast it" in another creature's enters trigger is that creature.
  Modern Horizons 2 (`mh2`): all 309 cards parse (the four split cards as two faces, the five basic lands untested); its rules are in
  `Mh2Wordings.kt` (`Mh2`, and `Mh2Phrases` for conditions, values and nouns) and the `mh2*` functions of `Sentences.kt`, tried first;
  `OracleParser` also reads "Vanishing" without a number, echo and reinforce X with a cost after a dash ("Echo—Discard a card."),
  artifact landwalk and landcycling, protection from permanents with counters, and a modal trigger's "choose both instead".
  The Lord of the Rings: Tales of Middle-earth (`ltr`): all 289 cards parse (the basic lands and two vanilla creatures untested; Stern
  Scolding, Samwise Gamgee and Wizard's Rockets tested in the cube); its rules are in `LtrWordings.kt` (`Ltr`, and `LtrPhrases` for
  nouns, values and conditions) and the `ltr*` functions of `Sentences.kt`, tried first; "the amassed Army" names the Army the amass
  step chose, "whenever you scry" makes "it" the source, a modal trigger's header may be "choose one and [this] gets +1/+1 until end of
  turn" (the pump in each mode), and `OracleParser` also reads nonbasic landwalk.
- Mechanics that used to be by hand (`MechanicWordings.kt`, tried first; `MechanicWordingsTest`, one test per wording): the Ring tempting
  you (the effect, "whenever the Ring tempts you", "[this] is your Ring-bearer", "you don't control a Ring-bearer", "you chose a creature
  other than [this] as your Ring-bearer"), soulbond's "As long as [this] is paired with another creature, both creatures have …" /
  "each of those creatures gets/has …", haunt's "the creature it haunts dies" (those triggers work in exile, CR 702.55c), "if this
  spell / it was bargained", "if tribute wasn't paid" and "If [this] would be put into a graveyard from anywhere, exile it instead";
  "[this] mutates", "Venture into the dungeon." / "Venture into Undercity.", "You take the initiative.", "you complete a dungeon",
  "you've completed a dungeon", "whenever you choose a creature as your Ring-bearer", "your Ring-bearer" as a noun, subject and
  object ("Put a +1/+1 counter on your Ring-bearer."), haunt's modal trigger working in exile too, and a dungeon card's rooms ("[name] — [effect] (→ [room] or [room])", also as card data writes the arrows,
  "(Leads to: [room], [room])", `MechanicWordings.rooms`; a room effect it can't read is a `Step.Manual`) and its restriction
  "You can't enter this dungeon unless you "venture into [quality]."" (`Mechanic.OnlyVentureInto`, working outside the game);
  the room effects the general rules didn't read (`Sentences.dungeonEffect`, `DungeonRoomWordingsTest`): "Draw three cards and
  reveal them. You may cast one of them without paying its mana cost.", "Reveal the top ten cards of your library. Put a creature card
  from among them onto the battlefield with three +1/+1 counters on it.", "Sacrifice a creature, an artifact, and a land." (three
  different permanents) and "Discard a card and sacrifice …", plus "Target creature can't attack until your next turn.", "You may
  play them." without a duration (for as long as they remain exiled) and "Each player loses 2 life unless they discard a card" /
  "… sacrifice a creature, artifact, or land of their choice" — every room of the dungeons in the card data that has its arrows
  (Baldur's Gate Wilderness's data has none, so it isn't a playable dungeon);
  in `OracleParser`, spree's and tiered's "+ [cost] — [effect]" modes and "Partner with [name]" (names with commas).
- Mechanics the engine plays since its lanes 1–4 (regexes in `MechanicWordings.kt`, rules in the `mechanic*` functions of
  `Sentences.kt`, tried first; `EngineLaneWordingsTest`, one test per wording; played parsed in the engine's
  `ReadLaneWordingsScenariosTest`): "You may exert [this] as it attacks. When you do, [effect]" (`Mechanic.AsAttacks`, the effect read
  as in its attack trigger) and "Exert [this]" as a cost; costs to attack and block ("Creatures can't attack you unless their controller
  pays {2} for each creature they control that's attacking you.", "Enchanted creature can't attack or block unless its controller pays
  {3}.", "This creature can't attack unless you sacrifice a land.", "[Black creatures] can't attack unless their controller sacrifices a
  land … for each … that's attacking.", "creatures can't block unless their controller pays {1} / 2 life for each of those creatures /
  blocking creature they control", also inside "As long as …"); "Any time you could mulligan and [this] is in your hand, you may
  [effect]." (works in the hand); "Your starting hand size is increased by N." (works in the command zone); "[Enchanted creature] is a
  copy of [the chosen creature / enchanted creature]" and "Other creatures you control are copies of enchanted creature."; the keyword
  actions explore ("it", a target, "each …"), monstrosity N/X with "as long as this creature is monstrous", adapt, support (other
  targets on a permanent), learn, discover, manifest / cloak the top card(s) of your library, time travel and goad ("goad it" is the
  object just targeted, never this permanent). Siege reminder text was already dropped and "this Siege" read. Not read: "whenever you
  exert a creature" and "becomes monstrous" (no event pattern in the API).
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
