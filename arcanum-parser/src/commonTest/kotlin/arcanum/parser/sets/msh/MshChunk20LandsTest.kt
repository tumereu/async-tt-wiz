package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 20: lands. A tapped dual land gaining 1 life (the set has ten, one tested); a land whose coloured mana works
 * if it entered this turn or you control a basic land (five, one tested); mana only for Hero or Villain spells and abilities of such
 * sources (CR 106.6); a look at the top three and a Hero revealed into the hand; four mana in any combination; an activation needing a
 * creature with toughness 4 or greater; a named Robot token for a sacrificed artifact (CR 111.4); surveil as it enters; a Villain made
 * to connive (CR 701.50a).
 */
class MshChunk20LandsTest {
    @Test
    fun syndicateLabs() = card(
        name = "Syndicate Labs",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {U} or {B}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, gainLife(1)),
        addEither("U", "B"),
    )

    // the land entered this turn (CR 603.4-style check as it is activated) or you control a basic land
    @Test
    fun grimStronghold() = card(
        name = "Grim Stronghold",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add {B} or {R}. Activate only if this land entered this turn or if you control a basic land.
        """,
    ) parsesTo abilities(
        addColorless,
        Ability.Activated(cost(tap), Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1),
            colors = listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Red)))), Players.You)))),
            anyTime.copy(condition = Condition.Or(listOf(
                Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield))),
                Condition.Exists(and(ObjectFilter.Super(arcanum.api.Supertype.Basic), land, you), atLeast = 1))))),
    )

    // CR 106.6: Hero spells or abilities of Hero sources; a look at the top three, the rest on the bottom in any order
    @Test
    fun guardiansTower() = card(
        name = "Guardians Tower",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a Hero spell or to activate an ability of a Hero source.
            {4}, {T}: Look at the top three cards of your library. You may reveal a Hero card from among them and put it into your hand. Put the rest on the bottom of your library in any order.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)),
            restriction = or(and(spellFilter, hero), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = hero))), Players.You))),
        activated(cost(mana("{4}"), tap),
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(hero, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1)))),
    )

    @Test
    fun foursquareBuilding() = card(
        name = "Foursquare Building",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {4}, {T}: Add four mana in any combination of colors.
            {4}, {T}: Draw a card. Activate only if you control a creature with toughness 4 or greater.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{4}"), tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(4), combination = true)), Players.You))),
        Ability.Activated(cost(mana("{4}"), tap), Instructions(steps = listOf(draw(1))),
            anyTime.copy(condition = Condition.Exists(and(creature, ObjectFilter.Toughness(arcanum.api.Comparison.AtLeast, n(4)), you), atLeast = 1))),
    )

    // CR 111.4: a named token
    @Test
    fun castleDread() = card(
        name = "Castle Dread",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast an artifact spell.
            {3}, {T}, Sacrifice an artifact: Create a 3/3 colorless Robot Villain artifact creature token named Dreadbot. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = and(spellFilter, artifact)), Players.You))),
        activated(cost(mana("{3}"), tap, sacrificeA(artifact)),
            create(TokenSpec.Defined(FaceDefinition("Dreadbot", colorIndicator = emptyList(), types = listOf(CardType.Artifact, CardType.Creature),
                subtypes = listOf(Subtype("Robot"), Subtype("Villain")), power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(3))), bind = "created"),
            rules = asSorcery),
    )

    @Test
    fun monitoringRoom() = card(
        name = "Monitoring Room",
        cost = null,
        type = "Land",
        text = """
            When this land enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Surveil(Players.You, n(1)))),
        addColorless,
        activated(cost(mana("{1}"), tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    // CR 701.50a: a target Villain connives
    @Test
    fun nefariousHideout() = card(
        name = "Nefarious Hideout",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a Villain spell or to activate an ability of a Villain source.
            {3}, {T}: Target Villain you control connives. Activate only as a sorcery. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)),
            restriction = or(and(spellFilter, villainFilter), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = villainFilter))), Players.You))),
        activated(cost(mana("{3}"), tap), connive(target("Villain")), targets = listOf(slot("Villain", and(villainFilter, you))), rules = asSorcery),
    )

    private val addColorless = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))
}
