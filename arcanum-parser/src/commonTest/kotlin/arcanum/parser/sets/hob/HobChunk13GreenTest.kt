package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.none
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 13: green cards. A keyword counter (CR 122.1b) and a creature type gained for good (CR 611.2a, 205.1b);
 * affinity for Elves (CR 702.41); milling and taking all Elf cards; base power and toughness copied from this creature's
 * (CR 608.2h, layer 7b); "this spell can't be countered"; "if that creature is a Bear" about the target; a characteristic-defining
 * P/T (CR 604.3); token copies that aren't legendary (CR 707.9b) unless it's a token itself; mana for each Halfling; "becomes the
 * target of a spell or ability an opponent controls"; revealing until a creature card, onto the battlefield by its mana value
 * (CR 701.20); the first creature spell each turn cheaper and with flash (CR 601.2f, 702.8a); fight (CR 701.14); one colour of
 * mana in an amount, spent only on Elves (CR 106.6).
 */
class HobChunk13GreenTest {
    // CR 122.1b: a keyword counter; 611.2a: it stays a Bear for good
    @Test
    fun barnulfTheSavage() = card(
        name = "Barnulf the Savage",
        cost = "{3}{G}{G}",
        type = "Legendary Creature — Bear Shapeshifter Warrior",
        text = """
            Trample
            Other Bears you control get +2/+2.
            At the beginning of combat on your turn, put a trample counter on up to one target creature you control. It becomes a Bear in addition to its other types. Then if you control three or more Bears, draw two cards.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Trample,
        static(pt(2, 2), affects = Objects.Each(and(another, sub("Bear"), you))),
        triggered(beginningOfCombatOnYourTurn,
            counters(target("creature"), kind = CounterKind.Keyword(Keyword.Trample.kind)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Bear"))))), Affected.Objects(target("creature")),
                Duration.Indefinitely)),
            Step.If(Condition.Exists(and(sub("Bear"), you), atLeast = 3), then = listOf(draw(2))),
            targets = listOf(slot("creature", creatureYouControl, min = 0))),
    )

    // CR 702.41a
    @Test
    fun grumpyWardens() = card(
        name = "Grumpy Wardens",
        cost = "{5}{G}",
        type = "Creature — Elf Soldier",
        text = """
            Affinity for Elves (This spell costs {1} less to cast for each Elf you control.)
            When this creature enters, mill four cards, then put all Elf cards from among them into your hand.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Affinity(sub("Elf"), "Elves"),
        triggered(entersSelf, mill(4, postBind = "milled"), move(Objects.Where(Objects.Bound("milled"), and(sub("Elf"), inZone(Zone.Graveyard))), Zone.Hand)),
    )

