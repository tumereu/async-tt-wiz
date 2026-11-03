package arcanum.parser.sets.tla

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 16: red and green cards: attacking and blocking only with another big creature, an Equipment attached as
 * it enters, "you may pay {1}. When you do" (CR 603.12), a Vehicle that becomes a creature by its counters, an Aura's dies trigger returning
 * itself and a milled creature (CR 603.10a, 400.7), +1/+1 for each creature type (capped), lands searched for each big attacker, "can't be
 * blocked by more than one creature" (CR 509.1b), a mana ability with an "instead", one creature dealing damage by its power, base power
 * set, and a search that puts the creature on the bottom of its library.
 */
class TlaChunk16RedAndGreenTest {
    @Test
    fun pantherPangolin() = card(
        name = "Panther-Pangolin",
        cost = "{1}{R}",
        type = "Creature — Cat Armadillo",
        text = "This creature can't attack or block unless you control another creature with power 4 or greater.",
        pt = "4/3",
    ) parsesTo abilities(
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any),
            condition = Condition.Not(Condition.Exists(and(another, creature, power(Comparison.AtLeast, 4), you), atLeast = 1))),
    )

    // CR 701.3a
    @Test
    fun pairedSabers() = card(
        name = "Paired Sabers",
        cost = "{2}{R}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains double strike until end of turn.
            Equipped creature gets +1/+1.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), gains(target("creature"), Keyword.DoubleStrike),
            targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 1), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 603.12
    @Test
    fun junaArtfulTumbler() = card(
        name = "Juna, Artful Tumbler",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Performer",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever Juna attacks, you may pay {1}. When you do, target creature can't block this turn.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Prowess,
        triggered(attacksSelf, Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
                targets = listOf(slot("creature", creature)))))),
    )

    // CR 205.1 (an artifact creature while it has three fire counters), 702.122a
    @Test
    fun siegeBalloon() = card(
        name = "Siege Balloon",
        cost = "{2}{R}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            {1}: Put a fire counter on this Vehicle.
            As long as this Vehicle has three or more fire counters on it, it's an artifact creature.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{1}")), counters(Objects.Self, 1, CounterKind.Named("fire"))),
        static(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("fire"), atLeast = 3))),
        Keyword.Crew(3),
    )

    @Test
    fun silentBowArchers() = card(
        name = "Silent Bow Archers",
        cost = "{1}{R}",
        type = "Creature — Human Archer",
        text = """
            Reach
            When this creature enters, you may discard a card. If you do, draw a card.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )

    // CR 603.10a (the enchanted creature's power as it last existed), 400.7 (this card in the graveyard)
    @Test
    fun heraldsDestiny() = card(
        name = "Herald's Destiny",
        cost = "{2}{G}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            Enchanted creature gets +1/+1 for each creature card in your graveyard and is an Avatar in addition to its other types.
            When enchanted creature dies, mill cards equal to its power. Return this card to its owner's hand and up to one creature card milled this way to the battlefield under your control.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creatureYouControl)),
        static(Mechanic.ModifyPowerToughness(count(inYourGraveyard(creature)), count(inYourGraveyard(creature))),
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Avatar")))), affects = Objects.AttachedTo(Objects.Self)),
        triggered(dies(ObjectFilter.Is(Objects.AttachedTo(Objects.Self))),
            doing(Effect.Mill(Players.You, powerOf(leftOne), postBind = "milled")),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.SelfAfter, Zone.Hand),
            move(Objects.Bound("chosen"), Zone.Battlefield, controller = Players.You)),
    )

    // CR 205.3 (each of its creature types; a changeling has them all, CR 702.73a), capped at 10
    @Test
    fun dutifulMenagerist() = card(
        name = "Dutiful Menagerist",
        cost = "{3}{G}",
        type = "Creature — Human Citizen Ally",
        text = "Each non-Human creature you control gets +1/+1 for each of its creature types, to a maximum of 10.",
        pt = "4/4",
    ) parsesTo abilities(
        static(Value.Min(listOf(Value.Of(NumericProperty.CreatureTypes, Objects.Affected, Combine.Single), n(10))).let { Mechanic.ModifyPowerToughness(it, it) },
            affects = Objects.Each(and(not(sub("Human")), creature, you))),
    )

    // CR 508.3d: "that many" is the number of those attackers
    @Test
    fun theStoneSovereign() = card(
        name = "The Stone Sovereign",
        cost = "{3}{G}",
        type = "Legendary Creature — Human Noble Ally",
        text = """
            When The Stone Sovereign enters, create a 4/4 green Bear creature token.
            Whenever one or more creatures you control with power 4 or greater attack, search your library for up to that many basic land cards, put them onto the battlefield tapped, then shuffle.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Bear", listOf(Color.Green), 4, 4))),
        triggered(Trigger.On(EventPattern.Attack(and(creature, you, power(Comparison.AtLeast, 4))), oneOrMore = true),
            search(basicLand, max = Value.Count(Objects.EventObject(arcanum.api.EventRole.Object))),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    // CR 509.1b
    @Test
    fun gumboTheOldKingsPal() = card(
        name = "Gumbo, the Old King's Pal",
        cost = "{4}{G}{G}",
        type = "Legendary Creature — Ape Goat",
        text = """
            When Gumbo enters, put a +1/+1 counter on each creature you control.
            Each creature you control with power 4 or greater can't be blocked by more than one creature.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(entersSelf, counters(Objects.Each(creatureYouControl))),
        static(Mechanic.MaximumBlockers(1), affects = Objects.Each(and(creature, you, power(Comparison.AtLeast, 4)))),
    )

    // a choice among the milled cards; "if you don't" (CR 608.2c)
    @Test
    fun emuPony() = card(
        name = "Emu-Pony",
        cost = "{2}{G}",
        type = "Creature — Bird Horse",
        text = "When this creature enters, mill three cards. You may put a land card from among them into your hand. If you don't, put a +1/+1 counter on this creature. (To mill three cards, put the top three cards of your library into your graveyard.)",
        pt = "3/1",
    ) parsesTo abilities(
        triggered(entersSelf, mill(3, postBind = "milled"),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.If(Condition.Not(Condition.NonEmpty("chosen")), then = listOf(counters(Objects.Self)))),
    )

    @Test
    fun columnVault() = card(
        name = "Column Vault",
        cost = "{G}",
        type = "Instant",
        text = "Target creature gets +2/+2 and gains reach until end of turn. Untap it.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2, has(Keyword.Reach)), untapIt(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 605.1a: a mana ability adding more if you control a big creature
    @Test
    fun rowdySpectators() = card(
        name = "Rowdy Spectators",
        cost = "{1}{G}",
        type = "Creature — Human Citizen",
        text = "{T}: Add {G}. If you control a creature with power 4 or greater, add {G}{G} instead.",
        pt = "2/1",
    ) parsesTo abilities(
        activated(cost(tap), Step.If(Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), atLeast = 1),
            then = listOf(doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}{G}")!!)), Players.You))),
            otherwise = listOf(doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))))),
    )

    // CR 120.3: one creature deals damage equal to its power (not a fight)
    @Test
    fun stonyRetort() = card(
        name = "Stony Retort",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control deals damage equal to its power to target creature an opponent controls.",
    ) parsesTo abilities(
        spell(damage(powerOf(target("creature")), toObjects(target("creature an opponent controls")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("creature an opponent controls", creatureOpponentControls))),
    )

    @Test
    fun fangTuskElkPanther() = card(
        name = "Fang-Tusk Elk-Panther",
        cost = "{4}{G}{G}",
        type = "Creature — Elk Cat",
        text = """
            Reach
            Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Typecycling(sub("Forest"), "Forestcycling", manaCost("{2}")),
    )

    // CR 613.4b (base power only)
    @Test
    fun tortoiseGoose() = card(
        name = "Tortoise-Goose",
        cost = "{G}",
        type = "Creature — Turtle Bird",
        text = "{3}: Until end of turn, this creature has base power 4 and gains trample.",
        pt = "0/4",
    ) parsesTo abilities(
        activated(cost(mana("{3}")), untilEndOfTurn(Objects.Self, Mechanic.SetPowerToughness(n(4), null), has(Keyword.Trample))),
    )

    // CR 701.21a (sacrificing a Food), 701.23 ("if you search your library this way")
    @Test
    fun haplessTurnipVendor() = card(
        name = "Hapless Turnip Vendor",
        cost = "{1}{G}",
        type = "Creature — Human Citizen",
        text = """
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Whenever you sacrifice a Food, you may search your library for a basic land card and put it onto the battlefield tapped. If you search your library this way, put this creature on the bottom of its owner's library, then shuffle.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, createFood),
        triggered(on(EventPattern.Sacrifice(and(sub("Food"), you))),
            Step.Pay(steps = listOf(search(basicLand), move(Objects.Bound("found"), Zone.Battlefield, tapped = true)), payer = Players.You, bind = "searched"),
            Step.If(Condition.Did("searched"), then = listOf(move(Objects.Self, Zone.Library, position = -1), shuffle))),
    )
}
