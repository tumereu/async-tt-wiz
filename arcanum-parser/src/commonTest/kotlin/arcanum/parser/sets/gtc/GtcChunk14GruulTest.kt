package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 14: Gruul (red and green). Revealing and sorting the revealed cards, one or more modes with X
 * (CR 700.2, 107.3a), a planeswalker that looks and fights (CR 701.14a), a land destroyed and replaced, damage equal to a
 * count, controlling all permanents you own (CR 613.1b), the creature that entered fighting (CR 603.10a), a search for
 * three cards with a random pick (CR 701.23, 701.20c), a land becoming a creature until end of turn (CR 205.1b), mana
 * added as it enters (CR 106.4), and counters for each attacking creature.
 */
class GtcChunk14GruulTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), announced: List<String> = emptyList()) =
        Ability.Spell(Instructions(targets = targets, steps = steps.toList(), announced = announced))

    // CR 701.20a: revealed in place; the lands to the hand, the rest to the graveyard
    @Test
    fun borgorathUnleashed() {
        val seen = Objects.Bound("seen")
        val lands = Objects.Where(seen, land)
        card(
            name = "Borgorath Unleashed",
            cost = "{4}{R}{R}{G}{G}",
            type = "Legendary Creature — Cyclops",
            pt = "7/6",
            text = """
                Trample
                Whenever Borgorath Unleashed deals combat damage to a player, reveal the top three cards of your library. Put all land cards revealed this way into your hand and the rest into your graveyard.
                Discard a land card: Borgorath Unleashed deals 3 damage to any target.
            """,
        ) parsesTo abilities(
            Keyword.Trample,
            Ability.Triggered(combatDamageToPlayer, steps(
                Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(3), fromTop = true), bind = "seen")),
                Step.Do(Effect.Move(lands, Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(seen, lands), Destination(Zone.Graveyard))),
            )),
            Ability.Activated(
                costOf(CostPart.Discard(CostObjects.Choose(land, count = n(1)))),
                Instructions(targets = listOf(anyTarget), steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), n(3), source = Objects.Self)))),
                rules = anyTime,
            ),
        )
    }

    // CR 700.2: one or more modes; X announced as it is cast
    @Test
    fun clanInsurrection() = card(
        name = "Clan Insurrection",
        cost = "{X}{R}{G}",
        type = "Sorcery",
        text = """
            Choose one or more —
            • Clan Insurrection deals X damage to target creature with flying.
            • Clan Insurrection deals X damage to target creature without flying.
            • Clan Insurrection deals X damage to target player or planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.HasKeyword(Keyword.Flying))))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Announced("X"), source = Objects.Self))))),
            Mode(Instructions(targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.Not(ObjectFilter.HasKeyword(Keyword.Flying)))))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Announced("X"), source = Objects.Self))))),
            Mode(Instructions(targets = listOf(playerOrPlaneswalker),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "player or planeswalker"), Value.Announced("X"), source = Objects.Self))))),
        ), min = 1, max = 3, chooser = Players.You), announced = listOf("X"))),
    )

    // CR 606: loyalty abilities; CR 701.14a: fight; CR 114: the emblem
    @Test
    fun domrakWildheart() = card(
        name = "Domrak Wildheart",
        cost = "{1}{R}{G}",
        type = "Legendary Planeswalker — Domri",
        loyalty = "3",
        text = """
            [+1]: Look at the top card of your library. If it's a creature card, you may reveal it and put it into your hand.
            [−2]: Target creature you control fights another target creature.
            [−7]: You get an emblem with "Creatures you control have double strike, trample, hexproof, and haste."
        """,
    ) parsesTo abilities(
        Ability.Activated(costOf(CostPart.Loyalty(n(1))), steps(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "seen")),
            Step.If(Condition.ObjectMatches(Objects.Bound("seen"), creature), then = listOf(Step.May(listOf(
                Step.Do(Effect.Reveal(Objects.Bound("seen"))),
                Step.Do(Effect.Move(Objects.Bound("seen"), Destination(Zone.Hand))),
            ), Players.You))),
        ), rules = anyTime),
        Ability.Activated(costOf(CostPart.Loyalty(n(-2))), Instructions(
            targets = listOf(slot("creature", creatureYouControl), TargetSlot("other creature", arcanum.api.TargetKind.Objects(creature), min = n(1), max = n(1),
                distinctFrom = listOf("creature"))),
            steps = listOf(Step.Do(Effect.Fight(Objects.Target("creature"), Objects.Target("other creature")))),
        ), rules = anyTime),
        Ability.Activated(costOf(CostPart.Loyalty(n(-7))), steps(Step.Do(Effect.CreateEmblem(listOf(Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Keyword.DoubleStrike, Keyword.Trample, Keyword.Hexproof, Keyword.Haste))),
            affects = Affected.Objects(Objects.Each(creatureYouControl)),
        )), Players.You))), rules = anyTime),
    )

    // CR 701.23: the found card onto the battlefield tapped, then shuffle
    @Test
    fun frenziedPlowing() = card(
        name = "Frenzied Plowing",
        cost = "{3}{R}{G}",
        type = "Sorcery",
        text = "Destroy target land. Search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Destroy(Objects.Target("land"))),
            Step.Do(Effect.Search(Players.You, ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), land)), max = n(1), zones = listOf(Zone.Library),
                bind = "found", searcher = Players.You)),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true))),
            Step.Do(Effect.Shuffle(Players.You)),
            targets = listOf(slot("land", land)),
        ),
    )

    @Test
    fun groundPound() = card(
        name = "Ground Pound",
        cost = "{R}{G}",
        type = "Sorcery",
        text = "Ground Pound deals damage to target creature equal to the number of lands you control.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Count(Objects.Each(ObjectFilter.And(listOf(land, you)))), source = Objects.Self)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 613.1b: you control the permanents you own, for good
    @Test
    fun ragingCharm() = card(
        name = "Raging Charm",
        cost = "{R}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Creatures without flying can't block this turn.
            • Gain control of all permanents you own.
            • Raging Charm deals 3 damage to each creature with flying.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(steps(untilEndOfTurn(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.Not(ObjectFilter.HasKeyword(Keyword.Flying))))), Mechanic.CantBlock(ObjectFilter.Any)))),
            Mode(steps(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.Category(ObjectCategory.Permanent), ObjectFilter.OwnedBy(PlayerFilter.You))))), Duration.Indefinitely)))),
            Mode(steps(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.HasKeyword(Keyword.Flying))))), n(3), source = Objects.Self)))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 603.10a: the creature that entered fights
    @Test
    fun ragebornBeast() = card(
        name = "Rageborn Beast",
        cost = "{5}{R}{G}",
        type = "Creature — Beast",
        pt = "6/6",
        text = "Whenever this creature or another creature you control enters, that creature fights target creature an opponent controls.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self),
                ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you)))), to = Zone.Battlefield)),
            Instructions(targets = listOf(slot("creature", creatureOpponentControls)),
                steps = listOf(Step.Do(Effect.Fight(Objects.EventObject(EventRole.After), Objects.Target("creature"))))),
        ),
    )

    @Test
    fun primalVisit() = card(
        name = "Primal Visit",
        cost = "{3}{R}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +3/+3 and has haste.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(3), n(3)), Mechanic.GainAbilities(listOf(Keyword.Haste))), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
    )

    // CR 701.23d: up to three; CR 701.20c: one of them at random when their three names differ; the rest shuffled in
    @Test
    fun signalTheTribes() = card(
        name = "Signal the Tribes",
        cost = "{R}{G}",
        type = "Instant",
        text = "Search your library for three creature cards and reveal them. If you reveal three cards with different names, choose one of them at random and put that card into your hand. Shuffle the rest into your library.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Search(Players.You, creature, max = n(3), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            Step.If(Condition.Compare(Value.CountDistinct(Objects.Bound("found"), SharedQuality.Name), Comparison.AtLeast, n(3)), then = listOf(
                Step.Bind(Objects.Random(Objects.Bound("found"), n(1)), "picked"),
                Step.Do(Effect.Move(Objects.Bound("picked"), Destination(Zone.Hand))),
            )),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("found"), Objects.Bound("picked")), Destination(Zone.Library))),
            Step.Do(Effect.Shuffle(Players.You)),
        ),
    )

    // CR 205.1b: the land is still a land, now also a 4/4 Elemental creature
    @Test
    fun rubblebeltGuildmage() = card(
        name = "Rubblebelt Guildmage",
        cost = "{R}{G}",
        type = "Creature — Human Shaman",
        pt = "2/2",
        text = """
            {R}{G}: Creatures you control gain trample until end of turn.
            {1}{R}{G}: Target land you control becomes a 4/4 Elemental creature until end of turn. It's still a land.
        """,
    ) parsesTo abilities(
        Ability.Activated(mana("{R}{G}"), steps(gains(Objects.Each(creatureYouControl), Keyword.Trample)), rules = anyTime),
        Ability.Activated(mana("{1}{R}{G}"), Instructions(
            targets = listOf(slot("land", ObjectFilter.And(listOf(land, you)))),
            steps = listOf(untilEndOfTurn(Objects.Target("land"),
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental"))), replaceSubtypes = true),
                Mechanic.SetPowerToughness(n(4), n(4)))),
        ), rules = anyTime),
    )

    // CR 106.4: mana added as the trigger resolves
    @Test
    fun kindlingEmissary() = card(
        name = "Kindling Emissary",
        cost = "{R/G}{R/G}",
        type = "Creature — Human Shaman",
        pt = "2/2",
        text = "When this creature enters, add {R}{G}.",
    ) parsesTo abilities(whenThisEnters(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{G}")!!)), Players.You))))

    @Test
    fun arenaFight() = card(
        name = "Arena Fight",
        cost = "{1}{R/G}",
        type = "Instant",
        text = "Target creature you control fights another target creature. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Fight(Objects.Target("creature"), Objects.Target("other creature"))),
            targets = listOf(slot("creature", creatureYouControl), TargetSlot("other creature", arcanum.api.TargetKind.Objects(creature), min = n(1), max = n(1),
                distinctFrom = listOf("creature"))),
        ),
    )

    // Counted as it resolves, itself included
    @Test
    fun rubbleRaiders() = card(
        name = "Rubble Raiders",
        cost = "{1}{R/G}{R/G}{R/G}",
        type = "Creature — Human Warrior",
        pt = "3/3",
        text = "Whenever this creature attacks, put a +1/+1 counter on it for each attacking creature you control.",
    ) parsesTo abilities(
        Ability.Triggered(attacksSelf, steps(Step.Do(Effect.PutCounters(Objects.Self, p1p1,
            Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature, you)))))))),
    )
}