    // CR 608.2h: this creature's power and toughness as the ability resolves; layer 7b
    @Test
    fun gelornElfLordsSteward() = card(
        name = "Gelorn, Elf-lord's Steward",
        cost = "{2}{G}{G}",
        type = "Legendary Creature — Elf Advisor",
        text = "Whenever Gelorn attacks, choose up to one other target creature you control. Its base power and toughness become equal to Gelorn's power and toughness until end of turn.",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(attacksSelf, untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(power(Objects.Self), toughness(Objects.Self))),
            targets = listOf(slot("creature", and(another, creature, you), min = 0))),
    )

    // CR 101.2
    @Test
    fun enormousHugeBear() = card(
        name = "Enormous Huge Bear",
        cost = "{5}{G}{G}",
        type = "Creature — Bear",
        text = """
            This spell can't be countered.
            Hexproof, haste
        """,
        pt = "10/7",
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Hexproof,
        Keyword.Haste,
    )

    @Test
    fun wardenOfTheGalleries() = card(
        name = "Warden of the Galleries",
        cost = "{1}{G}",
        type = "Creature — Elf Soldier",
        text = """
            Trample
            {5}{G}{G}: Put three +1/+1 counters on this creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Trample,
        activated(cost(mana("{5}{G}{G}")), counters(Objects.Self, 3)),
    )

    // CR 608.2b: "that creature" and "it" are the target
    @Test
    fun tinyBear() = card(
        name = "Tiny Bear",
        cost = "{2}{G}",
        type = "Creature — Bear",
        text = """
            Flash
            When this creature enters, untap another target creature you control. If that creature is a Bear, put a +1/+1 counter on it.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, untapIt(target("creature")), Step.If(Condition.ObjectMatches(target("creature"), sub("Bear")), then = listOf(counters(target("creature")))),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 604.3
    @Test
    fun gloomwoodTrailblazer() = card(
        name = "Gloomwood Trailblazer",
        cost = "{2}{G}",
        type = "Creature — Elf Ranger",
        text = "Gloomwood Trailblazer's power and toughness are each equal to the number of lands you control.",
        pt = "*/*",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(and(land, you)), count(and(land, you))), characteristicDefining = true),
    )

    // CR 603.4, 707.9b: copies that aren't legendary, made only by the card
    @Test
    fun theScrivenerHalflings() = card(
        name = "The Scrivener Halflings",
        cost = "{3}{G}{G}",
        type = "Legendary Creature — Halfling Advisor",
        text = """
            When The Scrivener Halflings enter, if they're not a token, create two tokens that are copies of them, except the tokens aren't legendary.
            {T}: Add {C} for each Halfling you control.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self, listOf(CopyException.Types(removeSupertypes = listOf(Supertype.Legendary)))),
            n(2), Players.You)), interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, token))),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!, times = count(and(sub("Halfling"), you)))), Players.You))),
    )

    // CR 509.1b; 115.1: becoming the target
    @Test
    fun ancientBloatedSpider() = card(
        name = "Ancient Bloated Spider",
        cost = "{4}{G}{G}",
        type = "Creature — Spider",
        text = """
            Reach
            This creature can't be blocked by creatures with power 2 or less.
            Whenever this creature becomes the target of a spell or ability an opponent controls, draw a card.
        """,
        pt = "6/7",
    ) parsesTo abilities(
        Keyword.Reach,
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
        triggered(on(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), opponents)), draw(1)),
    )

    // CR 701.20; 603.2h: once each turn
    @Test
    fun partingAsFriends() = card(
        name = "Parting as Friends",
        cost = "{4}{G}",
        type = "Enchantment",
        text = "Whenever a nontoken creature you control dies, reveal cards from the top of your library until you reveal a creature card. If its mana value is less than or equal to the number of lands you control, put it onto the battlefield. Otherwise, put it into your hand. Put the rest on the bottom of your library in a random order. This ability triggers only once each turn.",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(not(token), creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.RevealUntil(creature, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
            Step.If(Condition.ObjectMatches(Objects.Bound("found"), ObjectFilter.ManaValue(Comparison.AtMost, count(and(land, you)))),
                then = listOf(move(Objects.Bound("found"), Zone.Battlefield)), otherwise = listOf(move(Objects.Bound("found"), Zone.Hand))),
            Step.Do(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            perTurn = 1),
    )

    // CR 120.1: the creature you control is the source
    @Test
    fun squabble() = card(
        name = "Squabble",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control deals damage equal to its power to target creature an opponent controls.",
    ) parsesTo abilities(
        spell(damage(power(target("creature")), toObjects(target("creature an opponent controls")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("creature an opponent controls", creatureOpponentControls))),
    )

    // CR 601.2f, 702.8a: only while you haven't cast a creature spell this turn
    @Test
    fun rhodricOfThornhollow() = card(
        name = "Rhodric of Thornhollow",
        cost = "{2}{G}{G}",
        type = "Legendary Creature — Avatar Wizard",
        text = "The first creature spell you cast each turn costs {2} less to cast and can be cast as though it had flash.",
        pt = "2/5",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(creature, spellFilter, you), CostChange.Less(ManaCost.parse("{2}")!!, n(1))),
            condition = Condition.Not(Condition.HappenedThisTurn(EventPattern.Cast(creature, PlayerFilter.You), atLeast = 1, measure = Measure.Events))),
        playerStatic(Mechanic.CastAsThoughFlash(creature),
            condition = Condition.Not(Condition.HappenedThisTurn(EventPattern.Cast(creature, PlayerFilter.You), atLeast = 1, measure = Measure.Events))),
    )

    // CR 701.24
    @Test
    fun beyondTheWoodlandArch() = card(
        name = "Beyond the Woodland Arch",
        cost = "{6}{G}{G}",
        type = "Sorcery",
        text = "Look at the top twenty cards of your library, put any number of land cards from among them onto the battlefield tapped, then shuffle. You gain 8 life.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(20), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(land, n(0), null, from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            shuffle, gainLife(8)),
    )

    // CR 701.14
    @Test
    fun trollBargaining() = card(
        name = "Troll Bargaining",
        cost = "{2}{G}{G}",
        type = "Sorcery",
        text = "Put two +1/+1 counters on target creature you control. Then it fights target creature an opponent controls. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spell(counters(target("creature"), 2), Step.Do(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls))),
    )

    // CR 700.2
    @Test
    fun wargStratagems() = card(
        name = "Warg Stratagems",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target creature with flying.
            • Put a +1/+1 counter on target creature you control. It gains trample and hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, kw(Keyword.Flying))))),
            mode(counters(target("creature")), gains(target("creature"), Keyword.Trample, Keyword.Hexproof), targets = listOf(slot("creature", creatureYouControl)))))),
    )

    // CR 701.23: a Forest card, not revealed
    @Test
    fun groveElves() = card(
        name = "Grove Elves",
        cost = "{2}{G}",
        type = "Creature — Elf Scout",
        text = "When this creature enters, search your library for a Forest card, put that card onto the battlefield, then shuffle.",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, search(sub("Forest")), move(Objects.Bound("found"), Zone.Battlefield), shuffle),
    )

    // CR 106.1b: one colour for all of it; 106.6: spent only on Elves
    @Test
    fun forestLoommaster() = card(
        name = "Forest Loommaster",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        text = """
            Vigilance
            Whenever another Elf you control enters, this creature gets +1/+1 until end of turn.
            {T}: Add X mana of any one color, where X is this creature's power. Spend this mana only to cast Elf spells and activate abilities of Elf sources.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(enters(and(another, sub("Elf"), you)), pump(Objects.Self, 1, 1)),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(power(Objects.Self)),
            restriction = or(and(sub("Elf"), spellFilter), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = sub("Elf")))), Players.You))),
    )

    @Test
    fun sizableBear() = card(
        name = "Sizable Bear",
        cost = "{3}{B/G}{B/G}",
        type = "Creature — Bear",
        text = "Reach, trample, haste",
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        Keyword.Haste,
    )
}
