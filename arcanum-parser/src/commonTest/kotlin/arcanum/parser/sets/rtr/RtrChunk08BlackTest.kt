package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.combatDamageToAPlayer
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.draw
import arcanum.parser.sets.rtr.Rtr.enchant
import arcanum.parser.sets.rtr.Rtr.enchanted
import arcanum.parser.sets.rtr.Rtr.gainLife
import arcanum.parser.sets.rtr.Rtr.land
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.notYou
import arcanum.parser.sets.rtr.Rtr.p1p1
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.sub
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.thisDies
import arcanum.parser.sets.rtr.Rtr.thisEnters
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import kotlin.test.Test

/**
 * Return to Ravnica chunk 08: the black cards. A dead creature put back on top of its library, any opponent sacrificing to
 * tap a creature (CR 118.12), revealing until a land for another player, opponents' creatures returned at the next end step
 * as black Zombies (a delayed trigger, CR 603.7, with what they are as they enter, CR 611.2e), counters by the damage dealt,
 * a target's controller discarding or losing life, defender lifted as long as you control a Gate (CR 702.3b), granted
 * activated abilities, upkeep life loss of an enchanted creature's controller, a copy token of itself, monocolored.
 */
class RtrChunk08BlackTest {
    @Test
    fun paidAssassination() = card(
        name = "Paid Assassination",
        cost = "{4}{B}{B}",
        type = "Sorcery",
        text = "Destroy target creature. Its controller discards a card.",
    ) parsesTo abilities(
        spell(Rtr.destroy(target("creature")), Step.Do(Effect.Discard(Players.ControllerOf(target("creature")), n(1), ObjectFilter.Any)),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun pyreRitual() = card(
        name = "Pyre Ritual",
        cost = "{B}",
        type = "Instant",
        text = """
            Exile target card from a graveyard.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")), draw(1),
            targets = listOf(slot("card", all(Rtr.card, ObjectFilter.InZone(Zone.Graveyard))))),
    )

    /** "Put it on top": the card in the graveyard (CR 400.7). */
    @Test
    fun restlessWraith() = card(
        name = "Restless Wraith",
        cost = "{3}{B}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = """
            Flying
            When this creature dies, put it on top of its owner's library.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(thisDies, Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Library, libraryPosition = 0)))),
    )

    /** Each combat (CR 507); any opponent may pay by sacrificing; "if a player does" checks that payment (CR 118.12). */
    @Test
    fun defilingDemon() = card(
        name = "Defiling Demon",
        cost = "{2}{B}{B}",
        type = "Creature — Demon",
        pt = "6/6",
        text = """
            Flying
            At the beginning of each combat, any opponent may sacrifice a creature of their choice. If a player does, tap this creature and put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Rtr.step(TurnStep.BeginningOfCombat, PlayerFilter.Any),
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creature, count = n(1))))), payer = Players.Each(PlayerFilter.Opponent), bind = "sacrificed"),
            Step.If(Condition.Did("sacrificed"), then = listOf(Step.Do(Effect.Tap(Objects.Self)), Rtr.counters(Objects.Self)))),
    )

    /** Revealed until a land card, all of them (the land too) put into that player's graveyard (CR 701.20). */
    @Test
    fun buryTheClues() = card(
        name = "Bury the Clues",
        cost = "{4}{B}",
        type = "Sorcery",
        text = "Destroy target land. Its controller reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.",
    ) parsesTo abilities(
        spell(
            Rtr.destroy(target("land")),
            Step.Do(Effect.RevealUntil(land, library = Players.ControllerOf(target("land")), count = n(1), reveal = true, foundBind = "found", restBind = "rest")),
            Rtr.move(Objects.Union(listOf(Objects.Bound("rest"), Objects.Bound("found"))), Zone.Graveyard),
            targets = listOf(slot("land", land)),
        ),
    )

    /** A pump and a granted activated ability (CR 113.10): the creature's controller activates it for itself. */
    @Test
    fun wickedDelight() = card(
        name = "Wicked Delight",
        cost = "{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+1 and has "{R}: This creature gains trample until end of turn."
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(enchanted, Mechanic.ModifyPowerToughness(n(2), n(1)), Mechanic.GainAbilities(listOf(
            activated(cost(manaPart("{R}")), Rtr.gains(Objects.Self, Keyword.Trample))))),
    )

    @Test
    fun gutterRat() = card(
        name = "Gutter Rat",
        cost = "{B}",
        type = "Creature — Rat",
        pt = "1/1",
        text = "When this creature dies, you may pay {B}. If you do, target player discards a card.",
    ) parsesTo abilities(
        triggered(thisDies,
            Step.Pay(Cost.mana(Rtr.mana("{B}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Discard(Players.Target("player"), n(1), ObjectFilter.Any)))),
            targets = listOf(Rtr.targetPlayer)),
    )

    /** A delayed trigger at the next end step (CR 603.7) returns the card in the graveyard, named now (CR 603.7c), under your
     * control with a counter; it is a black Zombie as it enters (CR 611.2e), for good. */
    @Test
    fun tombTreachery() = card(
        name = "Tomb Treachery",
        cost = "{5}{B}{B}",
        type = "Enchantment",
        text = "Whenever a creature you don't control dies, return it to the battlefield under your control with an additional +1/+1 counter on it at the beginning of the next end step. That creature is a black Zombie in addition to its other colors and types.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(all(creature, notYou), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Bind(Objects.EventObject(EventRole.After), "dead"),
            Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), Instructions(steps = listOf(
                Step.Do(Effect.Move(Objects.Bound("dead"), Destination(Zone.Battlefield, controller = Players.You,
                    counters = listOf(CountersToPut(p1p1, n(1))),
                    entersAs = listOf(Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black)), add = true),
                        Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Zombie")))))),
                    postBind = "returned"))))))),
    )

    /** An additional sacrifice (CR 601.2f); the destroyed creature's controller as it last existed loses life (CR 608.2h). */
    @Test
    fun hurlFestival() = card(
        name = "Hurl Festival",
        cost = "{3}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Destroy target creature. Its controller loses 2 life.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creature, count = n(1))))))),
        spell(Rtr.destroy(target("creature")), Rtr.loseLife(n(2), Players.ControllerOf(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun thoughtRot() = card(
        name = "Thought Rot",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Target player discards two cards.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Discard(Players.Target("player"), n(2), ObjectFilter.Any)), targets = listOf(Rtr.targetPlayer)),
    )

    /** "Put that many counters on it": the creature that dealt the damage, as many as it dealt. */
    @Test
    fun necropolisOverlord() = card(
        name = "Necropolis Overlord",
        cost = "{3}{B}{B}{B}",
        type = "Creature — Vampire",
        pt = "6/5",
        text = """
            Flying
            Whenever a creature you control deals combat damage to a player, put that many +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer(all(creature, you)), Rtr.counters(Objects.EventObject(EventRole.Source), Value.EventAmount)),
    )

    /** CR 702.3b: defender's restriction lifted while the condition holds. */
    @Test
    fun ogreEscapee() = card(
        name = "Ogre Escapee",
        cost = "{3}{B}",
        type = "Creature — Ogre Rogue",
        pt = "4/4",
        text = """
            Defender
            This creature can attack as though it didn't have defender as long as you control a Gate.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(Objects.Self, Mechanic.CanAttackDespiteDefender, condition = Condition.Exists(all(sub("Gate"), you), atLeast = 1)),
    )

    /** A characteristic-defining P/T (CR 604.3) and a token that's a copy of it (CR 707.2). */
    @Test
    fun swarmingRat() = card(
        name = "Swarming Rat",
        cost = "{1}{B}",
        type = "Creature — Rat",
        pt = "*/*",
        text = """
            Swarming Rat's power and toughness are each equal to the number of Rats you control.
            {2}{B}, Discard a card: Create a token that's a copy of this creature.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.SetPowerToughness(Value.Count(Objects.Each(all(sub("Rat"), you))), Value.Count(Objects.Each(all(sub("Rat"), you))))),
            affects = Affected.Objects(Objects.Self), characteristicDefining = true),
        activated(cost(manaPart("{2}{B}"), CostPart.Discard(CostObjects.Choose(Rtr.card, count = n(1)))),
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), count = n(1), controller = Players.You))),
    )

    @Test
    fun menacingShade() = card(
        name = "Menacing Shade",
        cost = "{2}{B}{B}",
        type = "Creature — Insect Shade",
        pt = "1/1",
        text = "{1}{B}: This creature gets +2/+2 until end of turn.",
    ) parsesTo abilities(
        activated(cost(manaPart("{1}{B}")), pump(Objects.Self, 2, 2)),
    )

    /** An intervening "if" (CR 603.4), checked as it triggers and as it resolves. */
    @Test
    fun howlingTorment() = card(
        name = "Howling Torment",
        cost = "{B}",
        type = "Enchantment",
        text = "At the beginning of each opponent's upkeep, if that player has one or fewer cards in hand, they lose 3 life.",
    ) parsesTo abilities(
        triggered(Rtr.step(TurnStep.Upkeep, PlayerFilter.Opponent), Rtr.loseLife(n(3), Players.EventPlayer),
            interveningIf = Condition.Compare(Value.HandSize(Players.EventPlayer), Comparison.AtMost, n(1))),
    )

    /** Each player chooses, then all sacrifice at once (CR 101.4). */
    @Test
    fun tenementReaper() = card(
        name = "Tenement Reaper",
        cost = "{3}{B}",
        type = "Creature — Horror",
        pt = "4/2",
        text = "When this creature enters, each player sacrifices a creature of their choice.",
    ) parsesTo abilities(
        triggered(thisEnters, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
            Step.Choose(Choice.Objects(all(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Bound("player")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
    )

    /** "That player": the active player of that upkeep (the enchanted creature's controller). */
    @Test
    fun festeringGash() = card(
        name = "Festering Gash",
        cost = "{2}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -2/-2.
            At the beginning of the upkeep of enchanted creature's controller, that player loses 2 life.
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(enchanted, Mechanic.ModifyPowerToughness(n(-2), n(-2))),
        triggered(Rtr.step(TurnStep.Upkeep, PlayerFilter.Is(Players.ControllerOf(enchanted))), Rtr.loseLife(n(2), Players.EventPlayer)),
    )

    @Test
    fun alehouseGrifter() = card(
        name = "Alehouse Grifter",
        cost = "{1}{B}",
        type = "Creature — Human Rogue",
        pt = "1/2",
        text = "{T}, Pay 3 life: Flip a coin. If you win the flip, you gain 6 life.",
    ) parsesTo abilities(
        activated(cost(CostPart.TapSelf, CostPart.PayLife(n(3))),
            Step.Do(Effect.FlipCoin(Players.You, bind = "flip")),
            Step.If(Condition.WonFlip("flip"), then = listOf(gainLife(6)))),
    )

    /** Monocolored: exactly one colour (CR 105.2a). */
    @Test
    fun finalToll() = card(
        name = "Final Toll",
        cost = "{1}{B}",
        type = "Instant",
        text = "Destroy target monocolored creature.",
    ) parsesTo abilities(
        spell(Rtr.destroy(target("creature")), targets = listOf(slot("creature", all(ObjectFilter.Monocolored, creature)))),
    )

    @Test
    fun subterraneanContacts() = card(
        name = "Subterranean Contacts",
        cost = "{1}{B}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Enchanted land has "{T}, Pay 1 life: Draw a card."
        """,
    ) parsesTo abilities(
        enchant(land),
        static(enchanted, Mechanic.GainAbilities(listOf(activated(cost(CostPart.TapSelf, CostPart.PayLife(n(1))), draw(1))))),
    )
}
