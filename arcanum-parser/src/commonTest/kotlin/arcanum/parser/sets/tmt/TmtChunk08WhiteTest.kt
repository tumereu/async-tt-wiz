package arcanum.parser.sets.tmt

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Timing
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 08: white cards. Channel (an ability word, CR 207.2c) discarding the card itself, counters on up to two
 * targets, an Aura enchanting a land that exiles until it leaves (CR 610.3), a spell cheaper if it targets a tapped creature (CR 601.2f),
 * landcycling (CR 702.29e), an exile with a delayed "you may pay; if you don't, return" (CR 603.7, 118.12), "can't be blocked by creatures
 * with greater power", an ability activated only during your turn (CR 602.5d), X tokens where X counts (CR 107.3c, 107.3m), "Dinosaurs
 * you control other than this creature", and an Aura sacrificed to exile what it enchanted (as it last existed, CR 608.2h).
 */
class TmtChunk08WhiteTest {
    @Test
    fun eveningNewsCrew() = card(
        name = "Evening News Crew",
        cost = "{1}{W}",
        type = "Creature — Human Citizen",
        text = """
            Vigilance
            Channel — {6}, Discard this card: Put a +1/+1 counter on each creature you control. Draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(mana("{6}"), CostPart.Discard(CostObjects.Self)), counters(Objects.Each(creatureYouControl)), draw(1)).copy(abilityWord = "Channel"),
    )

    @Test
    fun directorHalberdSuitAndShades() = card(
        name = "Director Halberd, Suit and Shades",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Soldier",
        text = "At the beginning of combat on your turn, put a +1/+1 counter on each of up to two target creatures.",
        pt = "1/2",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    @Test
    fun juniperKunoichiTrainee() = card(
        name = "Juniper, Kunoichi Trainee",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Ninja",
        text = """
            When Juniper enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
            Juniper can't be blocked by creatures with power 3 or greater.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, scry(2)),
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtLeast, 3)))),
    )

    // CR 610.3: exiled until this Aura leaves the battlefield
    @Test
    fun interdimensionalBanishment() = card(
        name = "Interdimensional Banishment",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant basic land you control
            When this Aura enters, exile target creature an opponent controls until this Aura leaves the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(and(ObjectFilter.Super(arcanum.api.Supertype.Basic), land, you))),
        triggered(entersSelf, doing(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 601.2f: the cost is worked out after the targets are chosen
    @Test
    fun groundedForever() = card(
        name = "Grounded Forever",
        cost = "{4}{W}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a tapped creature.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), tapped)),
        spell(destroy(target("creature")), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun guardianCraneStance() = card(
        name = "Guardian Crane Stance",
        cost = "{W}",
        type = "Instant",
        text = "Target creature gets +1/+3 and gains flying until end of turn. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 3, has(Keyword.Flying)), scry(1), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun rooftopFlyingAce() = card(
        name = "Rooftop Flying Ace",
        cost = "{2}{W}",
        type = "Creature — Bird Mutant",
        text = """
            Flying
            {3}{W}: Target creature without flying gains flying until end of turn. Activate only as a sorcery.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{3}{W}"), gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", and(creature, not(kw(Keyword.Flying))))),
            rules = asSorcery),
    )

    // CR 702.29e: typecycling
    @Test
    fun gutsyBadAppleSister() = card(
        name = "Gutsy, Bad Apple Sister",
        cost = "{4}{W}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            When Gutsy enters, create a 2/2 red Mutant creature token.
            Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, create(mutantToken)),
        landcycling("Plains"),
    )

    // CR 603.7: the delayed trigger; CR 118.12: "you may pay … If you don't"
    @Test
    fun hawkwingDeathFromAbove() = card(
        name = "Hawkwing, Death from Above",
        cost = "{2}{W}",
        type = "Legendary Creature — Mutant Ninja Bird",
        text = """
            Flying
            When Hawkwing enters, exile up to one other target creature. At the beginning of the next end step, you may pay {3}{B}. If you don't, return that card to the battlefield under its owner's control.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            delayed(nextEndStep,
                Step.Pay(manaCost("{3}{B}"), payer = Players.You, bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(move(Objects.Bound("exiled"), Zone.Battlefield,
                    controller = Players.OwnerOf(Objects.Bound("exiled")), postBind = "returned")))),
            targets = listOf(slot("creature", and(another, creature), min = 0))),
    )

    // the power is the creature's only: artifacts and enchantments of any kind
    @Test
    fun decisiveTakedown() = card(
        name = "Decisive Takedown",
        cost = "{2}{W}",
        type = "Instant",
        text = "Destroy target artifact, enchantment, or creature with power 4 or greater.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, power(Comparison.AtLeast, 4)))))),
    )

    // CR 509.1b: compared as blockers are declared; CR 602.5d: only during your turn
    @Test
    fun primevalHousepet() = card(
        name = "Primeval Housepet",
        cost = "{W}",
        type = "Creature — Dinosaur Ninja",
        text = """
            This creature can't be blocked by creatures with greater power.
            {1}{W}, {T}: Return another target creature you control to its owner's hand. Activate only during your turn.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(and(creature, ObjectFilter.Power(Comparison.Greater, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single))))),
        activated(cost(mana("{1}{W}"), tap), move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(another, creature, you))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.YourTurn)),
    )

    // CR 107.3c: X is counted as the ability resolves
    @Test
    fun manePrideLionessLeader() = card(
        name = "Mane Pride, Lioness Leader",
        cost = "{3}{W}{W}",
        type = "Legendary Creature — Cat Mutant Rebel",
        text = """
            When Mane Pride enters, create X 2/2 red Mutant creature tokens, where X is the number of nontoken creatures you control.
            Whenever Mane Pride attacks, put a +1/+1 counter on each creature you control.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(entersSelf, create(mutantToken, count(and(not(token), creature, you)))),
        triggered(attacksSelf, counters(Objects.Each(creatureYouControl))),
    )

    // CR 107.3m: X is the value it was cast with
    @Test
    fun hornedInvasionCommander() = card(
        name = "Horned Invasion Commander",
        cost = "{X}{X}{W}{W}",
        type = "Creature — Dinosaur Soldier",
        text = """
            Flying
            When this creature enters, create X 2/2 white Dinosaur Soldier creature tokens.
            Whenever this creature attacks, Dinosaurs you control other than this creature get +1/+1 and gain flying until end of turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(creatureToken("Dinosaur Soldier", listOf(Color.White), 2, 2), Value.CastX(Objects.Self))),
        triggered(attacksSelf, pump(Objects.Each(and(sub("Dinosaur"), you, another)), 1, 1, has(Keyword.Flying))),
    )

    // CR 608.2h: "enchanted creature" after the Aura was sacrificed as a cost is the creature it enchanted as it last existed
    @Test
    fun uneasyTrucePact() = card(
        name = "Uneasy Truce Pact",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block.
            {5}, Sacrifice this Aura: Exile enchanted creature. You create a 1/1 black Ninja creature token. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), affects = Objects.AttachedTo(Objects.Self)),
        activated(cost(mana("{5}"), sacrificeSelf), move(Objects.AttachedTo(Objects.Self), Zone.Exile), create(ninjaToken), rules = asSorcery),
    )
}
