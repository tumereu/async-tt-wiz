package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
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
 * Gatecrash chunk 08: black. Revealing until a land card and milling all revealed (CR 701.20a), an Aura that makes a land
 * a Swamp and punishes tapping it, sacrifices of a target player's choice, numbers read from creatures that have left
 * (CR 608.2h), X in a target's power (CR 107.3a), exiling the top of a library and taking a creature from among them, a
 * creature card from each opponent's graveyard (CR 601.2c), the defending player discarding (CR 506.2), and a count of
 * creature cards in opponents' graveyards.
 */
class GtcChunk08BlackTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), announced: List<String> = emptyList()) =
        Ability.Spell(Instructions(targets = targets, steps = steps.toList(), announced = announced))
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val revealUntilLand = listOf(
        Step.Do(Effect.RevealUntil(land, Players.Target("player"), n(1), reveal = true, foundBind = "found", restBind = "rest")),
        Step.Do(Effect.Move(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), Destination(Zone.Graveyard))),
    )

    // CR 701.20a: the land card revealed goes too
    @Test
    fun balconySpy() = card(
        name = "Balcony Spy",
        cost = "{3}{B}",
        type = "Creature — Vampire Rogue",
        pt = "2/3",
        text = """
            Flying
            When this creature enters, target player reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(*revealUntilLand.toTypedArray(), targets = listOf(targetPlayer)),
    )

    // CR 305.7 doesn't apply: "is a Swamp" alone adds the type (it's an Aura's effect, not replacing land types)
    @Test
    fun taintedGround() = card(
        name = "Tainted Ground",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Enchanted land is a Swamp.
            Whenever enchanted land becomes tapped, its controller loses 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        Ability.Static(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Swamp"))))), affects = Affected.Objects(enchanted)),
        Ability.Triggered(
            Trigger.On(EventPattern.Tap(ObjectFilter.Is(enchanted))),
            steps(Step.Do(Effect.LoseLife(Players.ControllerOf(Objects.EventObject(EventRole.Object)), n(2)))),
        ),
    )

    @Test
    fun corpseBarricade() = card(
        name = "Corpse Barricade",
        cost = "{2}{B}",
        type = "Creature — Zombie",
        pt = "1/4",
        text = """
            Defender
            Sacrifice another creature: This creature gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Ability.Activated(
            costOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature)), count = n(1)))),
            steps(gains(Objects.Self, Keyword.Deathtouch)),
            rules = anyTime,
        ),
    )

    // The count is the enchanted creature's controller's graveyard, read continuously (CR 611.3a)
    @Test
    fun approachingDeath() {
        val minus = Value.Times(Value.Count(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard),
            ObjectFilter.OwnedBy(PlayerFilter.Is(Players.ControllerOf(enchanted))))))), -1)
        card(
            name = "Approaching Death",
            cost = "{B}",
            type = "Enchantment — Aura",
            text = """
                Enchant creature
                Enchanted creature gets -X/-X, where X is the number of creature cards in its controller's graveyard.
            """,
        ) parsesTo abilities(
            Keyword.Enchant(TargetKind.Objects(creature)),
            Ability.Static(listOf(Mechanic.ModifyPowerToughness(minus, minus)), affects = Affected.Objects(enchanted)),
        )
    }

    // CR 608.2h: the sacrificed creature's toughness as it last existed
    @Test
    fun consumeFlesh() = card(
        name = "Consume Flesh",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target player sacrifices a creature of their choice, then gains life equal to that creature's toughness.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("player")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            Step.Do(Effect.GainLife(Players.Target("player"), toughness(Objects.Bound("sacrificed")))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 603.10a: X is the enchanted creature's power as it last existed
    @Test
    fun finalWish() = card(
        name = "Final Wish",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            When enchanted creature dies, target player loses X life and you gain X life, where X is its power.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creatureYouControl)),
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(enchanted), from = Zone.Battlefield, to = Zone.Graveyard)),
            Instructions(
                targets = listOf(targetPlayer),
                steps = listOf(
                    Step.Do(Effect.LoseLife(Players.Target("player"), power(Objects.EventObject(EventRole.Before)))),
                    Step.Do(Effect.GainLife(Players.You, power(Objects.EventObject(EventRole.Before)))),
                ),
            ),
        ),
    )

    // CR 602.1: tapping an untapped Gate you control is the cost
    @Test
    fun gatewalkShade() = card(
        name = "Gatewalk Shade",
        cost = "{2}{B}",
        type = "Creature — Shade",
        pt = "1/1",
        text = """
            {B}: This creature gets +1/+1 until end of turn.
            Tap an untapped Gate you control: This creature gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Activated(mana("{B}"), steps(pump(Objects.Self, 1, 1)), rules = anyTime),
        Ability.Activated(costOf(CostPart.TapObjects(CostObjects.Choose(ObjectFilter.Sub(Subtype("Gate")), count = n(1)))), steps(pump(Objects.Self, 2, 2)), rules = anyTime),
    )

    // CR 608.2h: the destroyed creature's power and controller as it last existed
    @Test
    fun gruesomeSpectacle() = card(
        name = "Gruesome Spectacle",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = "Destroy target nonartifact creature. Its controller mills cards equal to that creature's power.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Destroy(Objects.Target("creature"))),
            Step.Do(Effect.Mill(Players.ControllerOf(Objects.Target("creature")), power(Objects.Target("creature")))),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Artifact)), creature)))),
        ),
    )

    @Test
    fun dreadOfTheDim() = card(
        name = "Dread of the Dim",
        cost = "{4}{B}",
        type = "Creature — Horror",
        pt = "3/4",
        text = "{U}: This creature gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(Ability.Activated(mana("{U}"), steps(gains(Objects.Self, Keyword.Hexproof)), rules = anyTime))

    @Test
    fun rotInTheRanks() = card(
        name = "Rot in the Ranks",
        cost = "{B}",
        type = "Enchantment",
        text = "Creature tokens get -1/-1.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(-1), n(-1))), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, token))))),
    )

    // CR 107.3a: X announced as the spell is cast decides what can be targeted
    @Test
    fun lethalGlare() = card(
        name = "Lethal Glare",
        cost = "{X}{B}",
        type = "Instant",
        text = "Destroy target creature with power X or less.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Destroy(Objects.Target("creature"))),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.Power(arcanum.api.Comparison.AtMost, Value.Announced("X")))))),
            announced = listOf("X"),
        ),
    )

    @Test
    fun masterOfTheVoid() = card(
        name = "Master of the Void",
        cost = "{4}{B}{B}{B}",
        type = "Creature — Demon",
        pt = "7/7",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, exile the top seven cards of that player's library, then put a creature card from among them onto the battlefield under your control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(combatDamageToPlayer, steps(
            Step.Do(Effect.Move(Objects.LibraryCards(Players.EventPlayer, n(7), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Choose(Choice.Objects(creature, n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You))),
        )),
    )

    @Test
    fun ratLordSlumlord() = card(
        name = "Rat Lord Slumlord",
        cost = "{3}{B}{B}",
        type = "Creature — Ogre Rogue",
        pt = "3/3",
        text = """
            Whenever another nontoken creature dies, you may create a 1/1 black Rat creature token.
            Rats you control have deathtouch.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Not(token), creature)),
                from = Zone.Battlefield, to = Zone.Graveyard)),
            steps(Step.May(listOf(Step.Do(Effect.CreateToken(creatureToken("Rat", listOf(Color.Black), 1, 1), n(1), Players.You))), Players.You)),
        ),
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Deathtouch))), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Rat")), you))))),
    )

    // CR 601.2c: up to one target in each opponent's graveyard
    @Test
    fun cryptPrimordial() = card(
        name = "Crypt Primordial",
        cost = "{5}{B}{B}",
        type = "Creature — Avatar",
        pt = "5/4",
        text = """
            Intimidate (This creature can't be blocked except by artifact creatures and/or creatures that share a color with it.)
            When this creature enters, for each opponent, you may put up to one target creature card from that player's graveyard onto the battlefield under your control.
        """,
    ) parsesTo abilities(
        Keyword.Intimidate,
        whenThisEnters(
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), null, from = Objects.Target("card")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You))),
            targets = listOf(TargetSlot("card", TargetKind.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.SlotPlayer))))), min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent)),
        ),
    )

    @Test
    fun gloomAlleyDenizen() = card(
        name = "Gloom Alley Denizen",
        cost = "{B}",
        type = "Creature — Vampire Rogue",
        pt = "1/1",
        text = "Whenever another black creature you control enters, target creature gains intimidate until end of turn. (It can't be blocked except by artifact creatures and/or creatures that share a color with it.)",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.OfColor(Color.Black), creature, you)),
                to = Zone.Battlefield)),
            Instructions(targets = listOf(slot("creature", creature)), steps = listOf(gains(Objects.Target("creature"), Keyword.Intimidate))),
        ),
    )

    // CR 506.2: the player this creature attacks
    @Test
    fun cobbleStreetThug() = card(
        name = "Cobble Street Thug",
        cost = "{2}{B}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = "Whenever this creature becomes blocked, defending player discards a card.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)),
            steps(Step.Do(Effect.Discard(Players.DefendingPlayerOf(Objects.Self), n(1), filter = ObjectFilter.Any))),
        ),
    )

    @Test
    fun chokingSmog() = card(
        name = "Choking Smog",
        cost = "{4}{B}{B}",
        type = "Creature — Elemental",
        pt = "3/3",
        text = """
            Flying
            Creatures with flying your opponents control get -1/-1.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(-1), n(-1))), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature,
            ObjectFilter.HasKeyword(Keyword.Flying), opponents))))),
    )

    @Test
    fun undercitySnitch() = card(
        name = "Undercity Snitch",
        cost = "{2}{B}",
        type = "Creature — Human Rogue",
        pt = "2/3",
        text = "{1}, Sacrifice a creature: Target player reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.",
    ) parsesTo abilities(
        Ability.Activated(
            costOf(manaPart("{1}"), CostPart.Sacrifice(CostObjects.Choose(creature, count = n(1)))),
            Instructions(targets = listOf(targetPlayer), steps = revealUntilLand),
            rules = anyTime,
        ),
    )

    @Test
    fun wightOfPrecinctNine() {
        val count = Value.Count(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent)))))
        card(
            name = "Wight of Precinct Nine",
            cost = "{1}{B}",
            type = "Creature — Zombie",
            pt = "1/1",
            text = "This creature gets +1/+1 for each creature card in your opponents' graveyards.",
        ) parsesTo abilities(Ability.Static(listOf(Mechanic.ModifyPowerToughness(count, count)), affects = Affected.Objects(Objects.Self)))
    }
}
